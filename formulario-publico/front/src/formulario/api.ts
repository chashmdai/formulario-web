import { ApiError, getCsrf, json } from '../api';

export type JourneyKind = 'ADULTO_CU' | 'ADULTO_DECLARADO' | 'NNA';
export type PersonRole = 'PRESENTADOR' | 'AFECTADO_NNA' | 'ADULTO_RELACIONADO' | 'PERSONA_SENALADA';
export const identityFields = ['primerNombre', 'segundoNombre', 'primerApellido', 'segundoApellido', 'fechaNacimiento', 'sexo', 'edad'] as const;
export type IdentityField = typeof identityFields[number];
export type Declarations = Partial<Record<IdentityField, string>>;
export interface Journey { id: string; tipo: JourneyKind; avisoVersion: string; aceptaCanal: boolean; estado: string; venceEn: string }
export interface JourneyCapabilities { disponible: boolean; avisoAdulto: string; avisoNna: string }
export interface IdentityView {
  personaId: string; estado: 'CONSULTANDO' | 'REVISADO'; autenticada: boolean; runInformado: boolean;
  camposFaltantes: IdentityField[]; camposObligatorios: IdentityField[]; valoresDeclarados: Declarations; mensaje: string;
}
const root = '/api/formulario';
const path = (journey: string, person: string) => `${root}/recorridos/${encodeURIComponent(journey)}/personas/${encodeURIComponent(person)}`;
export const getJourneyCapabilities = () => json<JourneyCapabilities>(`${root}/capacidades`);
export const getCurrentJourney = () => json<Journey>(`${root}/actual`);
async function mutation<T>(url: string, method: 'POST' | 'DELETE', body?: unknown): Promise<T> {
  const csrf = await getCsrf();
  const response = await fetch(url, {method, credentials: 'same-origin', cache: 'no-store',
    headers: {'Content-Type': 'application/json', [csrf.headerName]: csrf.token},
    ...(body === undefined ? {} : {body: JSON.stringify(body)}),
  });
  if (!response.ok) throw new ApiError(response.status);
  return response.status === 204 ? undefined as T : await response.json() as T;
}
export const startJourney = (tipo: JourneyKind, avisoVersion: string, aceptaCanalDigital: boolean) =>
  mutation<Journey>(`${root}/recorridos`, 'POST', {tipo, avisoVersion, aceptaCanalDigital});
export const cancelJourney = (id: string) => mutation<void>(`${root}/recorridos/${encodeURIComponent(id)}`, 'DELETE');
export const resetPerson = (journey: string, person: string) => mutation<void>(`${path(journey, person)}/identificacion`, 'DELETE');
/** Rechaza una proyección ampliada accidentalmente con atributos internos del Registro Civil. */
function publicView(value: unknown): IdentityView {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new ApiError(502);
  const v = value as Record<string, unknown>;
  const keys = ['personaId', 'estado', 'autenticada', 'runInformado', 'camposFaltantes', 'camposObligatorios', 'valoresDeclarados', 'mensaje'];
  const fields = (a: unknown): a is IdentityField[] => Array.isArray(a) && a.every(k => identityFields.includes(k as IdentityField));
  if (Object.keys(v).some(k => !keys.includes(k)) || typeof v.personaId !== 'string' || !['CONSULTANDO', 'REVISADO'].includes(String(v.estado))
    || typeof v.autenticada !== 'boolean' || typeof v.runInformado !== 'boolean' || typeof v.mensaje !== 'string'
    || !fields(v.camposFaltantes) || !fields(v.camposObligatorios) || v.camposObligatorios.some(k => !(v.camposFaltantes as string[]).includes(k))
    || !v.valoresDeclarados || typeof v.valoresDeclarados !== 'object' || Array.isArray(v.valoresDeclarados)) throw new ApiError(502);
  if (Object.entries(v.valoresDeclarados).some(([k, s]) => !(v.camposFaltantes as string[]).includes(k) || typeof s !== 'string' || s.length > 500)) throw new ApiError(502);
  if (v.estado === 'CONSULTANDO' && (v.camposFaltantes.length || Object.keys(v.valoresDeclarados).length)) throw new ApiError(502);
  return value as IdentityView;
}
export async function getPerson(journey: string, person: string): Promise<IdentityView> {
  return publicView(await json<unknown>(path(journey, person)));
}
const identifying = new Map<string, Promise<IdentityView>>();
export function identifyPerson(journey: string, person: string, rol: PersonRole, runNumero?: string, runDv?: string): Promise<IdentityView> {
  const key = JSON.stringify([journey, person, rol, runNumero, runDv]);
  const existing = identifying.get(key);
  if (existing) return existing;
  const pending = mutation<unknown>(`${path(journey, person)}/identificacion`, 'POST', {rol, runNumero, runDv}).then(publicView);
  identifying.set(key, pending);
  void pending.then(() => identifying.delete(key), () => identifying.delete(key));
  return pending;
}
export async function declarePerson(journey: string, person: string, atributos: Declarations): Promise<IdentityView> {
  return publicView(await mutation<unknown>(`${path(journey, person)}/declaracion`, 'POST', {atributos}));
}
