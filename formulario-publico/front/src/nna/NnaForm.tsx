import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { DeliveryPanel } from '../DeliveryPanel';
import { emptyData, fields, validRun } from './fields';
import type { NnaData } from './fields';
import { getNnaCapabilities, nnaAdapter } from './api';
export function NnaForm() {
  const [data, setData] = useState<NnaData>(emptyData);
  const [message, setMessage] = useState('');
  const [available, setAvailable] = useState(false);
  const [prepared, setPrepared] = useState(false);
  const [error, setError] = useState('');
  const adapter = useMemo(() => ({ ...nnaAdapter(data), operationInput: JSON.stringify([data, message]) }), [data, message]);
  useEffect(() => { let active = true; void getNnaCapabilities().then(c => { if (active) setAvailable(c.recepcionDisponible); }).catch(() => {}); return () => { active = false; }; }, []);
  function review(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError('');
    if (!validRun(data.runNumero, data.runDv)) { setError('Revisa el número de RUN/RUT y su dígito verificador, o deja ambos vacíos.'); return; }
    if (fields.some(f => f.required && !data[f.key]?.trim()) || !message.trim()) { setError('Completa los campos obligatorios.'); return; }
    setPrepared(true);
  }
  return <section className="card" aria-labelledby="nna-title">
    <h2 id="nna-title">Queremos escucharte</h2>
    <p className="notice">Puedes contarnos lo que necesitas con tus propias palabras. Los datos que informes no verifican tu identidad.</p>
    {!available ? <p role="status">El ingreso NNA está deshabilitado.</p> : <>
      <form onSubmit={review} autoComplete="off">
        <fieldset disabled={prepared}><legend>Sobre ti</legend><div className="identity-grid">
          {fields.map(f => <label key={f.key}>{f.label}<input name={f.key} required={f.required} maxLength={f.max} value={data[f.key] ?? ''} autoComplete="off" onChange={e => setData({ ...data, [f.key]: e.target.value === '' && !f.required ? null : e.target.value })} /></label>)}
        </div><label>Cuéntanos<textarea required maxLength={2000} rows={6} value={message} autoComplete="off" onChange={e => setMessage(e.target.value)} /></label>
        <p>{message.length} / 2000 caracteres</p><button className="primary">Revisar mis respuestas</button></fieldset>
      </form>
      {prepared && <><p>Tus datos están listos para revisar. Si aún no has enviado y recargas la página, deberás completar de nuevo el formulario.</p><DeliveryPanel mensaje={message} adapter={adapter} /></>}
      {error && <p role="alert">{error}</p>}
    </>}
  </section>;
}
