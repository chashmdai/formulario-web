import { useEffect, useRef, useState } from 'react';
import { getCapabilities, getDelivery, sendCommunication } from './api';
import type { Capabilities, Delivery } from './api';
export interface DeliveryAdapter { capabilities: () => Promise<Capabilities>; get: (id: string) => Promise<Delivery>; send: (mensaje: string, key: string, aviso: string) => Promise<Delivery>; receiptUrl: (id: string) => string; operationInput?: string }
const adultAdapter: DeliveryAdapter = { capabilities: getCapabilities, get: getDelivery, send: sendCommunication, receiptUrl: id => `/api/comunicaciones/${encodeURIComponent(id)}/comprobante` };

export function DeliveryPanel({ mensaje, adapter = adultAdapter }: { mensaje: string; adapter?: DeliveryAdapter }) {
  const [capabilities, setCapabilities] = useState<Capabilities | null>(null);
  const [read, setRead] = useState(false);
  const [delivery, setDelivery] = useState<Delivery | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const keys = useRef(new Map<string, string>());
  useEffect(() => {
    let active = true;
    void adapter.capabilities().then(c => { if (active) setCapabilities(c); }).catch(() => {});
    return () => { active = false; };
  }, [adapter]);
  useEffect(() => {
    if (!delivery || delivery.estado === 'REGISTRADA_SIGER') return;
    let active = true; let attempt = 0; let timer: ReturnType<typeof setTimeout>;
    const poll = async () => {
      try { const updated = await adapter.get(delivery.id); if (active) setDelivery(updated); }
      catch { /* Se conserva recepción pendiente y consulta manual; no se infiere un registro. */ }
      if (active && ++attempt < 20) timer = setTimeout(() => { void poll(); }, Math.min(15000, 2000 * 2 ** Math.floor(attempt / 3)));
    };
    timer = setTimeout(() => { void poll(); }, 2000);
    return () => { active = false; clearTimeout(timer); };
  }, [delivery?.id, delivery?.estado]);
  if (!capabilities?.recepcionDisponible && !delivery) return <p>La recepción de Comunicaciones está deshabilitada. Puedes revisar una vista previa.</p>;
  const registration = delivery?.estado === 'REGISTRADA_SIGER' && delivery.comprobante ? delivery.registroInstitucional?.registro : null;

  async function send() {
    setBusy(true); setError('');
    try {
      if (!capabilities) return;
      const input = JSON.stringify([capabilities.avisoVersion, adapter.operationInput ?? mensaje]);
      const key = keys.current.get(input) ?? crypto.randomUUID();
      keys.current.set(input, key);
      setDelivery(await adapter.send(mensaje, key, capabilities.avisoVersion));
    }
    catch { setError('No pudimos confirmar la respuesta. El envío puede haber llegado al servidor. Reintenta aquí con los mismos datos y la misma clave. Si recargas, se perderá esta consulta; lo aceptado en MySQL se conserva.'); }
    finally { setBusy(false); }
  }
  async function refresh() {
    if (!delivery) return;
    setBusy(true); setError('');
    try { setDelivery(await adapter.get(delivery.id)); }
    catch { setError('No se pudo consultar el estado. Reintenta en esta pestaña antes de recargar.'); }
    finally { setBusy(false); }
  }
  return <section className="notice" aria-label="Entrega de prueba">
    <h3>Recepción y registro de la Comunicación</h3><p>{capabilities?.aviso}</p>
    {!delivery && <><label><input type="checkbox" checked={read} onChange={e => setRead(e.target.checked)} /> He leído el aviso de esta demostración.</label>
      <button type="button" className="primary" disabled={busy || !read || !mensaje.trim()} onClick={() => { void send(); }}>Enviar Comunicación de prueba</button></>}
    {delivery && <div role="status"><p>Identificador técnico de recepción: {delivery.id}</p><p>{registration
      ? 'Comunicación registrada en SIGER.'
      : 'Recepción técnica pendiente de confirmación de registro en SIGER. Aún no disponemos del folio ni del comprobante institucional definitivo.'}</p>
      {registration && <dl><dt>Folio institucional</dt><dd>{registration.folio}</dd><dt>Identificador de Comunicación</dt><dd>{registration.comunicacionId}</dd><dt>Fecha de registro</dt><dd>{new Date(registration.registradaEn).toLocaleString('es-CL')}</dd><dt>Canal de origen</dt><dd>{registration.canalOrigen}</dd></dl>}
      <button type="button" disabled={busy} onClick={() => { void refresh(); }}>Consultar recepción en SIGER</button></div>}
    {registration && delivery?.comprobante && <p><a href={adapter.receiptUrl(delivery.id)} download>Descargar comprobante definitivo de registro en SIGER</a></p>}
    {error && <p role="alert">{error}</p>}
  </section>;
}
