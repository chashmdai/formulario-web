import {useRef} from 'react';
import type {InputHTMLAttributes} from 'react';
import {canonicalMobile} from '../formulario/contacts';
type Props=Pick<InputHTMLAttributes<HTMLInputElement>,'name'|'required'|'disabled'|'aria-invalid'|'aria-describedby'|'aria-errormessage'>&{id:string;value:string;onValueChange:(value:string)=>void};
/** Prefijo separado: escribir/borrar el número local nunca reintroduce dígitos. */
export function MobileInput({id,value,onValueChange,...props}:Props){
 const ref=useRef<HTMLInputElement>(null);
 const shown=value.startsWith('+569')?value.slice(4):canonicalMobile(value)?.slice(4)??value;
 function update(local:string){onValueChange(local?'+569'+local:'');}
 return <div className="mobile-input"><span className="mobile-prefix" id={id+'-prefix'}>+56 9</span><input {...props} id={id} ref={ref} type="tel" inputMode="numeric" autoComplete="tel-national" maxLength={32} placeholder="1234 5678" value={shown} aria-describedby={[id+'-prefix',props['aria-describedby']].filter(Boolean).join(' ')} onChange={event=>{
  const raw=event.target.value,parsed=canonicalMobile(raw);
  if(parsed&&(/^(\+|0056)/.test(raw)||/^56[0-9]{9}$/.test(raw)||(event.nativeEvent as InputEvent).inputType==='insertReplacementText'&&/^9[0-9]{8}$/.test(raw)))onValueChange(parsed);else update(raw);
 }} onPaste={event=>{
  const text=event.clipboardData.getData('text'),canonical=canonicalMobile(text);if(!canonical)return;
  const digits=text.replace(/\D/g,'');if(digits.length<8)return;
  event.preventDefault();const input=event.currentTarget,start=input.selectionStart??shown.length,end=input.selectionEnd??start,full=digits.length!==8;
  const next=full?canonical.slice(4):shown.slice(0,start)+digits+shown.slice(end);update(next);
  requestAnimationFrame(()=>{const cursor=full?8:start+digits.length;ref.current?.setSelectionRange(cursor,cursor);});
 }}/></div>;
}
