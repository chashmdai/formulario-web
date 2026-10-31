import {useRef,useState} from 'react';
import type {InputHTMLAttributes} from 'react';
import {canonicalMobile} from '../formulario/contacts';
type Props=Pick<InputHTMLAttributes<HTMLInputElement>,'name'|'required'|'disabled'|'aria-invalid'|'aria-describedby'|'aria-errormessage'>&{id:string;value:string;onValueChange:(value:string)=>void};
/** Prefijo fijo independiente; el campo editable contiene como máximo ocho dígitos. */
export function MobileInput({id,value,onValueChange,...props}:Props){
 const ref=useRef<HTMLInputElement>(null),[feedback,setFeedback]=useState('');
 const shown=value.startsWith('+569')?value.slice(4):canonicalMobile(value)?.slice(4)??value;
 function update(local:string){onValueChange(local?'+569'+local:'');}
 return <div className="mobile-control"><div className="mobile-input"><span className="mobile-prefix" id={id+'-prefix'}>+56 9</span><input {...props} id={id} ref={ref} type="tel" inputMode="numeric" autoComplete="tel-national" maxLength={8} placeholder="12345678" value={shown} aria-invalid={!!feedback||props['aria-invalid']} onBlur={()=>setFeedback(value&&!canonicalMobile(value)?'Escribe los ocho dígitos después de +56 9.':'')} aria-describedby={[id+'-prefix',props['aria-describedby'],feedback?id+'-paste-error':''].filter(Boolean).join(' ')} onChange={event=>{
  setFeedback('');const raw=event.target.value,parsed=canonicalMobile(raw);
  if(parsed&&(/^(\+|0056)/.test(raw)||/^56[0-9]{9}$/.test(raw)||(event.nativeEvent as InputEvent).inputType==='insertReplacementText'&&/^9[0-9]{8}$/.test(raw)))onValueChange(parsed);
  else update(raw.replace(/\D/g,'').slice(0,8));
 }} onPaste={event=>{
  event.preventDefault();const text=event.clipboardData.getData('text'),canonical=canonicalMobile(text),digits=text.replace(/\D/g,'');
  const full=!!canonical&&digits.length>8,partial=/^[0-9\s().-]+$/.test(text)&&digits.length>0&&digits.length<=8;
  if(!canonical&&!partial){setFeedback('No se incorporó el número. Pega ocho dígitos o un celular chileno con prefijo +56 9.');return;}
  const input=event.currentTarget,start=input.selectionStart??shown.length,end=input.selectionEnd??start;
  const next=full?canonical!.slice(4):shown.slice(0,start)+digits+shown.slice(end);
  if(next.length>8){setFeedback('El número admite ocho dígitos. Selecciona el contenido que quieres reemplazar.');return;}
  setFeedback('');update(next);requestAnimationFrame(()=>{const cursor=full?8:start+digits.length;ref.current?.setSelectionRange(cursor,cursor);});
 }}/></div>{feedback&&<p className="field-error" role="alert" id={id+'-paste-error'}>{feedback}</p>}</div>;
}
