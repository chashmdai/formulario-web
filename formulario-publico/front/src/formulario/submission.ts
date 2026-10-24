import { ApiError, getCsrf, json } from '../api';
import { getCurrentJourney, getJourneyCapabilities, startJourney } from './api';
import type { Journey } from './api';
import type { Attachment, AttachmentPort } from './NnaFlow';

export interface CustodyReceipt {algoritmo: string; keyId: string; payload: string; firma: string}
export interface SubmissionState {
  id: string; estado: 'PENDIENTE_REGISTRO_SIGER' | 'REGISTRADA_SIGER'; custodia: CustodyReceipt;
  registro: null | {registro: {folio: string}; comprobante: CustodyReceipt};
}
export interface CompleteCapture {campos: Record<string,string>; personas: never[]; instituciones: never[]; adjuntos: string[]}
const root = '/api/formulario';
const journeyPath = (id: string) => `${root}/recorridos/${encodeURIComponent(id)}`;
function state(value: SubmissionState): SubmissionState {
  if (!value || !['PENDIENTE_REGISTRO_SIGER','REGISTRADA_SIGER'].includes(value.estado) || !value.id || !value.custodia
    || (value.estado === 'REGISTRADA_SIGER' && !value.registro?.registro?.folio) || (value.estado === 'PENDIENTE_REGISTRO_SIGER' && value.registro !== null)) throw new ApiError(502);
  return value;
}
export async function getSubmission(id: string): Promise<SubmissionState> {return state(await json<SubmissionState>(`${root}/comunicaciones/${encodeURIComponent(id)}`));}
export async function getJourneySubmission(id: string): Promise<SubmissionState> {return state(await json<SubmissionState>(`${journeyPath(id)}/comunicacion`));}
export const getReceptionCapabilities = () => json<{nna: boolean; adultos: boolean}>(`${root}/recepcion/capacidades`);
export const listAttachments = (journey:string) => json<Attachment[]>(`${journeyPath(journey)}/adjuntos`);
export async function submitNna(journey: string, key: string, body: CompleteCapture): Promise<SubmissionState> {
  const csrf = await getCsrf();
  const response = await fetch(`${journeyPath(journey)}/comunicaciones`, {method: 'POST', credentials: 'same-origin', cache: 'no-store',
    headers: {'Content-Type': 'application/json', [csrf.headerName]: csrf.token, 'Idempotency-Key': key}, body: JSON.stringify(body)});
  if (!response.ok) throw new ApiError(response.status);
  return state(await response.json() as SubmissionState);
}
export function attachmentPort(journey: string): AttachmentPort {
  return {
    async replace(files: File[]) {
      if (!files.length) {await this.clear();return [];}
      const csrf = await getCsrf(), body = new FormData();files.forEach(file => body.append('archivos', file));
      const response = await fetch(`${journeyPath(journey)}/adjuntos`, {method:'POST', credentials:'same-origin', cache:'no-store', headers:{[csrf.headerName]:csrf.token}, body});
      if (!response.ok) throw new ApiError(response.status);
      const result = await response.json() as Attachment[];
      if (!Array.isArray(result) || result.length !== files.length || result.some(f => f.estado !== 'VALIDADO' || !f.id || !/^[a-f0-9]{64}$/.test(f.sha256))) throw new ApiError(502);
      return result;
    },
    async clear() {
      const csrf = await getCsrf();const response = await fetch(`${journeyPath(journey)}/adjuntos`, {method:'DELETE', credentials:'same-origin', cache:'no-store', headers:{[csrf.headerName]:csrf.token}});
      if (!response.ok) throw new ApiError(response.status);
    },
  };
}
let entering: Promise<Journey> | null = null;
/** Evita dos altas por el montaje de StrictMode; no guarda datos del formulario en almacenamiento del navegador. */
export function enterNna(): Promise<Journey> {
  if (entering) return entering;
  const pending = (async () => {
    const caps = await getJourneyCapabilities();if (!caps.disponible) throw new ApiError(503);
    try {const current = await getCurrentJourney();if (current.tipo === 'NNA' && ['BORRADOR','RECIBIDO'].includes(current.estado)) return current;}
    catch (error) {if (!(error instanceof ApiError) || error.status !== 404) throw error;}
    return startJourney('NNA', caps.avisoNna, false);
  })();
  entering = pending;void pending.then(() => {entering = null;}, () => {entering = null;});return pending;
}
