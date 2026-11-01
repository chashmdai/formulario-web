import {useEffect,useRef,useState} from 'react';
import type {SubmissionState} from '../formulario/submission';
import {Icon} from './FormChrome';

const CHECK_INTERVAL_MS=4000;
export function ReceiptPanel({receipt,busy,error,onDownload,onCheck}:{receipt:SubmissionState;busy:boolean;error?:string;onDownload:(value:unknown,name:string)=>void;onCheck:()=>void|Promise<void>}){
 const section=useRef<HTMLElement>(null);
 const [copy,setCopy]=useState<'idle'|'done'|'failed'>('idle');
 const [delayedId,setDelayedId]=useState<string|null>(null);
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
 return <section ref={section} tabIndex={-1} className="paper receipt" id="communication-receipt" aria-labelledby="receipt-title" aria-live="polite"><div className="paper-body">
  <div className="receipt-mark"><Icon name="check"/></div>
  <span className={`receipt-state ${registered?'confirmed':'pending'}`}>{registered?'Registro institucional confirmado':'Registro institucional pendiente'}</span>
  <h2 className="paper-title" id="receipt-title">{registered?'Comunicación registrada':'Recibimos tu comunicación'}</h2>
  <p className="receipt-lead">{registered?'Tu comunicación cuenta con un registro institucional. Puedes guardar tus comprobantes.':'Tu comunicación se conserva en el Formulario Público. Su registro institucional está pendiente.'}</p>
  {registered&&<p className="receipt-folio">Folio: <strong>{receipt.registro!.registro.folio}</strong></p>}
  {!registered&&<div className="receipt-waiting"><span className="ui-spinner" aria-hidden="true"/><p>{delayedId===receipt.id?'El registro está tardando más de lo habitual. Tu comunicación sigue recibida y puedes guardar tu comprobante de recepción.':'Estamos esperando tu folio institucional.'}<small>Esta pantalla se actualizará automáticamente. No necesitas volver a enviar tu comunicación.</small></p></div>}
  <ol className="receipt-timeline" aria-label="Estado de tu comunicación"><li className="complete"><Icon name="check" small/><div><strong>Comunicación recibida</strong><span>Tu información quedó guardada.</span></div></li><li className={registered?'complete':'waiting'}><Icon name={registered?'check':'paper'} small/><div><strong>{registered?'Registro confirmado':'Registro pendiente'}</strong><span>{registered?'El comprobante institucional está disponible.':'Aún no se ha emitido el folio institucional.'}</span></div></li></ol>
  <div className="receipt-reference"><span>Tu referencia de recepción</span><div><code>{receipt.id}</code><button className="copy-reference" type="button" onClick={()=>{void copyReference();}}>{copy==='done'?'Copiada':'Copiar referencia'}</button></div>{copy==='failed'&&<small>Puedes seleccionar la referencia y copiarla manualmente.</small>}</div>
  <div className="receipt-next"><Icon name="paper"/><div><h3>Guarda tus comprobantes</h3><p>El comprobante de recepción acredita que recibimos tu comunicación. {registered?'El comprobante institucional acredita su registro en SIGER e incluye el folio.':'Cuando se confirme el registro en SIGER, también podrás descargar el comprobante institucional con tu folio.'}</p>{registered&&<p className="receipt-follow-up"><strong>Anota y guarda el folio de tu comunicación en un lugar seguro.</strong> Es importante para consultar el estado y dar seguimiento a tu gestión.</p>}</div></div>
  <div className="receipt-actions"><button type="button" className={`btn ${registered?'btn-secondary':'btn-primary'}`} onClick={()=>onDownload(receipt.custodia,`comprobante-custodia-${receipt.id}.json`)}><Icon name="upload" small/>Descargar comprobante de recepción</button>{receipt.registro&&<button type="button" className="btn btn-primary" onClick={()=>onDownload(receipt.registro,`comprobante-institucional-${receipt.id}.json`)}>Descargar comprobante institucional</button>}{!registered&&<button type="button" className="btn btn-secondary" disabled={busy} aria-busy={busy} onClick={()=>{void Promise.resolve(onCheck()).catch(()=>{});}}>{busy?'Consultando estado…':error?'Reintentar consulta':'Actualizar estado'}</button>}</div>
  {error&&<p className="field-error receipt-error" role="alert">{error}</p>}
 </div></section>;
}
