import {useEffect,useRef,useState} from 'react';
import type {SubmissionState} from '../formulario/submission';
import {custodyFilename,registryFilename} from '../comprobante/evidence';
import {downloadReceiptPdf} from '../comprobante/download';
import {Icon} from './FormChrome';

const CHECK_INTERVAL_MS=4000;
export function ReceiptPanel({receipt,busy,error,onDownload,onCheck,onPdf=downloadReceiptPdf}:{receipt:SubmissionState;busy:boolean;error?:string;onDownload:(value:unknown,name:string)=>void;onCheck:()=>void|Promise<void>;onPdf?:(receipt:SubmissionState)=>Promise<unknown>}){
 const section=useRef<HTMLElement>(null);
 const [copy,setCopy]=useState<'idle'|'done'|'failed'>('idle');
 const [delayedId,setDelayedId]=useState<string|null>(null);
 const [pdf,setPdf]=useState<'idle'|'busy'|'failed'>('idle');
 const registered=receipt.estado==='REGISTRADA_SIGER'&&!!receipt.registro;
 useEffect(()=>{section.current?.scrollIntoView?.({block:'start',behavior:'auto'});section.current?.focus({preventScroll:true});},[]);
 useEffect(()=>{
  if(registered||busy)return;
  const timer=window.setTimeout(()=>{void Promise.resolve(onCheck()).catch(()=>{});},CHECK_INTERVAL_MS);
  return()=>window.clearTimeout(timer);
 },[receipt.id,registered,busy,onCheck]);
 useEffect(()=>{
  if(registered)return;
  const timer=window.setTimeout(()=>setDelayedId(receipt.id),60000);
  return()=>window.clearTimeout(timer);
 },[receipt.id,registered]);
 async function copyReference(){try{if(!navigator.clipboard)throw new Error();await navigator.clipboard.writeText(receipt.id);setCopy('done');}catch{setCopy('failed');}}
 async function downloadPdf(){if(pdf==='busy')return;setPdf('busy');try{await onPdf(receipt);setPdf('idle');}catch{setPdf('failed');}}
 return <section ref={section} tabIndex={-1} className="paper receipt" id="communication-receipt" aria-labelledby="receipt-title" aria-live="polite"><div className="paper-body">
  <div className="receipt-mark"><Icon name="check"/></div>
  <span className={`receipt-state ${registered?'confirmed':'pending'}`}>{registered?'Registro institucional confirmado':'Registro institucional pendiente'}</span>
  <h2 className="paper-title" id="receipt-title">{registered?'Comunicación registrada':'Recibimos tu comunicación'}</h2>
  <p className="receipt-lead">{registered?'Tu comunicación cuenta con un registro institucional. Puedes guardar tu comprobante.':'Tu comunicación se conserva en el Formulario Público. Su registro institucional está pendiente.'}</p>
  {registered&&<p className="receipt-folio">Folio: <strong>{receipt.registro!.registro.folio}</strong></p>}
  {!registered&&<div className="receipt-waiting"><span className="ui-spinner" aria-hidden="true"/><p>{delayedId===receipt.id?'El registro está tardando más de lo habitual. Tu comunicación sigue recibida y puedes guardar tu comprobante de recepción.':'Estamos esperando tu folio institucional.'}<small>Esta pantalla se actualizará automáticamente. No necesitas volver a enviar tu comunicación.</small></p></div>}
  <ol className="receipt-timeline" aria-label="Estado de tu comunicación"><li className="complete"><Icon name="check" small/><div><strong>Comunicación recibida</strong><span>Tu información quedó guardada.</span></div></li><li className={registered?'complete':'waiting'}><Icon name={registered?'check':'paper'} small/><div><strong>{registered?'Registro confirmado':'Registro pendiente'}</strong><span>{registered?'El comprobante institucional está disponible.':'Aún no se ha emitido el folio institucional.'}</span></div></li></ol>
  <div className="receipt-reference"><span>Tu referencia de recepción</span><div><code>{receipt.id}</code><button className="copy-reference" type="button" onClick={()=>{void copyReference();}}>{copy==='done'?'Copiada':'Copiar referencia'}</button></div>{copy==='failed'&&<small>Puedes seleccionar la referencia y copiarla manualmente.</small>}</div>
  <div className="receipt-next"><Icon name="paper"/><div><h3>Guarda tu comprobante</h3><p>El comprobante PDF resume lo que enviaste, indica el estado de tu comunicación {registered?'e incluye tu folio institucional.':'y tu referencia de recepción. Cuando SIGER confirme el registro, podrás descargarlo nuevamente con tu folio.'} Es una representación legible y no está firmado digitalmente: las evidencias firmadas se incluyen al final y también puedes descargarlas por separado.</p>{registered&&<p className="receipt-follow-up"><strong>Anota y guarda el folio de tu comunicación en un lugar seguro.</strong> Es importante para consultar el estado y dar seguimiento a tu gestión.</p>}</div></div>
  <div className="receipt-actions"><button type="button" className="btn btn-primary" disabled={pdf==='busy'} aria-busy={pdf==='busy'||undefined} onClick={()=>{void downloadPdf();}}><Icon name="upload" small/>{pdf==='busy'?'Preparando comprobante…':'Descargar comprobante PDF'}</button>{!registered&&<button type="button" className="btn btn-secondary" disabled={busy} aria-busy={busy} onClick={()=>{void Promise.resolve(onCheck()).catch(()=>{});}}>{busy?'Consultando estado…':error?'Reintentar consulta':'Actualizar estado'}</button>}</div>
  {pdf==='failed'&&<p className="field-error receipt-error" role="alert">No pudimos preparar el comprobante PDF. Inténtalo nuevamente. Tu comunicación sigue recibida y las evidencias técnicas continúan disponibles.</p>}
  <details className="receipt-evidence"><summary>Evidencias técnicas originales (JSON)</summary>
   <p>Son los comprobantes firmados digitalmente con Ed25519, sin modificaciones. Permiten verificar técnicamente la integridad de la recepción{registered?' y del registro institucional':''}.</p>
   <div className="receipt-evidence-actions"><button type="button" className="btn btn-secondary" onClick={()=>onDownload(receipt.custodia,custodyFilename(receipt.id))}>Descargar evidencia de custodia (JSON)</button>{receipt.registro&&<button type="button" className="btn btn-secondary" onClick={()=>onDownload(receipt.registro,registryFilename(receipt.id))}>Descargar registro institucional (JSON)</button>}</div>
  </details>
  {error&&<p className="field-error receipt-error" role="alert">{error}</p>}
 </div></section>;
}
