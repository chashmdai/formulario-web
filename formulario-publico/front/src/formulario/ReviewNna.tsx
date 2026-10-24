import {useDialogFocus} from '../ui/useDialogFocus';
import {fileSize} from '../ui/AttachmentStatus';
import { useEffect, useRef } from 'react';
import type { IdentityView } from './api';
import type { Attachment } from './NnaFlow';
import { regiones } from './territorio';

export interface NnaReview {campos: Record<string,string>; identity: IdentityView; attachments: Attachment[]}
function Section({title, rows}: {title: string; rows: [string,string][]}) {
  return <section className="dialog-section"><h3>{title}</h3><dl>{rows.filter(([,v]) => v).map(([k,v]) => <div className="summary-row" key={k}><dt>{k}</dt><dd>{v}</dd></div>)}</dl></section>;
}
const names: Record<string,string> = {primerNombre:'Primer nombre', segundoNombre:'Segundo nombre', primerApellido:'Primer apellido', segundoApellido:'Segundo apellido',edad:'Edad'};
function contact(c: Record<string,string>, prefix: string): string {
  switch (c[`${prefix}-method`]) {
    case 'celular': return `Celular: ${c[`${prefix}-celular`]}`;
    case 'email': return `Correo electrónico: ${c[`${prefix}-email`]}`;
    case 'social': return `${c[`${prefix}-network`] === 'otra' ? c[`${prefix}-other-network`] : c[`${prefix}-network`]}: ${c[`${prefix}-handle`]}`;
    case 'unsafe': return 'No tengo una forma segura de contacto';
    default: return '';
  }
}
export function ReviewNna({review, busy, locked, error, onBack, onSend, onCheck}: {
  review: NnaReview; busy: boolean; locked: boolean; error: string; onBack: () => void; onSend: () => void; onCheck: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);useDialogFocus(dialog);
  useEffect(() => {const current=dialog.current;current?.showModal();return () => current?.close();}, []);
  useEffect(()=>{if(error)dialog.current?.querySelector<HTMLElement>('[role=alert]')?.scrollIntoView?.({block:'nearest',behavior:'auto'});},[error]);
  const c = review.campos;
  const treatment=({nombre:'Usar mi nombre',ella:'Ella',el:'Él',elle:'Elle',noresponde:'Prefiero no responder'} as Record<string,string>)[c['nna-treatment']]||c['nna-treatment'];
  return <dialog className="dialog" id="vista-previa" ref={dialog} aria-labelledby="modal-titulo" aria-describedby="modal-descripcion" onCancel={e => {if (locked || busy) e.preventDefault();else onBack();}}>
    <div className="dialog-head"><div><h2 id="modal-titulo">Revisión de tu comunicación</h2><p id="modal-descripcion">Revisa lo que nos contarás. Si quieres cambiar algo, puedes volver a tus respuestas.</p></div>
      <button type="button" className="dialog-close" aria-label="Cerrar revisión" disabled={busy || locked} onClick={onBack}><svg className="icon"><use href="#i-x" /></svg></button></div>
    <div className="dialog-body" id="resumen">
      <Section title="Sobre ti" rows={[
        ...Object.entries(review.identity.valoresDeclarados).map(([k,v]): [string,string] => [names[k] || k,v || '']),
        ['RUN',review.identity.runInformado ? 'Informado por ti' : 'No informado'],
        ['Nombre preferido',c['nna-preferred']],['Cómo referirnos a ti',c['nna-treatment'] === 'otra' ? c['nna-treatment-text'] : treatment],
        ['Región',regiones[c['nna-region']]],['Comuna',c['nna-commune']],
      ]} />
      <p className="field-help">Los antecedentes recuperados del Registro Civil se mantienen protegidos y no se muestran aquí.</p>
      <Section title="Contacto" rows={[
        ['Forma principal',contact(c,'nna-primary')],['Otra forma de contacto',contact(c,'nna-secondary')],
        ['Seguridad del contacto',({yes:'Sí',unsure:'No estoy seguro o segura',no:'Necesito acordar otra forma'} as Record<string,string>)[c['nna-safe']]],['Precauciones',c['nna-contact-instructions']],
      ]} />
      <Section title="Tu comunicación" rows={[["Relato",c['nna-story']]]} />
      <Section title="Archivos adjuntos" rows={review.attachments.length ? review.attachments.map((f,i) => [`Archivo ${i+1}`,`${f.nombre} · ${fileSize(f.tamanio)}`]) : [['Archivos','Sin archivos adjuntos']]} />
      {error && <p role="alert" className="field-error">{error}</p>}
    </div>
    <div className="dialog-foot"><p>Al enviar, tu comunicación se conserva para su gestión por la Defensoría.</p><div className="dialog-buttons">
      <button type="button" className="btn btn-secondary" disabled={busy || locked} onClick={onBack}>Volver al formulario</button>
      {locked && error && <button type="button" className="btn btn-secondary" disabled={busy} onClick={onCheck}>Comprobar recepción</button>}
      <button type="button" className="btn btn-primary" disabled={busy} onClick={onSend}>{busy ? 'Enviando…' : locked ? 'Reintentar el mismo envío' : 'Enviar comunicación'}</button>
    </div></div>
  </dialog>;
}
