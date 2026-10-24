/** Solo validación matemática. Nunca se interpreta como autenticación o existencia registral. */
export function validRun(numero: string, dv: string): boolean {
  if (!/^[1-9][0-9]{0,7}$/.test(numero) || !/^[0-9Kk]$/.test(dv)) return false;
  let sum = 0, multiplier = 2;
  for (let i = numero.length - 1; i >= 0; i--) { sum += Number(numero[i]) * multiplier; multiplier = multiplier === 7 ? 2 : multiplier + 1; }
  const value = 11 - sum % 11;
  return dv.toUpperCase() === (value === 11 ? '0' : value === 10 ? 'K' : String(value));
}
export const runNumber = (input: string) => input.replace(/\D/g, '').slice(0, 8);
export const formatRunNumber = (input: string) => runNumber(input).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
