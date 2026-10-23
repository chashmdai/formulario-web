import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError, getCsrf, getSession, previewCommunication } from './api';
import type { Csrf, Preview, Session } from './api';
import { ClaveUnicaButton } from './ClaveUnicaButton';
import { DeliveryPanel } from './DeliveryPanel';
import { NnaForm } from './nna/NnaForm';

export default function App() {
  const nna = new URLSearchParams(window.location.search).get("formulario") === "nna";
  const [session, setSession] = useState<Session | null>(null);
  const [csrf, setCsrf] = useState<Csrf | null>(null);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');
  const [preview, setPreview] = useState<Preview | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    void Promise.all([getSession(), getCsrf()]).then(([s, c]) => {
      if (active) { setSession(s); setCsrf(c); }
    }).catch(() => { if (active) setError('No pudimos conectar con el Formulario Público. Recarga la página para reintentar.'); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (nna || !session?.authenticated) return;
    const refresh = () => {
      void getSession().then((s) => {
        if (!s.authenticated) window.location.assign('/auth/session-expired');
      }).catch(() => setError('No pudimos comprobar la sesión. Reintenta antes de continuar.'));
    };
    window.addEventListener('focus', refresh);
    return () => window.removeEventListener('focus', refresh);
  }, [session?.authenticated, nna]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(''); setPreview(null);
    try { setPreview(await previewCommunication(mensaje)); }
    catch (e) {
      if (e instanceof ApiError && e.status === 401) window.location.assign('/auth/session-expired');
      else setError(e instanceof ApiError && e.status === 400 ? 'Escribe un mensaje de 1 a 2000 caracteres.' : 'No pudimos preparar la vista previa. Reintenta.');
    } finally { setBusy(false); }
  }

  return <>
    <header><div className="brand"><span className="brand-mark" aria-hidden="true">D</span><div><strong>Defensoría de la Niñez</strong><span>Formulario Público · Atención ciudadana</span></div></div>
      {session && session.environment !== 'PRODUCTION' && <span className="environment">Ambiente de pruebas · {session.environment}</span>}
    </header>
    <main>
      <div className="intro"><span className="eyebrow">Portal ciudadano</span><h1>Tu voz es importante.</h1>
        <p>{nna ? "Prototipo NNA: solo para pruebas con datos sintéticos." : "Inicia sesión para verificar tu identidad y preparar una Comunicación a la Defensoría de la Niñez."}</p><nav aria-label="Formulario"><a href="/">Acceso adulto con ClaveÚnica</a> · <a href="/?formulario=nna">Formulario NNA provisional</a></nav></div>
      {error && <p className="notice error" role="alert">{error}</p>}
      {!session && !error && <p role="status">Conectando con el Formulario Público…</p>}
      {!nna && session && !session.authenticated && <section className="card login" aria-labelledby="login-title">
        <div className="step">01 · Verifica tu identidad</div><h2 id="login-title">Ingresa con tu ClaveÚnica</h2>
        <p>Continuarás al sitio oficial de Gobierno Digital. Luego regresarás al Formulario Público con tus datos verificados.</p>
        <ClaveUnicaButton />
        {!session.loginAvailable && <p className="notice" role="status">El inicio de sesión está pendiente de habilitación del callback institucional.</p>}
        {new URLSearchParams(window.location.search).get('auth') === 'failed' && <p className="notice error" role="alert">No pudimos validar el retorno de autenticación. Vuelve a iniciar sesión desde el Formulario Público.</p>}
        {new URLSearchParams(window.location.search).get('session') === 'closed' && <p role="status">Tu sesión se cerró. Si el ingreso anterior falló, vuelve a intentarlo.</p>}
      </section>}
      {!nna && session?.authenticated && session.identity && <section className="card" aria-labelledby="identity-title">
        <div className="section-heading"><div><div className="step">01 · Identidad verificada</div><h2 id="identity-title">Ya puedes continuar</h2></div>
          {csrf && <form action="/auth/logout" method="post"><input type="hidden" name={csrf.parameterName} value={csrf.token} /><button className="secondary" type="submit">Cerrar sesión</button></form>}
        </div>
        <p>Estos datos provienen de ClaveÚnica y están asociados a tu sesión.</p>
        <div className="identity-grid">
          {session.identity.runNumero != null && session.identity.runDv != null ? <>
            <label>Número de RUN<input value={session.identity.runNumero} readOnly /></label>
            <label>Dígito verificador<input value={session.identity.runDv} readOnly /></label>
          </> : <label>RUN<input value={session.identity.run} readOnly /></label>}
          {session.identity.nombresComponentes ? session.identity.nombresComponentes.slice(0, 2).map((nombre, index) =>
            <label key={`nombre-${index}`}>{['Primer nombre', 'Segundo nombre'][index]}<input value={nombre} readOnly /></label>)
            : <label>Nombres<input value={session.identity.nombres} readOnly /></label>}
          {session.identity.apellidosComponentes ? session.identity.apellidosComponentes.slice(0, 2).map((apellido, index) =>
            <label key={`apellido-${index}`}>{['Primer apellido', 'Segundo apellido'][index]}<input value={apellido} readOnly /></label>)
            : <label>Apellidos<input value={session.identity.apellidos} readOnly /></label>}
        </div>
        <form onSubmit={(event) => { void submit(event); }} className="communication">
          <div className="step">02 · Prepara una Comunicación</div><h2>Cuéntanos lo que quieres comunicar</h2>
          <p>Primero revisa el mensaje junto a tu identidad. El envío de prueba, cuando está habilitado, es un paso posterior.</p>
          <label htmlFor="mensaje">Mensaje</label><textarea id="mensaje" required maxLength={2000} rows={5} value={mensaje} onChange={(e) => { setMensaje(e.target.value); setPreview(null); }} />
          <span className="counter">{mensaje.length} / 2000 caracteres</span>
          <button className="primary" disabled={busy || !mensaje.trim()}>{busy ? 'Preparando…' : 'Revisar Comunicación'}</button>
        </form>
        {preview && <><div className="notice preview" role="status"><h3>Vista previa de la Comunicación</h3><p>Se utilizará tu identidad verificada por ClaveÚnica.</p><p className="message">{preview.mensaje}</p><p>Vista previa preparada. Este paso no envía ni almacena la Comunicación.</p></div><DeliveryPanel mensaje={preview.mensaje} /></>}
      </section>}
      {nna && <NnaForm />}
      {!nna && <aside className="privacy"><strong>Tu identidad, protegida</strong><p>El Formulario Público recibe tu RUN y nombre desde ClaveÚnica. Tu contraseña se ingresa exclusivamente en el sitio oficial.</p></aside>}
    </main><footer>Defensoría de la Niñez · Formulario Público</footer>
  </>;
}
