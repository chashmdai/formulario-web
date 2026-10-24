import { useEffect, useImperativeHandle, useRef, useState } from 'react';
import type { ReactNode, Ref } from 'react';
import { ApiError } from '../api';
import { declarePerson, getPerson, identifyPerson, resetPerson } from './api';
import type { Declarations, IdentityField, IdentityView, PersonRole } from './api';
import { formatRunNumber, pastedRun, runNumber, validRun } from './run';

export interface PersonIdentityHandle {save: () => Promise<IdentityView | null>}
export type RelatedRole = Exclude<PersonRole,'PRESENTADOR'>;
interface Props {journey: string; person: string; role: RelatedRole; identityRef?: Ref<PersonIdentityHandle>; children?: ReactNode; onResolved?: (view: IdentityView | null) => void}
const fields: {key: IdentityField; suffix: string; label: string; hint: string}[] = [
  {key:'primerNombre',suffix:'names',label:'Primer nombre',hint:'(si lo sabes)'},
  {key:'segundoNombre',suffix:'middle',label:'Segundo nombre',hint:'(opcional)'},
  {key:'primerApellido',suffix:'first',label:'Primer apellido',hint:'(si lo sabes)'},
  {key:'segundoApellido',suffix:'second',label:'Segundo apellido',hint:'(opcional)'},
  {key:'edad',suffix:'age',label:'Edad aproximada',hint:'(opcional)'},
];

