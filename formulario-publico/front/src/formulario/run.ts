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
export function pastedRun(input: string): {numero: string; dv: string} | null {
  const compact=input.trim().replace(/[.\s]/g,'');
  const explicit=/^([0-9]{1,8})-([0-9Kk])$/.exec(compact);
  if(explicit)return {numero:explicit[1],dv:explicit[2].toUpperCase()};
  if(/^[0-9]{8}[0-9Kk]$/.test(compact)||/^[0-9]{1,8}[Kk]$/.test(compact))return {numero:compact.slice(0,-1),dv:compact.slice(-1).toUpperCase()};
  return null;
}
