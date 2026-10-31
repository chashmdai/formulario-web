import {Children,isValidElement} from 'react';
import type {ReactNode,SelectHTMLAttributes} from 'react';
function optionText(children:ReactNode,wanted:string):string{
 let result='';
 Children.forEach(children,child=>{
  if(!isValidElement<{value?:string|number;children?:ReactNode}>(child))return;
  if(child.type==='option'){
   const label=Children.toArray(child.props.children).filter(c=>typeof c==='string'||typeof c==='number').join('').replace(/\s+/g,' ').trim();
   if(String(child.props.value??label)===wanted)result=label;
  }else if(child.props.children){const nested=optionText(child.props.children,wanted);if(nested)result=nested;}
 });
 return result;
}
/** Conserva el selector nativo y muestra completa una selección larga, sin alterar su nombre accesible. */
export function ReadableSelect({children,...props}:SelectHTMLAttributes<HTMLSelectElement>){
 const label=optionText(children,String(props.value??''));
 return <><select {...props}>{children}</select>{props.value&&label.length>22&&<span className="field-help selected-option readable-selection" aria-hidden="true">{label}</span>}</>;
}
