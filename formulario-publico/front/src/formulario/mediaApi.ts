import {ApiError,getCsrf,json} from '../api';
import type {Attachment} from './NnaFlow';
export interface MediaPolicy{habilitada:boolean;limites:{cantidad:number;total:number;bloque:number;imagen:number;documento:number;audio:number;video:number};formatos:{extension:string;tipo:string;categoria:string;maximo:number}[];deshabilitados:Record<string,string>}
export interface UploadState{id:string;nombre:string;tamanio:number;sha256:string;recibidos:number;bloque:number;estado:string;error:string|null;bloquesFaltantes:number[];archivo:Attachment|null}
export interface Manifest{revision:string;archivos:Attachment[]}
export interface MediaView{version:string;origen:string;original:Attachment;representacion:'DESCARGA'|'IMAGEN_DERIVADA'|'AUDIO'|'VIDEO';originalInline:boolean;descargable:boolean;huellaDerivada:string|null;tamanioDerivada:number|null}
const root=(journey:string)=>`/api/formulario/recorridos/${encodeURIComponent(journey)}`;
export const mediaPolicy=(journey:string)=>json<MediaPolicy>(root(journey)+'/multimedia/politica');
export const mediaManifest=(journey:string)=>json<Manifest>(root(journey)+'/multimedia/manifiesto');
export const pendingMedia=(journey:string)=>json<UploadState[]>(root(journey)+'/cargas');
export const mediaView=(journey:string,id:string)=>json<MediaView>(root(journey)+`/adjuntos/${encodeURIComponent(id)}/vista`);
export const mediaUrl=(journey:string,id:string,preview=false)=>root(journey)+`/adjuntos/${encodeURIComponent(id)}`+(preview?'/representacion':'');
async function mutation<T>(journey:string,path:string,method:string,body?:unknown,signal?:AbortSignal):Promise<T>{
 const csrf=await getCsrf();const response=await fetch(root(journey)+path,{method,credentials:'same-origin',cache:'no-store',signal,headers:{[csrf.headerName]:csrf.token,...(body===undefined?{}:{'Content-Type':'application/json'})},...(body===undefined?{}:{body:JSON.stringify(body)})});if(!response.ok)throw new ApiError(response.status);return response.status===204?undefined as T:await response.json() as T;
}
export const commitMedia=(journey:string,ids:string[],base:string)=>mutation<Manifest>(journey,'/multimedia/manifiesto','POST',{ids,base});
export const cancelMedia=(journey:string,id:string)=>mutation<void>(journey,`/cargas/${encodeURIComponent(id)}`,'DELETE');
function abort(){throw new DOMException('Carga pausada','AbortError');}
export function hashMedia(file:File,chunk:number,signal:AbortSignal,onProgress:(read:number)=>void):Promise<{digest:string;blocks:string[]}>{return new Promise((resolve,reject)=>{
 if(signal.aborted){reject(new DOMException('Carga pausada','AbortError'));return;}
 const worker=new Worker(new URL('./mediaHash.worker.ts',import.meta.url),{type:'module'});
 const dispose=()=>{worker.terminate();signal.removeEventListener('abort',stop);};
 const stop=()=>{dispose();reject(new DOMException('Carga pausada','AbortError'));};signal.addEventListener('abort',stop,{once:true});
 worker.onerror=()=>{dispose();reject(new Error('No pudimos preparar el archivo.'));};worker.onmessage=(event:MessageEvent<{read?:number;digest?:string;blocks?:string[];error?:boolean}>)=>{if(event.data.error){dispose();reject(new Error('No pudimos preparar el archivo.'));}else if(event.data.digest&&event.data.blocks){dispose();resolve({digest:event.data.digest,blocks:event.data.blocks});}else if(event.data.read!==undefined)onProgress(event.data.read);};worker.postMessage({file,chunk});
 });}
function delay(ms:number,signal:AbortSignal){return new Promise<void>((resolve,reject)=>{const stop=()=>{clearTimeout(timer);reject(new DOMException('Carga pausada','AbortError'));};const timer=setTimeout(()=>{signal.removeEventListener('abort',stop);resolve();},ms);signal.addEventListener('abort',stop,{once:true});if(signal.aborted)stop();});}
export interface UploadProgress{fase:'PREPARANDO'|'CARGANDO'|'ANALIZANDO';bytes:number;id?:string}
export async function uploadMedia(journey:string,file:File,policy:MediaPolicy,signal:AbortSignal,progress:(p:UploadProgress)=>void):Promise<Attachment>{
 progress({fase:'PREPARANDO',bytes:0});const hash=await hashMedia(file,policy.limites.bloque,signal,read=>progress({fase:'PREPARANDO',bytes:read}));if(signal.aborted)abort();
 let state=await mutation<UploadState>(journey,'/cargas','POST',{nombre:file.name,tamanio:file.size,sha256:hash.digest},signal);const id=state.id;
 if(state.estado==='VALIDADO'&&state.archivo)return state.archivo;
 for(let index=0;index<hash.blocks.length;index++){
  const start=index*state.bloque,end=Math.min(file.size,start+state.bloque);if(end<=state.recibidos&&!state.bloquesFaltantes.includes(index))continue;
  progress({fase:'CARGANDO',bytes:state.recibidos,id});let completed=false;
  for(let attempt=0;attempt<3&&!completed;attempt++){
   if(signal.aborted)abort();try{
    const csrf=await getCsrf();const response=await fetch(root(journey)+`/cargas/${id}/bloques/${index}`,{method:'PUT',credentials:'same-origin',cache:'no-store',signal,headers:{[csrf.headerName]:csrf.token,'Content-Type':'application/octet-stream','X-SHA256':hash.blocks[index]},body:file.slice(start,end)});if(!response.ok)throw new ApiError(response.status);state=await response.json() as UploadState;completed=true;
   }catch(error){if(signal.aborted)abort();if(error instanceof ApiError&&![429,503].includes(error.status))throw error;if(attempt===2)throw error;await delay(500*(attempt+1),signal);state=await json<UploadState>(root(journey)+`/cargas/${id}`);completed=state.recibidos>=end&&!state.bloquesFaltantes.includes(index);}
  }
  progress({fase:'CARGANDO',bytes:state.recibidos,id});
 }
 state=await mutation<UploadState>(journey,`/cargas/${id}/finalizar`,'POST',undefined,signal);progress({fase:'ANALIZANDO',bytes:file.size,id});const until=Date.now()+200000;
 while(state.estado==='ANALIZANDO'){
  if(Date.now()>until)throw new ApiError(503);await delay(750,signal);state=await json<UploadState>(root(journey)+`/cargas/${id}`);
 }
 if(state.estado==='VALIDADO'&&state.archivo)return state.archivo;
 throw new ApiError(state.estado==='RECHAZADO'?422:503);
}
