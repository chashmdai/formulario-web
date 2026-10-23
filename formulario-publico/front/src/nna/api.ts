import { getCsrf, json } from '../api';
import type { Capabilities, Delivery } from '../api';
import type { DeliveryAdapter } from '../DeliveryPanel';
import type { NnaData } from './fields';
export const getNnaCapabilities = () => json<Capabilities>('/api/nna/capacidades');
export function nnaAdapter(datos: NnaData): DeliveryAdapter {
  return {
    operationInput: JSON.stringify(datos),
    capabilities: getNnaCapabilities,
    get: id => json<Delivery>(`/api/nna/comunicaciones/${encodeURIComponent(id)}`),
    receiptUrl: id => `/api/nna/comunicaciones/${encodeURIComponent(id)}/comprobante`,
    send: async (mensaje, key, avisoVersion) => {
      const csrf = await getCsrf();
      return json<Delivery>('/api/nna/comunicaciones', { method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token, 'Idempotency-Key': key }, body: JSON.stringify({ datos, mensaje, avisoPrivacidadLeido: true, avisoVersion }) });
    },
  };
}
