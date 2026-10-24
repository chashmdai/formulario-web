import type {MediaControl} from '../ui/MediaAttachments';
import { useEffect, useImperativeHandle, useRef, useState } from 'react';
import type { ChangeEvent, RefObject } from 'react';
import type { Session } from '../api';
import type { IdentityView, Journey } from './api';
import type { IdentificationHandle } from './IdentificationControls';
import { NnaStepOne } from './NnaStepOne';
import { NnaStepTwo } from './NnaStepTwo';
import { nnaRequired, nnaValues, validateNna } from './nnaRules';
import type { FormErrors } from './nnaRules';

export interface Attachment {id: string; nombre: string; tamanio: number; sha256: string; estado: string;tipo?:string}
export interface AttachmentPort {replace: (files: File[]) => Promise<Attachment[]>; clear: () => Promise<void>}
export interface NnaNavigation {go: (step: number) => void}
type TextControl = {value: string; onChange: (e: ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => void; required: boolean; disabled: boolean; 'aria-invalid': boolean; 'aria-errormessage'?: string};
export interface NnaContext {
  media?:MediaControl;reviewBlocked?:boolean;
  journey: Journey; session: Session | null; identityRef: RefObject<IdentificationHandle | null>;
  full: boolean; step: number; attachments: Attachment[]; errors: FormErrors; uploading: boolean; fileFeedback: string;
  bind: (id: string) => TextControl;
  check: (id: string) => {checked: boolean; onChange: (e: ChangeEvent<HTMLInputElement>) => void; disabled: boolean};
  setValue: (id:string,value:string)=>void;value: (id: string) => string; shown: (id: string) => boolean; action: (id: string) => void;
  onFiles: (e: ChangeEvent<HTMLInputElement>) => void;
}
interface Props {
  journey: Journey; session: Session | null; full: boolean; filePort: AttachmentPort;
  onStep?: (step: number) => void;
  navigationRef?: RefObject<NnaNavigation | null>;
  initialAttachments?: Attachment[];mediaEnabled?:boolean;
  onReview: (values: Record<string, string>, identity: IdentityView, attachments: Attachment[]) => Promise<void>;
}
/** Dos pasos del mockup. La recepción y la revisión son operaciones del contenedor, nunca simulaciones locales. */
export function NnaFlow({journey, session, full, filePort, onStep, onReview, navigationRef, mediaEnabled=false, initialAttachments = []}: Props) {
  const [step, setStep] = useState(1);
  const [values, setValues] = useState<Record<string, string>>({});
  const [errors, setErrors] = useState<FormErrors>({});
  const [attachments, setAttachments] = useState<Attachment[]>(initialAttachments);
  const [uploading, setUploading] = useState(false);
  const [mediaBusy,setMediaBusy]=useState(false);
  const [working, setWorking] = useState(false);
  const [failure, setFailure] = useState('');
  const identityRef = useRef<IdentificationHandle>(null);
  const form = useRef<HTMLFormElement>(null);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const value = (id: string) => values[id] || '';
  const required = nnaRequired(values);
  function changed(id: string, next: string) {
    setValues(old => ({...old, [id]: next, ...(id === 'nna-region' ? {'nna-commune': ''} : {})}));
    setErrors(old => {const copy = {...old};delete copy[id];return copy;});
  }
  function shown(id: string) {
    if (id.includes(':')) {const [prefix, channel] = id.split(':');return (prefix !== 'nna-secondary' || value('nna-extra-contact') === 'true') && value(`${prefix}-method`) === channel;}
    if (id.endsWith('-other-network-wrap')) return value(id.replace('-other-network-wrap','-network')) === 'otra';
    if (id === 'nna-secondary-panel') return value('nna-extra-contact') === 'true';
    if (id === 'nna-unsafe-note') return value('nna-primary-method') === 'unsafe';
    if (id === 'nna-contact-safety') return !!value('nna-primary-method') && value('nna-primary-method') !== 'unsafe';
    if (id === 'nna-files-clear') return attachments.length > 0 || !!errors['nna-files'];
    return true;
  }
  function disabled(id: string) {
    if (working || uploading) return true;
    if (id === 'nna-commune') return !value('nna-region');
    for (const prefix of ['nna-primary','nna-secondary']) if (id.startsWith(`${prefix}-`) && !id.endsWith('-method')) {
      const channel = value(`${prefix}-method`);
      return (prefix === 'nna-secondary' && value('nna-extra-contact') !== 'true') || !channel || channel === 'unsafe'
        || (id.endsWith('-celular') && channel !== 'celular') || (id.endsWith('-email') && channel !== 'email')
        || ((!id.endsWith('-celular') && !id.endsWith('-email')) && channel !== 'social')
        || (id.endsWith('-other-network') && value(`${prefix}-network`) !== 'otra');
    }
    return false;
  }
  function focusErrors(next: FormErrors) {
    setErrors(next);
    if (Object.keys(next).length) requestAnimationFrame(() => form.current?.querySelector<HTMLElement>('#nna-errors')?.focus());
  }
  function focusStep(target: number) {
    requestAnimationFrame(()=>{
      document.getElementById(target===1?'nna-step-one':'nna-step-two')?.scrollIntoView?.({block:'start',behavior:'auto'});
      document.getElementById(target===1?'nna-ident-title':'nna-story-title')?.focus({preventScroll:true});
    });
  }
  async function action(id: string) {
    if (working || uploading) return;
    setFailure('');
    if (id === 'nna-back') {setStep(1);onStep?.(1);focusStep(1);return;}
    if (id === 'nna-files-clear') {
      setUploading(true);try {await filePort.clear();if (mounted.current) {setAttachments([]);setErrors(old => {const next = {...old};delete next['nna-files'];return next;});}} catch {if (mounted.current) setFailure('No pudimos quitar los archivos. Inténtalo nuevamente.');}
      finally {if (mounted.current) setUploading(false);}return;
    }
    if(id==='nna-review'&&mediaBusy)return;
    if (id !== 'nna-next' && id !== 'nna-review') return;
    const next = {...validateNna(values, id === 'nna-review'), ...(errors['nna-files'] ? {'nna-files': errors['nna-files']} : {})};focusErrors(next);
    setWorking(true);
    try {
      const identity = await identityRef.current?.save();
      if (!mounted.current) return;
      if (!identity) {setStep(1);onStep?.(1);focusStep(1);return;}
      if (Object.keys(next).length) return;
      if (id === 'nna-next') {setStep(2);onStep?.(2);focusStep(2);}
      else await onReview(nnaValues(values), identity, attachments);
    } catch {if (mounted.current) setFailure('No pudimos revisar tu comunicación. Inténtalo nuevamente.');}
    finally {if (mounted.current) setWorking(false);}
  }
  function onFiles(event: ChangeEvent<HTMLInputElement>) {
    const selected = Array.from(event.target.files || []), element = event.target;
    const allowed = /\.(pdf|png|jpe?g|webp|txt|docx?)$/i;
    let error = '';
    if (selected.length > 5) error = 'Puedes adjuntar hasta 5 archivos.';
    else if (selected.some(file => file.size > 10 * 1024 * 1024)) error = 'Cada archivo admite hasta 10 MB.';
    else if (selected.reduce((sum, file) => sum + file.size, 0) > 25 * 1024 * 1024) error = 'Los archivos admiten hasta 25 MB en total.';
    else if (selected.some(file => !allowed.test(file.name))) error = 'Selecciona PDF, imágenes, Word o TXT.';
    if (error) {setErrors(old => ({...old, 'nna-files': error}));element.value = '';return;}
    setUploading(true);setFailure('');
    void filePort.replace(selected).then(files => {
      if (!mounted.current) return;
      setAttachments(files);setErrors(old => {const copy = {...old};delete copy['nna-files'];return copy;});
    }).catch(() => {if (mounted.current) setErrors(old => ({...old, 'nna-files': 'No pudimos recibir estos archivos. Revisa su formato o vuelve a intentarlo.'}));})
      .finally(() => {element.value = '';if (mounted.current) setUploading(false);});
  }
  useImperativeHandle(navigationRef, () => ({go: target => {if (full) document.getElementById(target === 1 ? 'nna-ident-title' : 'nna-story-title')?.focus();else void action(target === 1 ? 'nna-back' : 'nna-next');}}));
  const ctx: NnaContext = {media:mediaEnabled?{journey:journey.id,files:attachments,disabled:working,onChange:setAttachments,onBusy:setMediaBusy}:undefined,reviewBlocked:mediaBusy,journey, session, identityRef, full, step, attachments, errors, uploading,
    fileFeedback: uploading ? 'Estamos recibiendo tus archivos…' : errors['nna-files'] || (attachments.length ? attachments.map(file => file.nombre).join(' · ') : 'Ningún archivo seleccionado'),
    value, setValue:changed, shown, action: id => {void action(id);}, onFiles,
    bind: id => ({value: value(id), onChange: e => changed(id, e.target.value), required: required.includes(id) || id === 'nna-story', disabled: disabled(id), 'aria-invalid': !!errors[id], 'aria-errormessage': errors[id]?id+'-summary':undefined}),
    check: id => ({checked: value(id) === 'true', onChange: e => changed(id, String(e.target.checked)), disabled: working || uploading}),
  };
  return <form autoComplete="off" data-audience="nna" id="formulario-nna" noValidate ref={form} onSubmit={e => e.preventDefault()} aria-busy={working || uploading}>
    <div className="nna-progress" hidden={full}><span id="nna-step-label">Paso {step} de 2</span><div className="nna-track" role="progressbar" aria-label="Avance del formulario" aria-valuemin={1} aria-valuemax={2} aria-valuenow={step}><span id="nna-progress-fill" style={{width: step === 1 ? '50%' : '100%'}}></span></div></div>
    <div className="error-summary" id="nna-errors" role="alert" tabIndex={-1} hidden={!Object.keys(errors).length}><p>Revisa estos campos para continuar:</p><ul>{Object.entries(errors).map(([id, message]) => <li key={id} id={id+'-summary'}><a href={`#${id}`} onClick={e => {e.preventDefault();const element=document.getElementById(id);const target=id==='nna-story'||id==='nna-files'?2:1;if(!full){setStep(target);onStep?.(target);}requestAnimationFrame(()=>{element?.scrollIntoView?.({block:'center',behavior:'auto'});element?.focus();});}}>{message}</a></li>)}</ul></div>
    {failure && <p role="alert" className="field-error">{failure}</p>}
    <NnaStepOne ctx={ctx} /><NnaStepTwo ctx={ctx} />
  </form>;
}
