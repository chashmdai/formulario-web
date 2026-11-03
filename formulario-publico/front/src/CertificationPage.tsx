import {useEffect, useState} from 'react';
import {getCsrf, getSession} from './api';
import type {Csrf, Session} from './api';
import {ClaveUnicaButton} from './ClaveUnicaButton';
import {InstitutionalHeader} from './prototipo/InstitutionalHeader';
import {InstitutionalFooter} from './prototipo/InstitutionalFooter';
import './prototipo/prototipo.css';
import './prototipo/inicio.css';
import './certification.css';

export default function CertificationPage(){
  const [session,setSession]=useState<Session|null>(null),[csrf,setCsrf]=useState<Csrf|null>(null),[error,setError]=useState(false);
  const [theme,setTheme]=useState('light'),[scale,setScale]=useState(100);
  useEffect(()=>{document.title='Certificación ClaveÚnica | Defensoría de la Niñez';let active=true;
    void getSession().then(async value=>{const token=value.authenticated?await getCsrf():null;if(active){setSession(value);setCsrf(token);}})
      .catch(()=>{if(active)setError(true);});return()=>{active=false;};},[]);
  useEffect(()=>{document.documentElement.dataset.theme=theme;document.documentElement.style.setProperty('--ui-scale',String(scale/100));},[theme,scale]);
  return <>
    <a className="skip" href="#certification">Saltar al contenido</a>
    <InstitutionalHeader title="Formulario Público" theme={theme} scale={scale} onTheme={setTheme}
      onIncrease={()=>setScale(v=>Math.min(130,v+10))} onDecrease={()=>setScale(v=>Math.max(100,v-10))}/>
    <main id="certification" className="shell certification">
      <p className="certification-kicker">Defensoría de la Niñez</p>
      <h1>Certificación de ClaveÚnica</h1>
      <div className="certification-notice"><h2>Recepción de comunicaciones suspendida</h2>
        <p>Este sitio está habilitado exclusivamente para la certificación oficial de ClaveÚnica. La recepción de comunicaciones ciudadanas se encuentra suspendida.</p></div>
      {error?<p role="alert">No pudimos comprobar la sesión. Recarga la página para volver a intentarlo.</p>:!session?<p role="status">Comprobando sesión…</p>:session.authenticated?<section aria-label="Resultado de autenticación">
        <h2 role="status">Autenticación realizada correctamente</h2>
        <p>La sesión de ClaveÚnica está activa.</p>
        {csrf&&<form method="post" action="/auth/logout"><input type="hidden" name={csrf.parameterName} value={csrf.token}/><button className="certification-logout" type="submit">Cerrar sesión</button></form>}
      </section>:<section aria-label="Acceso ClaveÚnica">
        {new URLSearchParams(window.location.search).get('auth')==='failed'&&<p role="alert">No se pudo completar la autenticación. Vuelve a iniciar sesión.</p>}
        {new URLSearchParams(window.location.search).get('session')==='closed'&&<p role="status">Sesión cerrada.</p>}
        {session.loginAvailable?<ClaveUnicaButton/>:<p role="status">El acceso a ClaveÚnica está pendiente de configuración.</p>}
      </section>}
    </main>
    <InstitutionalFooter note="Sitio destinado exclusivamente a la certificación de ClaveÚnica. Recepción ciudadana suspendida."/>
  </>;
}
