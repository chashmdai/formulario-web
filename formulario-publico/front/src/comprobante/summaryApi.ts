import {ApiError,json} from '../api';
import {publicView} from '../formulario/api';
import type {IdentityView,JourneyKind} from '../formulario/api';
import type {InstitutionReference,PersonReference} from '../formulario/adultRules';
import type {AuthenticatedIdentity} from '../formulario/reviewModel';
import {parseSubmission} from '../formulario/submission';
import type {SubmissionState} from '../formulario/submission';

export interface SummaryAttachment {id:string;nombre:string;tamanio:number;sha256:string;tipo:string|null}
export interface RetiredFile {id:string;tamanio:number;sha256:string}
export interface SummaryContent {
  version:string;tipo:JourneyKind;recibidaEn:string;campos:Record<string,string>;relaciones:PersonReference[];instituciones:InstitutionReference[];
  personas:IdentityView[];identidadAutenticada:AuthenticatedIdentity|null;adjuntos:SummaryAttachment[];
}
/** Resumen del envío derivado por el servidor del documento custodiado y verificado. No se guarda en el navegador. */
export interface SubmissionSummary {state:SubmissionState;content:SummaryContent|null;retiredFiles:RetiredFile[]}

type Obj=Record<string,unknown>;
const invalid=():never=>{throw new ApiError(502);};
const object=(value:unknown,keys:readonly string[]):Obj=>{
  if(!value||typeof value!=='object'||Array.isArray(value))return invalid();
  // Rechaza una proyección ampliada accidentalmente, igual que la vista pública de identidad.
  if(Object.keys(value).some(key=>!keys.includes(key)))return invalid();
  return value as Obj;
};
const text=(value:unknown,max=10000):string=>typeof value==='string'&&value.length<=max?value:invalid();
const optionalText=(value:unknown):string=>value===null||value===undefined?'':text(value,500);
const list=(value:unknown,max:number):unknown[]=>Array.isArray(value)&&value.length<=max?value:invalid();
const record=(value:unknown,max:number):Record<string,string>=>{
  const source=object(value,Object.keys(value&&typeof value==='object'?value:{}));const entries=Object.entries(source);
  if(entries.length>max)return invalid();
  return Object.fromEntries(entries.map(([key,v])=>[text(key,100),text(v)]));
};
const sha=(value:unknown)=>typeof value==='string'&&/^[a-f0-9]{64}$/.test(value)?value:invalid();
const size=(value:unknown)=>typeof value==='number'&&Number.isSafeInteger(value)&&value>0?value:invalid();
const kinds:JourneyKind[]=['ADULTO_CU','ADULTO_DECLARADO','NNA'];

export function parseSummary(value:unknown,id:string):SubmissionSummary{
  const v=object(value,['id','estado','custodia','registro','contenido','archivosRetirados']);
  const state=parseSubmission({id:v.id,estado:v.estado,custodia:v.custodia,registro:v.registro} as SubmissionState);
  if(state.id!==id)return invalid();
  const retiredFiles=list(v.archivosRetirados??[],12).map(item=>{const f=object(item,['id','tamanio','sha256']);return {id:text(f.id,64),tamanio:size(f.tamanio),sha256:sha(f.sha256)};});
  if(!('contenido' in v))return invalid();
  if(v.contenido===null){
    // Solo un registro confirmado puede haber retirado su contenido.
    if(state.estado!=='REGISTRADA_SIGER')return invalid();
    return {state,content:null,retiredFiles};
  }
  if(retiredFiles.length)return invalid();
  const c=object(v.contenido,['version','tipo','recibidaEn','campos','relaciones','instituciones','personas','identidadAutenticada','adjuntos']);
  const tipo=kinds.find(kind=>kind===c.tipo)??invalid();
  const personas=list(c.personas,30).map(publicView);
  const relaciones=list(c.relaciones,29).map(item=>{const r=object(item,['personaId','detalles']);return {personaId:text(r.personaId,40),detalles:record(r.detalles??{},10)};});
  const instituciones=list(c.instituciones,20).map(item=>{const i=object(item,['nombre','tipo','rol','region','comuna']);
    return {nombre:optionalText(i.nombre),tipo:optionalText(i.tipo),rol:optionalText(i.rol),region:optionalText(i.region),comuna:optionalText(i.comuna)};});
  const adjuntos=list(c.adjuntos,12).map(item=>{const a=object(item,['id','nombre','tamanio','sha256','tipo']);
    return {id:text(a.id,64),nombre:text(a.nombre,500),tamanio:size(a.tamanio),sha256:sha(a.sha256),tipo:a.tipo===null||a.tipo===undefined?null:text(a.tipo,200)};});
  let identidadAutenticada:AuthenticatedIdentity|null=null;
  if(c.identidadAutenticada!==null&&c.identidadAutenticada!==undefined){
    const a=object(c.identidadAutenticada,['run','nombres','apellidos']);
    if(tipo!=='ADULTO_CU')return invalid();
    identidadAutenticada={run:text(a.run,20),nombres:optionalText(a.nombres),apellidos:optionalText(a.apellidos)};
  }
  return {state,retiredFiles,content:{version:text(c.version,8),tipo,recibidaEn:text(c.recibidaEn,64),campos:record(c.campos,80),relaciones,instituciones,personas,identidadAutenticada,adjuntos}};
}
/** Consulta propia de la sesión; el servidor responde 404 si la sesión no es la propietaria. */
export async function getSubmissionSummary(id:string):Promise<SubmissionSummary>{
  const controller=new AbortController();const timeout=window.setTimeout(()=>controller.abort(),15000);
  try{return parseSummary(await json<unknown>(`/api/formulario/comunicaciones/${encodeURIComponent(id)}/resumen`,{signal:controller.signal}),id);}
  finally{window.clearTimeout(timeout);}
}
