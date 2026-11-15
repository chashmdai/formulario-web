import {ApiError} from '../api';
import type {SubmissionState} from '../formulario/submission';
import {saveBlob} from './evidence';
import {buildReceiptDocument,pdfFilename} from './receiptModel';
import type {ReceiptDocument} from './receiptModel';
import {getSubmissionSummary} from './summaryApi';
import type {SubmissionSummary} from './summaryApi';

/**
 * Prepara el modelo del comprobante. El contenido se pide al servidor en cada descarga (también tras recargar):
 * no se conserva en almacenamiento del navegador. Si la sesión ya no es propietaria (404), se genera un
 * comprobante reducido con las evidencias disponibles; otros fallos no producen un documento incompleto.
 */
export async function prepareReceipt(state:SubmissionState,now=new Date()):Promise<ReceiptDocument>{
  let summary:SubmissionSummary|null=null;
  try{summary=await getSubmissionSummary(state.id);}
  catch(error){if(!(error instanceof ApiError)||error.status!==404)throw error;}
  return buildReceiptDocument(summary,state,now);
}
export async function downloadReceiptPdf(state:SubmissionState):Promise<ReceiptDocument>{
  const model=await prepareReceipt(state);
  const [{renderReceiptPdf},{loadPdfAssets}]=await Promise.all([import('./pdf'),import('./assets')]);
  const bytes=await renderReceiptPdf(model,await loadPdfAssets());
  saveBlob(bytes as Uint8Array<ArrayBuffer>,'application/pdf',pdfFilename(model.reference));
  return model;
}
