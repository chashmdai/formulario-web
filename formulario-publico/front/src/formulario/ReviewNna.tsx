import {useDialogFocus} from '../ui/useDialogFocus';
import { useEffect, useRef } from 'react';
import type { IdentityView } from './api';
import type { Attachment } from './NnaFlow';
import {nnaSections} from './reviewModel';
import {SummarySections} from './SummarySections';

export interface NnaReview {campos: Record<string,string>; identity: IdentityView; attachments: Attachment[]}
export function ReviewNna({review, busy, locked, error, onBack, onSend, onCheck}: {
  review: NnaReview; busy: boolean; locked: boolean; error: string; onBack: () => void; onSend: () => void; onCheck: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);useDialogFocus(dialog);
  useEffect(() => {const current=dialog.current;current?.showModal();return () => current?.close();}, []);
  useEffect(()=>{if(error)dialog.current?.querySelector<HTMLElement>('[role=alert]')?.scrollIntoView?.({block:'nearest',behavior:'auto'});},[error]);
  const sections=nnaSections({campos:review.campos,identity:review.identity,attachments:review.attachments});
  return <dialog className="dialog review-dialog" id="vista-previa" ref={dialog} aria-labelledby="modal-titulo" aria-describedby="modal-descripcion" onCancel={e => {if (locked || busy) e.preventDefault();else onBack();}}>
    <div className="dialog-head"><div><h2 id="modal-titulo">Revisión de tu comunicación</h2><p id="modal-descripcion">Revisa lo que nos contarás. Si quieres cambiar algo, puedes volver a tus respuestas.</p></div>
      <button type="button" className="dialog-close" aria-label="Cerrar revisión" disabled={busy || locked} onClick={onBack}><svg className="icon"><use href="#i-x" /></svg></button></div>
    <div className="dialog-body" id="resumen">
      <SummarySections sections={sections}/>
      {error && <p role="alert" className="field-error">{error}</p>}
    </div>
    <div className="dialog-foot"><p>Al enviar, tu comunicación se conserva para su gestión por la Defensoría.</p><div className="dialog-buttons">
      <button type="button" className="btn btn-secondary" disabled={busy || locked} onClick={onBack}>Volver al formulario</button>
      {locked && error && <button type="button" className="btn btn-secondary" disabled={busy} onClick={onCheck}>Comprobar recepción</button>}
      <button type="button" className="btn btn-primary" disabled={busy} onClick={onSend}>{busy ? 'Enviando…' : locked ? 'Reintentar el mismo envío' : 'Enviar comunicación'}</button>
    </div></div>
  </dialog>;
}
