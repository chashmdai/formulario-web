import {sha256} from '@noble/hashes/sha2.js';
import {bytesToHex} from '@noble/hashes/utils.js';
import type {SubmissionState} from '../formulario/submission';

/**
 * Evidencias criptográficas originales. El PDF solo las representa: la validez reside en estos JSON,
 * que conservan intactos `payload` y `firma`. El navegador no posee claves y no verifica firmas;
 * el servidor ya validó la custodia y el registro institucional antes de entregarlos.
 */
export interface EvidenceFile {kind:'custodia'|'registro';title:string;filename:string;json:string;sha256:string;fields:[string,string][]}

/** Serialización única para la descarga y el anexo: el mismo texto produce la misma huella. */
export function evidenceJson(value:unknown){return JSON.stringify(value,null,2);}
export function textSha256(text:string){return bytesToHex(sha256(new TextEncoder().encode(text)));}
export const custodyFilename=(id:string)=>`comprobante-custodia-${id}.json`;
export const registryFilename=(id:string)=>`comprobante-institucional-${id}.json`;

/** Decodifica el contenido firmado (base64url sin relleno) sin alterarlo; devuelve null si no es JSON. */
export function decodePayload(payload:unknown):Record<string,unknown>|null{
  if(typeof payload!=='string'||!/^[A-Za-z0-9_-]+$/.test(payload)||payload.length>16384)return null;
  try{
    const base64=payload.replace(/-/g,'+').replace(/_/g,'/')+'='.repeat((4-payload.length%4)%4);
    const binary=atob(base64),bytes=Uint8Array.from(binary,c=>c.charCodeAt(0));
    const value:unknown=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes));
    return value&&typeof value==='object'&&!Array.isArray(value)?value as Record<string,unknown>:null;
  }catch{return null;}
}
const FOLIO=/^[A-Z][A-Z0-9]{1,15}-[0-9]{1,19}$/;
export interface ConfirmedRegistration {folio:string;registradaEn:string|null}
/**
 * Folio institucional solo si el estado está confirmado y el contenido firmado del registro coincide
 * con el folio y con esta referencia. Nunca deriva ni inventa un folio a partir de otros datos.
 */
export function confirmedRegistration(state:SubmissionState):ConfirmedRegistration|null{
  if(state.estado!=='REGISTRADA_SIGER')return null;
  const folio=state.registro?.registro?.folio,signed=decodePayload(state.registro?.comprobante?.payload);
  if(typeof folio!=='string'||!FOLIO.test(folio)||!signed||signed.folio!==folio||signed.operacionPublicaId!==state.id)throw new Error('Registro institucional incoherente');
  return {folio,registradaEn:typeof signed.registradaEn==='string'?signed.registradaEn:null};
}
export function receivedAt(state:SubmissionState):string|null{
  const signed=decodePayload(state.custodia?.payload);
  return signed&&signed.id===state.id&&typeof signed.recibidaEn==='string'?signed.recibidaEn:null;
}
const shown=(value:unknown)=>typeof value==='string'||typeof value==='number'||typeof value==='boolean'?String(value):'';
function fields(source:Record<string,unknown>|null,keys:[string,string][],receipt:{algoritmo?:unknown;keyId?:unknown}):[string,string][]{
  const rows:[string,string][]=[['Algoritmo de firma',shown(receipt.algoritmo)],['Identificador de clave',shown(receipt.keyId)]];
  if(!source){rows.push(['Contenido firmado','No se pudo decodificar; consulte el JSON original.']);return rows.filter(([,value])=>value);}
  for(const [key,label] of keys){const value=shown(source[key]);if(value)rows.push([label,value]);}
  return rows.filter(([,value])=>value);
}
export function evidenceFiles(state:SubmissionState):EvidenceFile[]{
  const result:EvidenceFile[]=[];
  if(state.custodia){
    const json=evidenceJson(state.custodia);
    result.push({kind:'custodia',title:'Comprobante de custodia del Formulario Público',filename:custodyFilename(state.id),json,sha256:textSha256(json),
      fields:fields(decodePayload(state.custodia.payload),[['emisor','Emisor'],['tipoRecepcion','Tipo de recepción'],['id','Referencia'],['recibidaEn','Recibida en (UTC)'],['huellaContenido','Huella del contenido (SHA-256)'],['huellaProcedencia','Huella de procedencia (SHA-256)'],['avisoVersion','Versión del aviso de privacidad'],['version','Versión del comprobante']],state.custodia)});
  }
  if(state.registro){
    const json=evidenceJson(state.registro);
    result.push({kind:'registro',title:'Registro institucional emitido por SIGER',filename:registryFilename(state.id),json,sha256:textSha256(json),
      fields:fields(decodePayload(state.registro.comprobante?.payload),[['emisor','Emisor'],['hecho','Hecho registrado'],['folio','Folio'],['registradaEn','Registrada en (UTC)'],['operacionPublicaId','Referencia de recepción'],['canalOrigen','Canal de origen'],['sha256','Huella del contenido (SHA-256)'],['huellaProcedencia','Huella de procedencia (SHA-256)'],['version','Versión del registro']],state.registro.comprobante||{})});
  }
  return result;
}
/** Descarga un texto tal cual; usado tanto para las evidencias JSON como para el PDF. */
export function saveBlob(content:BlobPart,type:string,name:string){
  const url=URL.createObjectURL(new Blob([content],{type}));const a=document.createElement('a');
  a.href=url;a.download=name;a.rel='noopener';document.body.append(a);a.click();a.remove();
  window.setTimeout(()=>URL.revokeObjectURL(url),0);
}
/** Descarga la evidencia original como JSON; mismo texto que reproduce el anexo del PDF. */
export function downloadEvidence(value:unknown,name:string){saveBlob(evidenceJson(value),'application/json',name);}
