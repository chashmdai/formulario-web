import catalog from './paises.json';
export const countryNames=Object.values(catalog.paises).sort((a,b)=>a.localeCompare(b,'es'));
export const otherNationality='__OTRA__';
export const nationalityPrefix=catalog.especial;
const names=new Set<string>(countryNames);
export function nationalitySelection(value:string){return names.has(value)?value:value?otherNationality:'';}
export function nationalityDetail(value:string){return value.startsWith(nationalityPrefix)?value.slice(nationalityPrefix.length):names.has(value)?'':value;}
/** Solo al volver a declarar: conserva textos anteriores dentro de la opción excepcional explícita. */
export function canonicalNationality(value:string){return value&&!names.has(value)&&!value.startsWith(nationalityPrefix)?nationalityPrefix+value:value;}
export function validNationality(value:string){
 if(names.has(value))return true;
 if(!value.startsWith(nationalityPrefix))return false;
 const detail=value.slice(nationalityPrefix.length);
 return detail.length<=120&&!!detail.trim()&&/\p{L}/u.test(detail)&&!/[\u0000-\u001f\u007f-\u009f]/u.test(detail);
}
export function searchCountries(query:string){
 const fold=(s:string)=>s.normalize('NFD').replace(/\p{M}/gu,'').toLocaleLowerCase('es').trim();
 const terms=fold(query).split(/\s+/);return countryNames.filter(name=>terms.every(term=>fold(name).includes(term)));
}
