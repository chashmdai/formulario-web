import { useEffect, useMemo, useRef, useState } from 'react';
import '@fontsource/cabin/400.css';
import '@fontsource/cabin/500.css';
import '@fontsource/cabin/600.css';
import '@fontsource/cabin/700.css';
import { ApiError, getCsrf, getSession } from '../api';
import type { Csrf, Session } from '../api';
import { ClaveUnicaButton } from '../ClaveUnicaButton';
import { InstitutionalHeader } from '../prototipo/InstitutionalHeader';
import { InstitutionalFooter } from '../prototipo/InstitutionalFooter';
import { PrivacyDialog } from '../prototipo/PrivacyDialog';
import { getCurrentJourney, getJourneyCapabilities, startJourney } from './api';
import type { Journey } from './api';
import { AdultFlow } from './AdultFlow';
import type { AdultNavigation } from './AdultFlow';
import { ReviewAdult } from './ReviewAdult';
import type { AdultReview } from './ReviewAdult';
import type { Attachment } from './NnaFlow';
import { attachmentPort, getJourneySubmission, getReceptionCapabilities, getSubmission, listAttachments, submitCapture } from './submission';
import type { CompleteCapture, SubmissionState } from './submission';
import { usePreferences } from './preferences';
import '../prototipo/prototipo.css';
import '../prototipo/inicio.css';

