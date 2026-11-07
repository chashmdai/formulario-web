/**
 * Reglas de datos personales DECLARADOS. Deben coincidir con IdentificacionCiudadana.validar (backend):
 * el navegador solo adelanta mensajes comprensibles; el servidor sigue siendo la autoridad.
 * Nunca se aplican a datos provenientes de ClaveÚnica o del Registro Civil.
 */
export const NAME_FIELDS = ['primerNombre', 'segundoNombre', 'primerApellido', 'segundoApellido'] as const;
export const NAME_MAX_LENGTH = 100;
export const DOCUMENT_MAX_LENGTH = 40;

const WORD = '\\p{L}[\\p{L}\\p{M}]*';
/** Palabras separadas por un espacio, guion o apóstrofo; el punto admite abreviaturas («Ma. José», «Jr.»). */
const NAME_SHAPE = new RegExp(`^${WORD}(?:(?:[ '\\-]|\\. ?)${WORD})*\\.?$`, 'u');
/** Escrituras en que un nombre de un solo carácter es habitual. */
const SINGLE_CHARACTER_SCRIPTS = /[\p{Script=Han}\p{Script=Hangul}\p{Script=Hiragana}\p{Script=Katakana}\p{Script=Yi}]/u;
const TYPOGRAPHIC_APOSTROPHES = /[‘’`´]/gu;

export function isNameField(key: string): key is typeof NAME_FIELDS[number] {
  return (NAME_FIELDS as readonly string[]).includes(key);
}

/** Forma canónica mostrada y enviada: NFC, apóstrofo recto y un solo espacio entre palabras. */
export function normalizeName(value: string): string {
  return value.normalize('NFC').replace(TYPOGRAPHIC_APOSTROPHES, "'").replace(/\s+/gu, ' ').trim();
}

/**
 * @param strict exige al menos dos letras (persona adulta que presenta la comunicación).
 *   Para terceros basta un dato parcial: una inicial puede ser lo único conocido.
 */
export function nameProblem(key: string, raw: string, strict: boolean): string | null {
  const value = normalizeName(raw);
  if (!value) return null;
  if (/\p{N}/u.test(value)) return 'No incluyas números.';
  if (/[^\p{L}\p{M} '.-]/u.test(value)) return "Usa solo letras, espacios, guion (-), apóstrofo (') o punto.";
  if (value.length > NAME_MAX_LENGTH) return `Admite hasta ${NAME_MAX_LENGTH} caracteres.`;
  if (!NAME_SHAPE.test(value)) return 'Los espacios y signos deben ir entre letras, como en «Pérez-Cotapos» u «O\'Higgins».';
  if (strict && [...value].filter(c => /\p{L}/u.test(c)).length < 2 && !SINGLE_CHARACTER_SCRIPTS.test(value))
    return key.endsWith('Apellido') ? 'Escribe el apellido completo, no solo la inicial.' : 'Escribe el nombre completo, no solo la inicial.';
  return null;
}

/**
 * Problemas inequívocos que pueden informarse mientras se escribe o pega (sin bloquear la pulsación):
 * números, caracteres no admitidos o exceso de largo. La forma y las iniciales se revisan al salir del campo,
 * porque «Pérez-» o «J» pueden ser estados intermedios legítimos de escritura.
 */
export function typingProblem(key: string, raw: string): string | null {
  if (isNameField(key)) {
    const problem = nameProblem(key, raw, false);
    return problem && !problem.startsWith('Los espacios') ? problem : null;
  }
  if (key === 'numeroDocumento') {
    const problem = documentNumberProblem(raw);
    return problem && !problem.startsWith('Escribe el número') ? problem : null;
  }
  return null;
}

export function normalizeDocumentNumber(value: string): string {
  return value.normalize('NFC').replace(/\s+/gu, ' ').trim();
}

export function documentNumberProblem(raw: string): string | null {
  const value = normalizeDocumentNumber(raw);
  if (!value) return null;
  if (value.length > DOCUMENT_MAX_LENGTH) return `Admite hasta ${DOCUMENT_MAX_LENGTH} caracteres.`;
  if (/[^\p{L}\p{N} ./-]/u.test(value)) return 'Usa solo letras, números, espacios, puntos, guiones o barras.';
  if ((value.match(/[\p{L}\p{N}]/gu) || []).length < 3) return 'Escribe el número completo, tal como aparece en el documento.';
  return null;
}

/** Misma ventana que el backend: fecha real, no futura y dentro de 130 años. */
export function birthDateProblem(value: string, today = new Date()): string | null {
  if (!value) return null;
  const parsed = new Date(`${value}T12:00:00Z`);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || !Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== value)
    return 'Revisa la fecha: usa día, mes y año válidos.';
  const now = localDate(today);
  if (value > now) return 'La fecha de nacimiento no puede ser posterior a hoy.';
  const oldest = `${Number(now.slice(0, 4)) - 130}${now.slice(4)}`;
  if (value < oldest) return 'Revisa el año de nacimiento.';
  return null;
}

/** Aviso no bloqueante: el recorrido adulto no decide la edad de la persona. */
export function isUnderage(value: string, today = new Date()): boolean {
  if (birthDateProblem(value, today)) return false;
  const now = localDate(today);
  return value > `${Number(now.slice(0, 4)) - 18}${now.slice(4)}`;
}

function localDate(now: Date): string {
  const parts = new Intl.DateTimeFormat('en-CA', {timeZone: 'America/Santiago', year: 'numeric', month: '2-digit', day: '2-digit'}).formatToParts(now);
  return ['year', 'month', 'day'].map(key => parts.find(p => p.type === key)?.value).join('-');
}
