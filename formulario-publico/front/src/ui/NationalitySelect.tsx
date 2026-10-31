import {useState} from 'react';
import type {InputHTMLAttributes} from 'react';
import {ReadableSelect} from './ReadableSelect';
import {countryNames,nationalityDetail,nationalityPrefix,nationalitySelection,otherNationality,searchCountries} from '../formulario/nationality';
type Props=Pick<InputHTMLAttributes<HTMLInputElement>,'id'|'name'|'required'|'disabled'|'aria-invalid'|'aria-describedby'>&{id:string;value:string;onValueChange:(value:string)=>void};
/** Búsqueda local y selección nativa: el texto de búsqueda nunca se guarda como nacionalidad. */
export function NationalitySelect({id,value,onValueChange,...props}:Props){
 const [query,setQuery]=useState(''),selected=nationalitySelection(value),filtered=searchCountries(query);
 const options=selected&&selected!==otherNationality&&!filtered.includes(selected)?countryNames.filter(name=>name===selected||filtered.includes(name)):filtered;
 return <div className="nationality-control">
  <label className="field-help" htmlFor={id+'-search'}>Buscar país</label>
  <input id={id+'-search'} type="search" inputMode="search" autoComplete="off" placeholder="Escribe para filtrar países" value={query} maxLength={100} disabled={props.disabled} aria-controls={id} onChange={event=>setQuery(event.target.value)}/>
  <p className="field-help" role="status">{filtered.length?filtered.length+' países coinciden con la búsqueda.':'No encontramos coincidencias. Puedes elegir Otra / situación especial.'}</p>
  <div className="select-wrap"><ReadableSelect {...props} id={id} value={selected} onChange={event=>{setQuery('');onValueChange(event.target.value===otherNationality?nationalityPrefix:event.target.value);}}>
   <option value="">Selecciona un país</option>{options.map(name=><option key={name} value={name}>{name}</option>)}
   <option value={otherNationality}>Otra / situación especial</option>
  </ReadableSelect></div>
  {selected===otherNationality&&<div className="nationality-other"><label htmlFor={id+'-other'}>Indica tu nacionalidad o situación especial <span className="required-mark" aria-hidden="true">*</span></label>
   <input id={id+'-other'} type="text" maxLength={120} required={props.required} disabled={props.disabled} value={nationalityDetail(value)} aria-invalid={props['aria-invalid']} aria-describedby={props['aria-describedby']} onChange={event=>onValueChange(nationalityPrefix+event.target.value)}/>
   <p className="field-help">Utiliza esta opción si tu nacionalidad no aparece o necesitas indicar una situación especial, como no tener nacionalidad.</p>
  </div>}
 </div>;
}
