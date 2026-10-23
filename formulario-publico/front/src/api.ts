export interface Identity { run: string; nombres: string; apellidos: string; nombresComponentes?: string[] | null; apellidosComponentes?: string[] | null; runNumero?: string | null; runDv?: string | null }
export interface Session { authenticated: boolean; identity: Identity | null; environment: string; loginAvailable: boolean; ingresoScope?: string | null }
export interface Csrf { token: string; parameterName: string; headerName: string }
export interface Preview { identidad: Identity; mensaje: string; almacenada: boolean }
export interface Capabilities { recepcionDisponible: boolean; avisoVersion: string; aviso: string }
export interface SignedReceipt { algoritmo: string; keyId: string; payload: string; firma: string }
export interface InstitutionalRegistration { registro: { comunicacionId: string; operacionPublicaId: string; numeroFolio: number; folio: string; registradaEn: string; canalOrigen: string }; comprobante: SignedReceipt }
export interface Delivery { id: string; estado: 'PENDIENTE_REGISTRO_SIGER' | 'REGISTRADA_SIGER' | 'PENDIENTE_SIGER' | 'CONFIRMADA_SIGER'; confirmacion: { recepcionInstitucionalId: string; registradaEn: string; sha256: string } | null; comprobante?: SignedReceipt | null; registroInstitucional?: InstitutionalRegistration | null }
export interface AttributeValue { valor: string; fuente: string; nivel: string; observadoEn: string; evidenciaRef: string; componentes?: string[] | null }
export interface IdentityAttributes { version: string; atributos: Record<string, AttributeValue[]>; disponibilidadFuentes: Record<string, string> }
export const getIdentityAttributes = () => json<IdentityAttributes>('/api/identidad/atributos');

export class ApiError extends Error {
  constructor(public readonly status: number) { super(status === 401 ? 'La sesión expiró. Vuelve a iniciar sesión.' : 'No pudimos completar la solicitud. Inténtalo nuevamente.'); }
}

export async function json<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { credentials: 'same-origin', cache: 'no-store', ...init });
  if (!response.ok) throw new ApiError(response.status);
  return response.json() as Promise<T>;
}
export const getSession = () => json<Session>('/api/session');
export const getCsrf = () => json<Csrf>('/api/csrf');
export const getCapabilities = () => json<Capabilities>('/api/comunicaciones/capacidades');
export const getDelivery = (id: string) => json<Delivery>(`/api/comunicaciones/${encodeURIComponent(id)}`);
export async function sendCommunication(mensaje: string, key: string, avisoVersion: string): Promise<Delivery> {
  const csrf = await getCsrf();
  return json<Delivery>('/api/comunicaciones', {
    method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token, 'Idempotency-Key': key },
    body: JSON.stringify({ mensaje, avisoPrivacidadLeido: true, avisoVersion }),
  });
}
export async function previewCommunication(mensaje: string): Promise<Preview> {
  const csrf = await getCsrf();
  return json<Preview>('/api/comunicaciones/preview', {
    method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify({ mensaje }),
  });
}
