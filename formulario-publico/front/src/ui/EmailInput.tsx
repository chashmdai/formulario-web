import {useState} from 'react';
import type {InputHTMLAttributes} from 'react';
import {cleanEmail,validEmail} from '../formulario/contacts';
type Props=Pick<InputHTMLAttributes<HTMLInputElement>,'name'|'required'|'disabled'|'aria-invalid'|'aria-describedby'|'aria-errormessage'>&{id:string;value:string;onValueChange:(value:string)=>void};
export function EmailInput({id,value,onValueChange,...props}:Props){
 const [feedback,setFeedback]=useState('');
 return <div className="email-control"><input {...props} id={id} type="email" autoComplete="email" inputMode="email" maxLength={254} placeholder="nombre@ejemplo.cl" value={value} aria-invalid={!!feedback||props['aria-invalid']} aria-describedby={[props['aria-describedby'],feedback?id+'-format-error':''].filter(Boolean).join(' ')}
 onChange={event=>{setFeedback('');onValueChange(event.target.value);}}
 onBlur={event=>{const next=cleanEmail(event.target.value);onValueChange(next);setFeedback(next&&!validEmail(next)?'Revisa el formato del correo electrónico.':'');}}/>
 {feedback&&<p className="field-error" role="alert" id={id+'-format-error'}>{feedback}</p>}</div>;
}