export default function AdultPage({initialJourney,mode='manual'}:{initialJourney?:Journey;mode?:'manual'|'cu'}){
  const [journey,setJourney]=useState<Journey|null>(null),[session,setSession]=useState<Session|null>(null),[available,setAvailable]=useState(false);
  const [full,setFull]=useState(false),[step,setStep]=useState(1),[retry,setRetry]=useState(0),[loading,setLoading]=useState(true);
  const [privacy,setPrivacy]=useState(false),[fatal,setFatal]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false),[locked,setLocked]=useState(false);
  const [review,setReview]=useState<AdultReview|null>(null),[receipt,setReceipt]=useState<SubmissionState|null>(null),[initialFiles,setInitialFiles]=useState<Attachment[]>([]);
  const [csrf,setCsrf]=useState<Csrf|null>(null);
  const navigation=useRef<AdultNavigation>(null),attempt=useRef<{key:string;body:CompleteCapture}|null>(null),sending=useRef(false);
  const preferences=usePreferences(full),kind=mode==='cu'?'ADULTO_CU':'ADULTO_DECLARADO';
  const files=useMemo(()=>journey?attachmentPort(journey.id):null,[journey]);
  useEffect(()=>{
    let active=true;document.title='Formulario para personas adultas | Defensoría de la Niñez';
    void Promise.all([getSession(),getReceptionCapabilities(),initialJourney?Promise.resolve(initialJourney):getCurrentJourney().catch(e=>{if(!(e instanceof ApiError)||e.status!==404)throw e;return null;})]).then(async([s,c,j])=>{
      if(!j||j.tipo!==kind){if(active){setSession(s);setAvailable(c.adultos);setPrivacy(true);setLoading(false);}return;}
      if(!j.aceptaCanal)throw new ApiError(409);
      const restored=j.estado==='RECIBIDO'?await getJourneySubmission(j.id):null;
      const attached=restored?[]:await listAttachments(j.id);
      if(active){setSession(s);setAvailable(c.adultos);setJourney(j);setReceipt(restored);setInitialFiles(attached);setFatal('');setLoading(false);}
    }).catch(()=>{if(active){setFatal('No pudimos abrir tu formulario. Inténtalo nuevamente.');setLoading(false);}});
    return()=>{active=false;};
  },[initialJourney,kind,retry]);
  useEffect(()=>{
    if(kind!=='ADULTO_CU'||!session?.authenticated)return;
    let active=true;void getCsrf().then(c=>{if(active)setCsrf(c);}).catch(()=>{});
    const refresh=()=>{void getSession().then(setSession).catch(()=>setError('No pudimos comprobar la sesión. Inténtalo nuevamente antes de enviar.'));};
    window.addEventListener('focus',refresh);return()=>{active=false;window.removeEventListener('focus',refresh);};
  },[kind,session?.authenticated]);
  async function accept(){
    if(sending.current)return;sending.current=true;setBusy(true);setError('');
    try{
      const [caps,s]=await Promise.all([getJourneyCapabilities(),getSession()]);if(!caps.disponible)throw new ApiError(503);
      if(kind==='ADULTO_CU'&&!s.authenticated&&!s.loginAvailable)throw new ApiError(503);
      const j=await startJourney(kind,caps.avisoAdulto,true);setJourney(j);setSession(s);setPrivacy(false);setInitialFiles([]);
      if(kind==='ADULTO_CU'&&!s.authenticated)window.location.assign('/oauth2/authorization/claveunica');
    }catch{setError('Este acceso está temporalmente cerrado. Inténtalo nuevamente más tarde.');}
    finally{sending.current=false;setBusy(false);}
  }
  async function send(){
    if(!journey||!review||sending.current)return;if(!available){setError('La recepción está temporalmente cerrada. Vuelve a intentarlo más tarde.');return;}
    if(!attempt.current)attempt.current={key:crypto.randomUUID(),body:review.capture};sending.current=true;setBusy(true);setLocked(true);setError('');
    try{setReceipt(await submitCapture(journey.id,attempt.current.key,attempt.current.body));setReview(null);}
    catch(e){setError(e instanceof ApiError&&e.status===401?'Tu sesión terminó. No podemos confirmar la recepción desde esta sesión.':'No pudimos confirmar la recepción. Comprueba el estado o reintenta el mismo envío para evitar duplicados.');}
    finally{sending.current=false;setBusy(false);}
  }
  async function check(){const id=receipt?.id||attempt.current?.key;if(!id||sending.current)return;sending.current=true;setBusy(true);setError('');try{setReceipt(await getSubmission(id));setReview(null);}catch{setError('No pudimos recuperar una recepción confirmada. Puedes reintentar el mismo envío.');}finally{sending.current=false;setBusy(false);}}
  function download(value:unknown,name:string){const url=URL.createObjectURL(new Blob([JSON.stringify(value,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download=name;document.body.append(a);a.click();a.remove();URL.revokeObjectURL(url);}
  const authenticated=journey?.tipo!=='ADULTO_CU'||session?.authenticated;
  return <>
    <a className="skip" href="#principal">Saltar al formulario</a><InstitutionalHeader {...preferences} title="Formulario para personas adultas" onHome={()=>window.location.assign('/?vista=inicio')} />
    <div className="shell" id="form-experience"><section className="hero" aria-labelledby="titulo"><div className="hero-text"><p className="eyebrow"><span className="accent-line" aria-hidden="true" /> Comunícate con la Defensoría</p><h1 id="titulo">¿En qué podemos ayudarte?</h1><p id="hero-description">Completa solo los datos necesarios y cuéntanos por qué te comunicas con la Defensoría.</p></div></section>
      <div className="form-toolbar" aria-label="Presentación del formulario"><div className="toolbar-title"><span className="toolbar-overline">TU FORMULARIO</span><strong id="view-current">Persona adulta · {kind==='ADULTO_CU'?'ClaveÚnica':'Identificación declarada'}</strong><small>Ambiente de pruebas · Usa solo datos de prueba</small></div><div className="toolbar-controls"><span className="step-info" id="step-info" aria-live="polite">{full?'Formulario completo':`Paso ${step} de 3`}</span><div className="view-toggle" role="group" aria-label="Forma de visualizar el formulario"><button type="button" aria-pressed={!full} onClick={()=>setFull(false)}>Por pasos</button><button type="button" aria-pressed={full} onClick={()=>setFull(true)}>Completo</button></div></div></div>
      <div className="page-layout" id="public-surface"><aside className="side-column" aria-label="Etapas del formulario"><div className="side-card"><span className="side-kicker">Tu comunicación</span><h2 className="side-title">{full?'Formulario completo':'Paso a paso'}</h2><ol className="journey" id="journey-adult">{[['Identificación','Solo los datos necesarios'],['Motivo de contacto','Elige la alternativa'],['Tu comunicación','Detalles y personas relacionadas']].map(([title,caption],i)=><li key={title} className={step===i+1?'active':''}><span className="journey-num">0{i+1}</span><div><a href={`#${['identificacion','motivo-seccion','informacion'][i]}`} onClick={e=>{e.preventDefault();navigation.current?.go(i+1);}}>{title}</a><small>{caption}</small></div></li>)}</ol></div><div className="side-note"><div className="side-note-title"><svg className="icon small"><use href="#i-info" /></svg> Sobre esta versión</div><p>Ambiente de pruebas. Completa solo los datos necesarios.</p></div></aside>
        <main className="form-area" id="principal" tabIndex={-1}>
          {(loading||busy&&!review)&&<p role="status">Estamos abriendo o revisando tu formulario…</p>}
          {fatal&&<div className="paper"><div className="paper-body"><p role="alert">{fatal}</p><button type="button" className="btn btn-primary" onClick={()=>{setLoading(true);setFatal('');setRetry(v=>v+1);}}>Reintentar</button></div></div>}
          {error&&!review&&<p className="field-error" role="alert">{error}</p>}
          {journey&&!authenticated&&!receipt&&<section className="paper"><div className="paper-body"><h2>Ingresa con tu ClaveÚnica</h2><p>Necesitas verificar tu identidad para continuar por este acceso.</p>{session?.loginAvailable?<ClaveUnicaButton />:<p role="status">El inicio con ClaveÚnica está temporalmente cerrado.</p>}</div></section>}
          {journey&&files&&authenticated&&!loading&&!fatal&&!receipt&&<>
            {!available&&<p role="status" className="field-help">La recepción está temporalmente cerrada. Puedes revisar el formulario, pero no enviarlo.</p>}
            <AdultFlow key={journey.id} journey={journey} session={session} full={full} filePort={files} initialAttachments={initialFiles} navigationRef={navigation} onStep={setStep} onReview={async(capture,presenter,people,attached)=>{setError('');setReview({capture,presenter,people,files:attached});}} />
          </>}
          {receipt&&<section className="paper receipt" id="communication-receipt" aria-live="polite"><div className="paper-body"><div className="receipt-mark"><svg className="icon" aria-hidden="true"><use href="#i-check" /></svg></div><h2 className="paper-title">{receipt.estado==='REGISTRADA_SIGER'?'Comunicación registrada':'Recibimos tu comunicación'}</h2><p>{receipt.estado==='REGISTRADA_SIGER'?`Folio: ${receipt.registro!.registro.folio}`:'Tu comunicación se conserva en el Formulario Público. Su registro en SIGER está pendiente.'}</p><p className="receipt-reference">Referencia de recepción: {receipt.id}</p><button type="button" className="btn btn-secondary" onClick={()=>download(receipt.custodia,`comprobante-custodia-${receipt.id}.json`)}>Descargar comprobante de recepción</button>{' '}{receipt.registro&&<button type="button" className="btn btn-secondary" onClick={()=>download(receipt.registro,`comprobante-institucional-${receipt.id}.json`)}>Descargar comprobante institucional</button>}{' '}<button type="button" className="btn btn-secondary" disabled={busy} onClick={()=>{void check();}}>Actualizar estado</button></div></section>}
        </main></div></div>
    {session?.authenticated&&kind==='ADULTO_CU'&&csrf&&<form action="/auth/logout" method="post" className="shell"><input type="hidden" name={csrf.parameterName} value={csrf.token} /><button className="btn btn-secondary" type="submit">Cerrar sesión</button></form>}
    {privacy&&<PrivacyDialog busy={busy} error={error} onClose={()=>window.location.assign('/?vista=inicio')} onAccept={()=>{void accept();}} />}
    {review&&!receipt&&<ReviewAdult review={review} session={session} busy={busy} locked={locked} error={error} onBack={()=>{setReview(null);requestAnimationFrame(()=>document.getElementById('revisar')?.focus());}} onSend={()=>{void send();}} onCheck={()=>{void check();}} />}
    <InstitutionalFooter note="Ambiente de pruebas. Usa solo datos de prueba." />
  </>;
}
