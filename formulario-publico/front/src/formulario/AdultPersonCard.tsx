import {ReadableSelect} from '../ui/ReadableSelect';
import type { Ref } from 'react';
import { PersonIdentity } from './PersonIdentity';
import type { PersonIdentityHandle } from './PersonIdentity';
import type { AdultPersonCard as Card } from './adultRules';
import { comunas, regiones } from './territorio';

const order=['15','01','02','03','04','05','13','06','07','16','08','09','14','10','11','12'];
const institutions=['Establecimiento educativo','Organismo público','Empresa o entidad privada','Fundación u ONG','Otra institución'];
/** Tarjetas controladas del mockup; el contenedor conserva metadatos y confirma cualquier retiro en backend. */
export function AdultPersonCard({card,journey,identityRef,error,disabled=false,position,canRemove=true,onChange,onKind,onRemove}: {
  card:Card;journey:string;identityRef?:Ref<PersonIdentityHandle>;error?:string;disabled?:boolean;position?:number;canRemove?:boolean;
  onChange:(details:Record<string,string>)=>void;onKind:(kind:'persona'|'institucion')=>void;onRemove:()=>void;
}) {
  const affected=card.role==='AFECTADO_NNA',related=card.role==='ADULTO_RELACIONADO',prefix=affected?'af':related?'ra':'sb',n=card.id.split('-').at(-1);
  const value=(key:string)=>card.detalles[key]||'';
  const update=(key:string,next:string)=>onChange({...card.detalles,[key]:next,...(key==='region'?{comuna:''}:{})});
  const attrs=(key:string)=>({id:`${prefix}-${key}-${n}`,value:value(key),disabled,'aria-invalid':!!error,'aria-describedby':error?card.id+'-error':undefined});
  const textField=(key:string,label:string,placeholder?:string)=><div className="field"><label htmlFor={`${prefix}-${key}-${n}`}>{label} <span className="muted-label">(opcional)</span></label><input {...attrs(key)} type="text" maxLength={500} placeholder={placeholder} onChange={e=>update(key,e.target.value)} /></div>;
  const location=<>
    <div className="field"><label htmlFor={`${prefix}-region-${n}`}>Región <span className="muted-label">{affected?'(si la sabes)':'(opcional)'}</span></label><div className="select-wrap"><ReadableSelect {...attrs('region')} onChange={e=>update('region',e.target.value)}><option value="">Selecciona una región</option>{order.map(code=><option key={code} value={code}>{regiones[code]}</option>)}</ReadableSelect></div></div>
    <div className="field"><label htmlFor={`${prefix}-commune-${n}`}>Comuna <span className="muted-label">(opcional)</span></label><div className="select-wrap"><ReadableSelect {...attrs('comuna')} id={`${prefix}-commune-${n}`} disabled={disabled||!value('region')} onChange={e=>update('comuna',e.target.value)}><option value="">{value('region')?'Selecciona una comuna':'Selecciona primero una región'}</option>{(comunas[value('region')]||[]).map(name=><option key={name} value={name}>{name}</option>)}</ReadableSelect></div></div>
  </>;
  return <section className="repeat-card" id={card.id} tabIndex={-1} aria-label={affected?`Antecedentes del NNA ${position??n}`:related?`Persona adulta relacionada ${position??n}`:`Persona o institución ${position??n}`}>
    <div className="repeat-card-head"><strong className="repeat-heading">{affected?`NNA ${position??n}`:related?`Persona adulta ${position??n}`:`Registro ${position??n}`}</strong><button className="btn btn-secondary" type="button" disabled={disabled} hidden={!canRemove} onClick={onRemove}>Quitar</button></div>
    {related&&<div className="field"><label htmlFor={`ra-relationship-${n}`}>Relación con la comunicación</label><div className="select-wrap"><ReadableSelect {...attrs('relacion')} id={`ra-relationship-${n}`} onChange={e=>update('relacion',e.target.value)}><option value="">Selecciona una opción</option>{['Familiar','Representante','Persona de contacto','Testigo','Otro adulto relacionado'].map(option=><option key={option}>{option}</option>)}</ReadableSelect></div></div>}
    {!affected&&!related&&<div className="field"><label htmlFor={`sb-kind-${n}`}>¿A quién puedes identificar? <span className="required-mark" aria-hidden="true">*</span></label><div className="select-wrap"><ReadableSelect id={`sb-kind-${n}`} required disabled={disabled} value={card.kind||'persona'} onChange={e=>onKind(e.target.value as 'persona'|'institucion')}><option value="persona">Persona</option><option value="institucion">Institución u organización</option></ReadableSelect></div></div>}
    {card.role==='PERSONA_SENALADA'&&card.kind==='institucion'?<div className="repeat-fields">
      <div className="field"><label htmlFor={`sb-entity-${n}`}>Nombre de la institución <span className="required-mark" aria-hidden="true">*</span></label><input {...attrs('nombre')} id={`sb-entity-${n}`} type="text" maxLength={500} required placeholder="Nombre de la institución" onChange={e=>update('nombre',e.target.value)} /></div>
      <div className="field"><label htmlFor={`sb-etype-${n}`}>Tipo de institución <span className="muted-label">(opcional)</span></label><div className="select-wrap"><ReadableSelect {...attrs('tipo')} id={`sb-etype-${n}`} onChange={e=>update('tipo',e.target.value)}><option value="">Selecciona una opción</option>{institutions.map(option=><option key={option}>{option}</option>)}</ReadableSelect></div></div>
    </div>:<PersonIdentity journey={journey} person={card.id} role={card.role} identityRef={identityRef}>
      {affected?location:!related?textField('alias','Alias o nombre conocido','Si solo conoces un apodo'):null}
    </PersonIdentity>}
    {affected&&<label className="ack-checkbox"><input type="checkbox" id={`af-unknown-${n}`} checked={value('datosDesconocidos')==='true'} disabled={disabled} onChange={e=>update('datosDesconocidos',String(e.target.checked))} /><span>No conozco otros datos para identificar a este NNA. Puedo describir la situación en el relato.</span></label>}
    {!affected&&!related&&<>{textField('rol','Vínculo con la situación relatada','Ej.: contexto, función o relación con los hechos')}<div className="repeat-fields">{location}</div></>}
    {error&&<p className="field-error" role="alert" id={card.id+'-error'}>{error}</p>}
  </section>;
}