/** Identificación de terceros del mockup: opcional, por persona/sesión, sin suplantar la identidad del presentador. */
export function PersonIdentity({journey,person,role,identityRef,children,onResolved}:Props) {
  const prefix=role==='AFECTADO_NNA'?'af':role==='ADULTO_RELACIONADO'?'ra':'sb',number=person.split('-').at(-1);
  const id=(suffix:string)=>`${prefix}-${suffix}-${number}`;
  const [view,setView]=useState<IdentityView|null>(null),[values,setValues]=useState<Declarations>({});
  const [numero,setNumero]=useState(''),[dv,setDv]=useState(''),[busy,setBusy]=useState(true),[failure,setFailure]=useState('');
  const [errors,setErrors]=useState<Record<string,string>>({});
  const generation=useRef(0),resetNeeded=useRef(false),resetPending=useRef<Promise<void>|null>(null),callback=useRef(onResolved);
  useEffect(()=>{callback.current=onResolved;},[onResolved]);
  function receive(next:IdentityView) {
    if(next.autenticada||next.personaId!==person)throw new ApiError(502);
    setView(next);setValues(next.valoresDeclarados);setErrors({});setFailure('');setBusy(next.estado==='CONSULTANDO');callback.current?.(next);
  }
  function fail(error:unknown) {setBusy(false);setFailure(error instanceof ApiError&&error.status===429?'Espera unos minutos antes de revisar otro RUN.':'No pudimos revisar o guardar los datos de esta persona. Inténtalo nuevamente.');}
  useEffect(()=>{
    let active=true;const token=++generation.current;
    void getPerson(journey,person).catch(error=>{if(!(error instanceof ApiError)||error.status!==404)throw error;if(!active||token!==generation.current)return null;return identifyPerson(journey,person,role);})
      .then(next=>{if(next&&active&&token===generation.current)receive(next);}).catch(error=>{if(active&&token===generation.current)fail(error);});
    return()=>{active=false;generation.current++;};
    // El rol y la persona son inmutables para cada tarjeta; callbacks se guardan por referencia.
  },[journey,person,role]);
  useEffect(()=>{
    if(view?.estado!=='CONSULTANDO'||failure)return;
    let active=true;const token=generation.current;const timer=window.setTimeout(()=>{
      void getPerson(journey,person).then(next=>{if(active&&token===generation.current)receive(next);}).catch(error=>{if(active&&token===generation.current)fail(error);});
    },2000);
    return()=>{active=false;window.clearTimeout(timer);};
  },[view,failure,journey,person]);
  function invalidate() {if(view)resetNeeded.current=true;generation.current++;setView(null);setValues({});setErrors({});setFailure('');callback.current?.(null);}
  async function reset() {
    if(!resetNeeded.current)return;
    if(!resetPending.current)resetPending.current=resetPerson(journey,person);
    try{await resetPending.current;resetNeeded.current=false;}finally{resetPending.current=null;}
  }
  async function review(withoutRun=false):Promise<IdentityView|null> {
    if(busy)return null;
    const token=++generation.current;setErrors({});setFailure('');
    if(!withoutRun&&(numero||dv)&&!validRun(runNumber(numero),dv)){setErrors({run:'Revisa el número y dígito verificador del RUN.'});return null;}
    setBusy(true);
    try{await reset();const next=await identifyPerson(journey,person,role,withoutRun||!numero?undefined:runNumber(numero),withoutRun||!numero?undefined:dv);
      if(token!==generation.current)return null;receive(next);return next;
    }catch(error){if(token===generation.current)fail(error);return null;}
  }
  async function refresh(){const token=++generation.current;setBusy(true);try{const next=await getPerson(journey,person).catch(error=>{if(!(error instanceof ApiError)||error.status!==404)throw error;return identifyPerson(journey,person,role);});if(token===generation.current)receive(next);}catch(error){if(token===generation.current)fail(error);}}
  async function save():Promise<IdentityView|null> {
    if(busy)return null;
    const current=view?.estado==='REVISADO'?view:await review();if(!current||current.estado!=='REVISADO')return null;
    const input=view?values:current.valoresDeclarados,problems:Record<string,string>={};
    for(const [key,value] of Object.entries(input)) {
      if(!value)continue;
      if(key==='edad'&&(!/^\d{1,2}$/.test(value)||Number(value)>17))problems[key]='Indica una edad entre 0 y 17 años, o deja el campo en blanco.';
      if(key!=='edad'&&fields.some(f=>f.key===key)&&(!/^[\p{L}\p{M} .'-]+$/u.test(value)||!/\p{L}/u.test(value)))problems[key]='Escribe solo letras y separadores de nombres.';
    }
    if(Object.keys(problems).length){setErrors(problems);document.getElementById(id(fields.find(f=>problems[f.key])!.suffix))?.focus();return null;}
    const token=++generation.current;setBusy(true);
    try{const declared=Object.fromEntries(fields.filter(f=>(role==='AFECTADO_NNA'||f.key!=='edad')&&current.camposFaltantes.includes(f.key)&&input[f.key]!==undefined).map(f=>[f.key,input[f.key]])) as Declarations;
      const next=await declarePerson(journey,person,declared);if(token!==generation.current)return null;receive(next);return next;
    }catch(error){if(token===generation.current)fail(error);return null;}
  }
  useImperativeHandle(identityRef,()=>({save}));
  const preserved=!!view?.runInformado&&!numero&&!dv;
  function changed(which:'numero'|'dv',next:string){if(busy)return;invalidate();if(which==='numero')setNumero(formatRunNumber(next));else setDv(next.replace(/[^0-9Kk]/g,'').slice(0,1).toUpperCase());}
  return <>
    <div className="person-lookup">
      {preserved?<p className="field-help">Ya se informó un RUN para esta persona. <button type="button" className="btn btn-secondary" disabled={busy} onClick={invalidate}>Corregir RUN</button></p>:<div className="field">
        <label htmlFor={id('run')}>RUN <span className="muted-label">(opcional)</span></label>
        <div className="run-split"><div className="run-piece"><span className="run-piece-label" aria-hidden="true">Número</span>
          <input type="text" id={id('run')} data-mask="run-body" data-run-body="true" inputMode="numeric" maxLength={10} placeholder="12.345.678" spellCheck={false} value={numero} disabled={busy} onChange={e=>changed('numero',e.target.value)} aria-invalid={!!errors.run}
            onPaste={e=>{if(busy)return;const parsed=pastedRun(e.clipboardData.getData('text'));if(parsed){e.preventDefault();invalidate();setNumero(formatRunNumber(parsed.numero));setDv(parsed.dv);}}} />
        </div><span className="run-hyphen" aria-hidden="true">-</span><div className="run-piece"><label className="run-piece-label" htmlFor={id('run')+'-dv'}>DV</label>
          <input type="text" id={id('run')+'-dv'} data-mask="run-dv" inputMode="text" maxLength={1} placeholder="K" autoComplete="off" autoCapitalize="characters" spellCheck={false} aria-label="Dígito verificador del RUN" value={dv} disabled={busy} onChange={e=>changed('dv',e.target.value)} aria-invalid={!!errors.run} />
        </div></div><p className="field-error" hidden={!errors.run}>{errors.run}</p>
      </div>}
      <div className="lookup-actions"><button type="button" className="btn btn-secondary" disabled={busy||preserved} onClick={()=>{void review();}}>Revisar RUN</button>
        <button type="button" className="btn btn-secondary" disabled={busy} onClick={()=>{invalidate();setNumero('');setDv('');void review(true);}}>Continuar sin RUN</button></div>
      <div className="manual-status" id={id('lookup')} role="status" aria-live="polite" hidden={!busy&&!failure&&!view?.runInformado}>{busy?'Estamos revisando los datos…':failure||view?.mensaje}</div>
      {failure&&<button type="button" className="btn btn-secondary" disabled={busy} onClick={()=>{void(view?.estado==='REVISADO'?save():view?.estado==='CONSULTANDO'||preserved||(!numero&&!dv)?refresh():review());}}>Volver a intentar</button>}
      <p className="field-help">RUN opcional. Completa los antecedentes que conozcas. Este recorrido no consulta los registros de otras personas.</p>
    </div>
    <div className="repeat-fields">
      {fields.filter(f=>(role==='AFECTADO_NNA'||f.key!=='edad')&&view?.camposFaltantes.includes(f.key)).map(f=><div className="field" data-identity-attr={f.suffix} key={f.key}>
        <label htmlFor={id(f.suffix)}>{f.label} <span className="muted-label">{role==='ADULTO_RELACIONADO'?'(si lo sabes)':f.hint}</span></label>
        <input id={id(f.suffix)} data-key={f.suffix} data-name-part={f.key==='edad'?undefined:true} type={f.key==='edad'?'number':'text'} {...(f.key==='edad'?{min:0,max:17,inputMode:'numeric' as const}: {})} maxLength={f.key==='edad'?2:500}
          placeholder={role==='AFECTADO_NNA'?(f.key==='edad'?'Ej.: 12':f.label):role==='PERSONA_SENALADA'&&f.key==='primerNombre'?'Primer nombre':undefined}
          value={values[f.key]||''} disabled={busy} onChange={e=>{const value=e.target.value;setValues(old=>({...old,[f.key]:value}));setErrors(old=>{const next={...old};delete next[f.key];return next;});}} aria-invalid={!!errors[f.key]} aria-describedby={id(f.suffix)+'-error'} />
        <p className="field-error" id={id(f.suffix)+'-error'} hidden={!errors[f.key]}>{errors[f.key]}</p>
      </div>)}
      {children}
    </div>
  </>;
}
