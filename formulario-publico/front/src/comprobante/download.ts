import {ApiError} from '../api';
import type {SubmissionState} from '../formulario/submission';
import {RegistrationNotConfirmedError,requireConfirmedRegistration,saveBlob} from './evidence';
import {buildReceiptDocument,pdfFilename} from './receiptModel';
import type {ReceiptDocument} from './receiptModel';
import {getSubmissionSummary} from './summaryApi';
import type {SubmissionSummary} from './summaryApi';

/**
 * Prepara el modelo del comprobante. El contenido se pide al servidor en cada descarga (también tras recargar):
 * no se conserva en almacenamiento del navegador. Si la sesión ya no es propietaria (404), se genera un
 * comprobante reducido con las evidencias disponibles; otros fallos no producen un documento incompleto.
 * Sin registro institucional confirmado se rechaza antes de consultar el servidor o cargar el generador.
 */
export async function prepareReceipt(state:SubmissionState,now=new Date()):Promise<ReceiptDocument>{
  requireConfirmedRegistration(state);
  let summary:SubmissionSummary|null=null;
  try{summary=await getSubmissionSummary(state.id);}
  catch(error){if(!(error instanceof ApiError)||error.status!==404)throw error;}
  return buildReceiptDocument(summary,state,now);
}
/**
 * `current` devuelve el estado vigente en la página: si durante la preparación deja de estar confirmado o cambia
 * el folio, el documento se descarta sin descargarse.
 */
export async function downloadReceiptPdf(state:SubmissionState,current:()=>SubmissionState=()=>state):Promise<ReceiptDocument>{
  requireConfirmedRegistration(state);
  const model=await prepareReceipt(state);
  const [{renderReceiptPdf},{loadPdfAssets}]=await Promise.all([import('./pdf'),import('./assets')]);
  const bytes=await renderReceiptPdf(model,await loadPdfAssets());
  const latest=current();
  if(latest.id!==model.reference||requireConfirmedRegistration(latest).folio!==model.folio)throw new RegistrationNotConfirmedError();
  saveBlob(bytes as Uint8Array<ArrayBuffer>,'application/pdf',pdfFilename(model.reference));
  return model;
}
