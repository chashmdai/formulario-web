import { useEffect, useImperativeHandle, useRef, useState } from 'react';
import type { ChangeEvent, ReactNode, RefObject } from 'react';
import type { Session } from '../api';
import type { IdentityView, Journey } from './api';
import { resetPerson } from './api';
import type { IdentificationHandle } from './IdentificationControls';
import type { PersonIdentityHandle, RelatedRole } from './PersonIdentity';
import type { Attachment, AttachmentPort } from './NnaFlow';
import { AdultPersonCard } from './AdultPersonCard';
import { AdultStepOne } from './AdultStepOne';
import { AdultStepTwo } from './AdultStepTwo';
import { AdultStepThree } from './AdultStepThree';
import { ConsultationGuidance } from './ConsultationGuidance';
import { activeCards, adultCapture, adultMaxLength, adultRequired, validateAdult, validateCards } from './adultRules';
import type { AdultCapture, AdultPersonCard as Card } from './adultRules';
import type { FormErrors } from './nnaRules';
import { comunas } from './territorio';

type Control={value:string;onChange:(e:ChangeEvent<HTMLInputElement|HTMLSelectElement|HTMLTextAreaElement>)=>void;required:boolean;disabled:boolean;'aria-invalid':boolean;'aria-errormessage'?:string};
type Check={checked:boolean;onChange:(e:ChangeEvent<HTMLInputElement>)=>void;disabled:boolean;'aria-invalid'?:boolean;'aria-errormessage'?:string};
export interface AdultNavigation {go:(step:number)=>void}
export interface AdultContext {
  journey:Journey;session:Session|null;identityRef:RefObject<IdentificationHandle|null>;cu:boolean;full:boolean;step:number;
  attachments:Attachment[];busy:boolean;uploading:boolean;errors:FormErrors;ready:boolean;blockIdentity:boolean;guidanceAcknowledged:boolean;
  reasonTitle:string;reasonText:string;fileFeedback:string;
  value:(id:string)=>string;setValue:(id:string,value:string)=>void;bind:(id:string)=>Control;check:(id:string)=>Check;radio:(name:string,value:string)=>Check & {value:string};
  required:(id:string)=>boolean;maxLength:(id:string)=>number;shown:(id:string)=>boolean;action:(id:string)=>void;
  communes:(prefix:string)=>readonly string[];onFiles:(e:ChangeEvent<HTMLInputElement>)=>void;
  people:(role:RelatedRole)=>ReactNode;count:(role:RelatedRole)=>number;
}
interface Props {
  journey:Journey;session:Session|null;full:boolean;filePort:AttachmentPort;initialAttachments?:Attachment[];
  navigationRef?:RefObject<AdultNavigation|null>;onStep?:(step:number)=>void;
  onReview:(capture:AdultCapture,presenter:IdentityView,people:ReadonlyMap<string,IdentityView>,files:Attachment[])=>Promise<void>;
}
const reasonCopy:Record<string,[string,string]>={
  consulta:['Realizar una consulta','Para consultas generales, solicitudes de orientación o información institucional.'],
  vulneracion:['Informar una vulneración de derechos','Permite describir hechos o situaciones que podrían afectar los derechos de un NNA.'],
  promocion:['Actividad de Promoción y Difusión','Permite consultar o solicitar actividades destinadas a comunidades u organizaciones.'],
};
/** Recorrido adulto del mockup. El contenedor revisa/envía la captura; aquí no se fabrican comprobantes. */
export function AdultFlow({journey,session,full,filePort,initialAttachments=[],navigationRef,onStep,onReview}:Props) {
  const [step,setStep]=useState(1),[values,setValues]=useState<Record<string,string>>({}),[errors,setErrors]=useState<FormErrors>({});
  const [cards,setCards]=useState<Card[]>([]),[attachments,setAttachments]=useState<Attachment[]>(initialAttachments);
  const [working,setWorking]=useState(false),[uploading,setUploading]=useState(false),[failure,setFailure]=useState('');
  const [guidance,setGuidance]=useState(false),[guidanceAcknowledged,setGuidanceAcknowledged]=useState(false);
  const identityRef=useRef<IdentificationHandle>(null),people=useRef(new Map<string,PersonIdentityHandle>()),form=useRef<HTMLFormElement>(null),pending=useRef(false);
  const sequence=useRef<Record<RelatedRole,number>>({AFECTADO_NNA:0,ADULTO_RELACIONADO:0,PERSONA_SENALADA:0});
  const mounted=useRef(true);
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;};},[]);
  const value=(id:string)=>values[id]||'';
  const required=adultRequired(values),reason=value('motivo'),topic=value('tema');
  const ready=!!reason&&(reason!=='consulta'||value('consulta-confirmada')==='true'&&['general','entrevista','estadisticas'].includes(topic)&&(topic!=='estadisticas'||value('stats-confirm')==='true'));
  const blockIdentity=reason==='consulta'&&(topic==='empleo'||topic==='estadisticas'&&!ready);
  function focusStep(target:number){requestAnimationFrame(()=>{const element=document.getElementById(['identificacion','motivo-seccion','informacion'][target-1]);element?.scrollIntoView?.({block:'start',behavior:'auto'});element?.querySelector<HTMLElement>('h2')?.focus({preventScroll:true});});}
  function focusField(id:string){
    const element=document.getElementById(id)||form.current?.querySelector<HTMLElement>(`[name="${id}"]`);
    const section=element?.closest<HTMLElement>('section.paper');
    if(section&&!full){const target=[`identificacion`,`motivo-seccion`,`informacion`].indexOf(section.id)+1;if(target){setStep(target);onStep?.(target);}}
    requestAnimationFrame(()=>{element?.scrollIntoView?.({block:'center',behavior:'auto'});element?.focus();});
  }
  function report(next:FormErrors){setErrors(next);if(Object.keys(next).length)requestAnimationFrame(()=>document.getElementById('adult-errors')?.focus());}
  function add(role:RelatedRole){
    if(cards.length>=29||sequence.current[role]>=99){setFailure('Has alcanzado el límite de personas de este recorrido. Continúa con las tarjetas existentes.');return;}
    const number=++sequence.current[role],prefix=role==='AFECTADO_NNA'?'afectado':role==='ADULTO_RELACIONADO'?'relacionado':'senalado';
    setCards(old=>[...old,{id:`${prefix}-${number}`,role,...(role==='PERSONA_SENALADA'?{kind:'persona' as const}:{}),detalles:{}}]);
  }
  function changed(id:string,next:string){
    setValues(old=>({...old,[id]:next,...(id.endsWith('-region')?{[id.replace('-region','-commune')]:''}:{}),...(id==='tema'?{'stats-confirm':'false'}:{})}));
    setErrors(old=>{const copy={...old};delete copy[id];return copy;});
    if(id==='motivo'&&next==='vulneracion'&&!cards.some(c=>c.role==='AFECTADO_NNA'))add('AFECTADO_NNA');
    if(id==='motivo'&&next==='consulta'&&value('consulta-confirmada')!=='true'){setGuidanceAcknowledged(false);setGuidance(true);}
    if(id==='identifica-responsables'&&next==='true'&&!cards.some(c=>c.role==='PERSONA_SENALADA'))add('PERSONA_SENALADA');
  }
  function shown(id:string):boolean {
    if(id.startsWith('tema:'))return topic===id.slice(5);
    if(['consulta','vulneracion','promocion'].includes(id))return reason===id;
    if(['tema-selector','consulta-guidance'].includes(id))return reason==='consulta';
    if(id==='consulta-redirect-panel')return reason==='consulta'&&['empleo','estadisticas'].includes(topic);
    if(id==='resource-empleo')return topic==='empleo';if(id==='resource-estadisticas')return topic==='estadisticas';
    if(id==='entrevista-rol-otro-wrap')return value('entrevista-rol')==='otro';
    if(id==='entrevista-entidad-wrap')return ['prensa','estudiante'].includes(value('entrevista-rol'));
    if(id==='relacion-familiar-wrap')return value('relacion')==='otro-familiar';
    if(id==='urgencia')return value('riesgo')==='si';if(id==='detalle-organizacion')return value('organizacion')==='si';
    if(['vulneracion-anexos','related-adults-section'].includes(id))return reason==='vulneracion';
    if(id==='responsables-contenido')return value('identifica-responsables')==='true';return true;
  }
  async function remove(card:Card){
    if(pending.current||card.role!=='ADULTO_RELACIONADO'&&cards.filter(c=>c.role===card.role).length<=1)return;
    pending.current=true;setWorking(true);setFailure('');
    try{await resetPerson(journey.id,card.id);if(!mounted.current)return;setCards(old=>old.filter(c=>c.id!==card.id));people.current.delete(card.id);}
    catch{setFailure('No pudimos quitar esta persona. Sus datos se conservaron; vuelve a intentarlo.');}
    finally{pending.current=false;setWorking(false);}
  }
  async function navigate(target:number){
    if(pending.current||uploading)return;if(blockIdentity&&target===1)target=2;
    pending.current=true;setWorking(true);setFailure('');
    try {
      if(target>1&&!blockIdentity&&!await identityRef.current?.save())return;
      if(!mounted.current)return;
      if(target>2){const next=validateAdult(values,false);report(next);if(Object.keys(next).length){setStep(2);onStep?.(2);return;}}
      setStep(target);onStep?.(target);focusStep(target);
    }catch{setFailure('No pudimos revisar tus datos. Inténtalo nuevamente.');}
    finally{pending.current=false;setWorking(false);}
  }
  async function review(){
    if(pending.current||uploading)return;pending.current=true;setWorking(true);setFailure('');
    const next={...validateAdult(values),...(errors.adjuntos?{adjuntos:errors.adjuntos}:{})};
    try {
      const presenter=await identityRef.current?.save();if(!mounted.current)return;if(!presenter){setStep(1);onStep?.(1);focusStep(1);return;}
      const publicPeople=new Map<string,IdentityView>();
      for(const card of activeCards(values,cards)){
        if(card.role==='PERSONA_SENALADA'&&card.kind==='institucion')continue;
        const resolved=await people.current.get(card.id)?.save();if(!mounted.current)return;if(resolved)publicPeople.set(card.id,resolved);
      }
      Object.assign(next,validateCards(values,cards,publicPeople));report(next);if(Object.keys(next).length)return;
      await onReview(adultCapture(values,cards,attachments.map(f=>f.id)),presenter,publicPeople,attachments);
    }catch{setFailure('No pudimos revisar tu comunicación. Inténtalo nuevamente.');}
    finally{pending.current=false;setWorking(false);}
  }
  async function clearFiles(){if(pending.current||uploading)return;setUploading(true);try{await filePort.clear();setAttachments([]);setErrors(old=>{const copy={...old};delete copy.adjuntos;return copy;});}catch{setFailure('No pudimos quitar los archivos. Inténtalo nuevamente.');}finally{setUploading(false);}}
  function onFiles(e:ChangeEvent<HTMLInputElement>){
    const input=e.target,batch=Array.from(input.files||[]);let message='';
    if(batch.length>5)message='Puedes adjuntar hasta 5 archivos.';
    else if(batch.some(f=>!f.size||f.size>10*1024*1024))message='Cada archivo debe tener contenido y admitir hasta 10 MB.';
    else if(batch.reduce((sum,f)=>sum+f.size,0)>25*1024*1024)message='Los archivos admiten hasta 25 MB en total.';
    else if(batch.some(f=>!/\.(pdf|png|jpe?g|webp|txt|docx?)$/i.test(f.name)))message='Selecciona PDF, imágenes, Word o TXT.';
    if(message){setErrors(old=>({...old,adjuntos:message}));input.value='';return;}
    setUploading(true);setFailure('');void filePort.replace(batch).then(files=>{if(!mounted.current)return;setAttachments(files);setErrors(old=>{const copy={...old};delete copy.adjuntos;return copy;});})
      .catch(()=>{if(mounted.current)setErrors(old=>({...old,adjuntos:'No pudimos recibir estos archivos. Revisa su formato o vuelve a intentarlo.'}));}).finally(()=>{input.value='';if(mounted.current)setUploading(false);});
  }
  function action(id:string){
    if(id==='abrir-orientacion'){setGuidanceAcknowledged(value('consulta-confirmada')==='true');setGuidance(true);}
    else if(id==='orientacion-close')setGuidance(false);
    else if(id==='confirmar-orientacion'&&guidanceAcknowledged){changed('consulta-confirmada','true');setGuidance(false);}
    else if(id==='agregar-afectado')add('AFECTADO_NNA');else if(id==='agregar-responsable')add('PERSONA_SENALADA');else if(id==='add-related-adult')add('ADULTO_RELACIONADO');
  }
  useImperativeHandle(navigationRef,()=>({go:target=>{void navigate(target);}}));
  const ctx:AdultContext={journey,session,identityRef,attachments,cu:journey.tipo==='ADULTO_CU',full,step,busy:working,uploading,errors,ready,blockIdentity,guidanceAcknowledged,
    value,setValue:changed,shown,action,maxLength:adultMaxLength,required:id=>required.includes(id),communes:prefix=>comunas[value(`${prefix}-region`)]||[],onFiles,
    reasonTitle:reasonCopy[reason]?.[0]||'',reasonText:reasonCopy[reason]?.[1]||'',
    fileFeedback:uploading?'Estamos recibiendo tus archivos…':errors.adjuntos||(attachments.length?attachments.map(f=>f.nombre).join(' · '):'Ningún archivo seleccionado'),
    count:role=>cards.filter(c=>c.role===role).length,
    people:role=>{const group=cards.filter(c=>c.role===role);return group.map((card,i)=><AdultPersonCard key={card.id} card={card} journey={journey.id} position={i+1} canRemove={role==='ADULTO_RELACIONADO'||group.length>1} disabled={working}
      identityRef={handle=>{if(handle)people.current.set(card.id,handle);else people.current.delete(card.id);}} error={errors[card.id]}
      onRemove={()=>{void remove(card);}} onKind={kind=>setCards(old=>old.map(c=>c.id===card.id?{...c,kind}:c))} onChange={detalles=>setCards(old=>old.map(c=>c.id===card.id?{...c,detalles}:c))} />);},
    bind:id=>({value:value(id),onChange:e=>changed(id,e.target.value),required:required.includes(id),disabled:working||uploading||id.endsWith('-commune')&&!value(id.replace('-commune','-region')),'aria-invalid':!!errors[id],'aria-errormessage':errors[id]?id+'-summary':undefined}),
    check:id=>({checked:id==='consulta-confirmada'?guidanceAcknowledged:value(id)==='true',onChange:e=>id==='consulta-confirmada'?setGuidanceAcknowledged(e.target.checked):changed(id,String(e.target.checked)),disabled:working||uploading,'aria-invalid':!!errors[id],'aria-errormessage':errors[id]?id+'-summary':undefined}),
    radio:(name,v)=>({value:v,checked:value(name)===v,onChange:()=>changed(name,v),disabled:working||uploading,'aria-invalid':!!errors[name],'aria-errormessage':errors[name]?name+'-summary':undefined}),
  };
  return <>
    {(working||uploading)&&<p role="status">Estamos revisando o guardando tus datos…</p>}
    <form id="formulario" data-audience="adult" autoComplete="off" noValidate ref={form} onSubmit={e=>e.preventDefault()} aria-busy={working||uploading} inert={working||uploading}>
      <div className="error-summary" id="adult-errors" tabIndex={-1} role="alert" hidden={!Object.keys(errors).length}><p>Revisa estos campos para continuar:</p><ul>{Object.entries(errors).map(([id,message])=><li key={id} id={id+'-summary'}><a href={`#${id}`} onClick={e=>{e.preventDefault();focusField(id);}}>{message}</a></li>)}</ul></div>
      {failure&&<p className="field-error" role="alert">{failure}</p>}
      <AdultStepOne ctx={ctx} /><AdultStepTwo ctx={ctx} /><AdultStepThree ctx={ctx} />
      {step===3||full?<button type="button" className="btn btn-secondary remove-files" hidden={!attachments.length&&!errors.adjuntos} disabled={working||uploading} onClick={()=>{void clearFiles();}}>Quitar archivos seleccionados</button>:null}
      <div className="adult-step-nav" id="adult-step-nav" hidden={full} aria-label="Navegación entre pasos"><button className="btn btn-secondary" id="adult-prev" type="button" hidden={step===1} onClick={()=>{void navigate(Math.max(1,step-1));}}>Volver</button><p aria-live="polite" id="adult-step-message">Los campos con * son obligatorios.</p><button className="btn btn-primary" id="adult-next" type="button" hidden={step===3} onClick={()=>{void navigate(step+1);}}>Continuar <svg className="icon small" aria-hidden="true"><use href="#i-arrow" /></svg></button></div>
      <div className="review-bar" hidden={!ready||!full&&step!==3}><div className="review-message"><span className="review-circle"><svg className="icon" aria-hidden="true"><use href="#i-check" /></svg></span><div><strong>Tu comunicación, antes de enviarla</strong><p>Revisa tus respuestas antes de enviar tu comunicación.</p></div></div><button className="btn btn-primary" id="revisar" type="button" onClick={()=>{void review();}}>Revisar respuestas</button></div>
    </form>
    {guidance&&<ConsultationGuidance ctx={ctx} />}
  </>;
}
