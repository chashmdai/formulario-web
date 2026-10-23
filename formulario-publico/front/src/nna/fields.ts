export interface NnaData {
  primerNombre: string; segundoNombre: string | null; primerApellido: string; segundoApellido: string | null;
  runNumero: string | null; runDv: string | null; direccion: string | null; comuna: string | null; region: string;
}
export const fields: { key: keyof NnaData; label: string; required?: boolean; max: number }[] = [
  { key: 'primerNombre', label: 'Primer nombre', required: true, max: 100 },
  { key: 'segundoNombre', label: 'Segundo nombre (opcional)', max: 100 },
  { key: 'primerApellido', label: 'Primer apellido', required: true, max: 100 },
  { key: 'segundoApellido', label: 'Segundo apellido (opcional)', max: 100 },
  { key: 'runNumero', label: 'Número de RUN/RUT (opcional)', max: 8 },
  { key: 'runDv', label: 'Dígito verificador (si informas RUN/RUT)', max: 1 },
  { key: 'direccion', label: 'Dirección (opcional)', max: 300 },
  { key: 'comuna', label: 'Comuna (opcional)', max: 100 },
  { key: 'region', label: 'Región', required: true, max: 100 },
];
export const emptyData = (): NnaData => ({ primerNombre: '', segundoNombre: null, primerApellido: '', segundoApellido: null, runNumero: null, runDv: null, direccion: null, comuna: null, region: '' });
export function validRun(numero: string | null, dv: string | null): boolean {
  if (numero === null && dv === null) return true;
  if (!numero || !/^[1-9][0-9]{0,7}$/.test(numero) || !dv || !/^[0-9Kk]$/.test(dv)) return false;
  let sum = 0; let factor = 2;
  for (let i = numero.length - 1; i >= 0; i--) { sum += Number(numero[i]) * factor; factor = factor === 7 ? 2 : factor + 1; }
  const result = 11 - sum % 11;
  return dv.toUpperCase() === (result === 11 ? '0' : result === 10 ? 'K' : String(result));
}
