/** Contactos declarados: formato no acredita existencia ni titularidad. */
export function cleanEmail(value:string){return value.trim();}
export function validEmail(raw:string){
 if(/[\r\n\0]/.test(raw))return false;
 const value=cleanEmail(raw),parts=value.split('@');if(value.length>254||parts.length!==2)return false;
 const [local,domain]=parts;if(!local||local.length>64||local.startsWith('.')||local.endsWith('.')||local.includes('..')||/[\s@<>()\[\]:;,"\\\x00-\x1f\x7f]/u.test(local))return false;
 const labels=domain.split('.');return labels.length>=2&&labels.every(label=>/^[\p{L}\p{N}](?:[\p{L}\p{N}-]{0,61}[\p{L}\p{N}])?$/u.test(label));
}
export function canonicalMobile(raw:string):string|null{
 const value=raw.trim();if(!value)return '';
 if(!/^\+?[0-9\s().-]+$/.test(value))return null;
 const digits=value.replace(/\D/g,'');
 if(value.startsWith('+'))return /^569[0-9]{8}$/.test(digits)?'+'+digits:null;
 if(/^[0-9]{8}$/.test(digits))return '+569'+digits;
 if(/^9[0-9]{8}$/.test(digits))return '+56'+digits;
 if(/^569[0-9]{8}$/.test(digits))return '+'+digits;
 if(/^00569[0-9]{8}$/.test(digits))return '+'+digits.slice(2);
 return null;
}
export function normalizeContact(id:string,value:string){return id==='email'||id.endsWith('-email')?cleanEmail(value):id==='telefono'||id.endsWith('-celular')?canonicalMobile(value)??value:value;}
