import { useEffect, useMemo, useRef, useState } from 'react';
import '@fontsource/cabin/400.css';
import '@fontsource/cabin/500.css';
import '@fontsource/cabin/600.css';
import '@fontsource/cabin/700.css';
import { ApiError, getSession } from '../api';
import type { Session } from '../api';
import { InstitutionalHeader } from '../prototipo/InstitutionalHeader';
import { InstitutionalFooter } from '../prototipo/InstitutionalFooter';
import type { Journey } from './api';
import { NnaFlow } from './NnaFlow';
import type { Attachment, NnaNavigation } from './NnaFlow';
import { ReviewNna } from './ReviewNna';
import type { NnaReview } from './ReviewNna';
import { attachmentPort, enterNna, getJourneySubmission, getReceptionCapabilities, getSubmission, listAttachments, submitNna } from './submission';
import type { CompleteCapture, SubmissionState } from './submission';
import { usePreferences } from './preferences';
import '../prototipo/prototipo.css';
import '../prototipo/inicio.css';

function download(data: unknown, filename: string) {
  const url=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:'application/json'}));
  const a=document.createElement('a');a.href=url;a.download=filename;document.body.append(a);a.click();a.remove();URL.revokeObjectURL(url);
}
export default function NnaPage() {
  const [journey,setJourney]=useState<Journey|null>(null),[session,setSession]=useState<Session|null>(null);
  const [full,setFull]=useState(false),[step,setStep]=useState(1),[retry,setRetry]=useState(0);
  const [loading,setLoading]=useState(true),[fatal,setFatal]=useState(''),[available,setAvailable]=useState(false);
  const [review,setReview]=useState<NnaReview|null>(null),[receipt,setReceipt]=useState<SubmissionState|null>(null);
  const [busy,setBusy]=useState(false),[error,setError]=useState(''),[locked,setLocked]=useState(false);
  const [initialFiles,setInitialFiles]=useState<Attachment[]>([]);
  const attempt=useRef<{key:string;body:CompleteCapture}|null>(null),navigation=useRef<NnaNavigation>(null),sending=useRef(false);
  const preferences=usePreferences(full);
  const files=useMemo(()=>journey?attachmentPort(journey.id):null,[journey]);
  useEffect(()=>{let active=true;
    document.title='Formulario para niños, niñas y adolescentes | Defensoría de la Niñez';
    void Promise.all([enterNna(),getSession(),getReceptionCapabilities()]).then(async([j,s,c])=>{
      const restored=j.estado==='RECIBIDO'?await getJourneySubmission(j.id):null;
      const restoredFiles=restored?[]:await listAttachments(j.id);
      if(active){setJourney(j);setSession(s);setAvailable(c.nna);setReceipt(restored);setInitialFiles(restoredFiles);setFatal('');}
    }).catch(()=>{if(active)setFatal('No pudimos abrir tu formulario. Inténtalo nuevamente.');}).finally(()=>{if(active)setLoading(false);});
    return()=>{active=false;};
  },[retry]);
  async function send() {
    if(!journey||!review||sending.current)return;
    if(!available){setError('La recepción está temporalmente cerrada. Vuelve a intentarlo más tarde.');return;}
    if(!attempt.current)attempt.current={key:crypto.randomUUID(),body:{campos:review.campos,personas:[],instituciones:[],adjuntos:review.attachments.map(f=>f.id)}};
    sending.current=true;setBusy(true);setLocked(true);setError('');
    try {setReceipt(await submitNna(journey.id,attempt.current.key,attempt.current.body));setReview(null);}
    catch(e){setError(e instanceof ApiError&&e.status===401?'Tu sesión terminó. No podemos confirmar la recepción desde esta sesión.':'No pudimos confirmar la recepción. Comprueba el estado o reintenta el mismo envío para evitar duplicados.');}
    finally{sending.current=false;setBusy(false);}
  }
  async function check() {
    const id=receipt?.id||attempt.current?.key;if(!id||sending.current)return;sending.current=true;setBusy(true);setError('');
    try{setReceipt(await getSubmission(id));setReview(null);}catch{setError('No pudimos recuperar una recepción confirmada. Puedes reintentar el mismo envío.');}
    finally{sending.current=false;setBusy(false);}
  }
  return <>
    <a className="skip" href="#principal">Saltar al formulario</a>
    <InstitutionalHeader {...preferences} title="Para niños, niñas y adolescentes" onHome={()=>window.location.assign('/?vista=inicio')} />
    <div className="shell" id="form-experience">
      <section className="hero" aria-labelledby="titulo"><div className="hero-text"><p className="eyebrow"><span className="accent-line" aria-hidden="true" /> Comunícate con la Defensoría</p>
        <h1 id="titulo">Queremos escucharte</h1><p id="hero-description">Puedes contarnos lo que te pasa o pedirnos orientación. No necesitas conocer tu RUN ni tener ClaveÚnica.</p></div></section>
      <div className="form-toolbar" aria-label="Presentación del formulario"><div className="toolbar-title"><span className="toolbar-overline">TU FORMULARIO</span><strong id="view-current">Niñas, niños y adolescentes</strong><small>Ambiente de pruebas · Usa solo datos de prueba</small></div>
        <div className="toolbar-controls"><span className="step-info" id="step-info" aria-live="polite">{full?'Formulario completo':`Paso ${step} de 2`}</span><div className="view-toggle" role="group" aria-label="Forma de visualizar el formulario">
          <button type="button" aria-pressed={!full} onClick={()=>setFull(false)}>Por pasos</button><button type="button" aria-pressed={full} onClick={()=>setFull(true)}>Completo</button></div></div></div>
      <div className="page-layout" id="public-surface">
        <aside className="side-column" aria-label="Etapas del formulario"><div className="side-card"><span className="side-kicker">Tu comunicación</span><h2 className="side-title">{full?'Formulario completo':'Paso a paso'}</h2>
          <ol className="journey" id="journey-nna">{[['Sobre ti y tu contacto','Cuéntanos cómo llamarte'],['Tu comunicación','Relato y archivos opcionales']].map(([title,caption],i)=><li className={step===i+1?'active':''} key={title}><span className="journey-num">0{i+1}</span><div><button type="button" disabled={!journey||!!receipt} onClick={()=>navigation.current?.go(i+1)}>{title}</button><small>{caption}</small></div></li>)}</ol>
        </div><div className="side-note"><div className="side-note-title"><svg className="icon small"><use href="#i-info" /></svg> Sobre esta versión</div><p>Ambiente de pruebas. Completa solo los datos necesarios.</p></div></aside>
        <main className="form-area" id="principal" tabIndex={-1}>
          {loading&&<p role="status">Estamos abriendo tu formulario…</p>}
          {fatal&&<div className="paper"><div className="paper-body"><p role="alert">{fatal}</p><button type="button" className="btn btn-primary" onClick={()=>{setLoading(true);setFatal('');setRetry(v=>v+1);}}>Reintentar</button></div></div>}
          {journey&&files&&!fatal&&!loading&&!receipt&&<div>
            {!available&&<p role="status" className="field-help">La recepción está temporalmente cerrada. Puedes revisar el formulario, pero no enviarlo.</p>}
            <NnaFlow journey={journey} session={session} full={full} filePort={files} initialAttachments={initialFiles} navigationRef={navigation} onStep={setStep} onReview={async(campos,identity,attachments)=>{setError('');setReview({campos,identity,attachments});}} />
          </div>}
          {receipt&&<section className="paper receipt" id="communication-receipt" aria-live="polite"><div className="paper-body"><div className="receipt-mark"><svg className="icon" aria-hidden="true"><use href="#i-check" /></svg></div>
            <h2 className="paper-title">{receipt.estado==='REGISTRADA_SIGER'?'Comunicación registrada':'Recibimos tu comunicación'}</h2>
            <p>{receipt.estado==='REGISTRADA_SIGER'?`Folio: ${receipt.registro!.registro.folio}`:'Tu comunicación se conserva en el Formulario Público. Su registro en SIGER está pendiente.'}</p>
            <p className="receipt-reference">Referencia de recepción: {receipt.id}</p>
            <button type="button" className="btn btn-secondary" onClick={()=>download(receipt.custodia,`comprobante-custodia-${receipt.id}.json`)}>Descargar comprobante de recepción</button>{' '}
            {receipt.registro&&<button type="button" className="btn btn-secondary" onClick={()=>download(receipt.registro,`comprobante-institucional-${receipt.id}.json`)}>Descargar comprobante institucional</button>}{' '}
            <button type="button" className="btn btn-secondary" disabled={busy} onClick={()=>{void check();}}>Actualizar estado</button>
            {error&&<p className="field-error" role="alert">{error}</p>}
          </div></section>}
        </main>
      </div>
    </div>
    {review&&!receipt&&<ReviewNna review={review} busy={busy} locked={locked} error={error} onBack={()=>{setReview(null);requestAnimationFrame(()=>document.getElementById('nna-review')?.focus());}} onSend={()=>{void send();}} onCheck={()=>{void check();}} />}
    <InstitutionalFooter note="Ambiente de pruebas. Usa solo datos de prueba." />
  </>;
}
