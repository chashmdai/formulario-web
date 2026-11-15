import {useDialogFocus} from '../ui/useDialogFocus';
import { useEffect, useRef } from 'react';
import type { Session } from '../api';
import type { IdentityView } from './api';
import type { AdultCapture } from './adultRules';
import type { Attachment } from './NnaFlow';
import {adultSections} from './reviewModel';
import {SummarySections} from './SummarySections';
export interface AdultReview {capture:AdultCapture;presenter:IdentityView;people:ReadonlyMap<string,IdentityView>;files:Attachment[]}
export function ReviewAdult({review,session,busy,locked,error,onBack,onSend,onCheck}:{review:AdultReview;session:Session|null;busy:boolean;locked:boolean;error:string;onBack:()=>void;onSend:()=>void;onCheck:()=>void}){
  const dialog=useRef<HTMLDialogElement>(null);useDialogFocus(dialog);useEffect(()=>{const current=dialog.current;current?.showModal();return()=>current?.close();},[]);
  useEffect(()=>{if(error)dialog.current?.querySelector<HTMLElement>('[role=alert]')?.scrollIntoView?.({block:'nearest',behavior:'auto'});},[error]);
  const authenticatedIdentity=review.presenter.autenticada&&session?.authenticated&&session.identity?{run:session.identity.run,nombres:session.identity.nombres,apellidos:session.identity.apellidos}:null;
  const sections=adultSections({campos:review.capture.campos,personas:review.capture.personas,instituciones:review.capture.instituciones,presenter:review.presenter,authenticatedIdentity,people:review.people,files:review.files});
  return <dialog className="dialog review-dialog" id="vista-previa" ref={dialog} aria-labelledby="modal-titulo" aria-describedby="modal-descripcion" onCancel={e=>{if(busy||locked)e.preventDefault();else onBack();}}>
    <div className="dialog-head"><div><h2 id="modal-titulo">Revisión de tu comunicación</h2><p id="modal-descripcion">Este es el resumen de lo que nos contarás. Puedes volver al formulario si necesitas cambiar algo.</p></div><button type="button" className="dialog-close" aria-label="Cerrar revisión" disabled={busy||locked} onClick={onBack}><svg className="icon"><use href="#i-x" /></svg></button></div>
    <div className="dialog-body" id="resumen">
      <SummarySections sections={sections}/>
      {error&&<p role="alert" className="field-error">{error}</p>}</div>
    <div className="dialog-foot"><p>Al enviar, tu comunicación se conserva para su gestión por la Defensoría.</p><div className="dialog-buttons"><button type="button" className="btn btn-secondary" disabled={busy||locked} onClick={onBack}>Volver al formulario</button>{locked&&error&&<button type="button" className="btn btn-secondary" disabled={busy} onClick={onCheck}>Comprobar recepción</button>}<button type="button" className="btn btn-primary" disabled={busy} aria-busy={busy||undefined} onClick={onSend}>{busy?'Enviando…':locked?'Reintentar el mismo envío':'Enviar comunicación'}</button></div></div>
  </dialog>;
}
