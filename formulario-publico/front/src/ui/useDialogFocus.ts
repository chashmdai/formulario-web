import {useEffect} from 'react';
import type {RefObject} from 'react';
/** Mantiene el recorrido de Tab dentro del diálogo modal, incluso si cambia su contenido. */
export function useDialogFocus(ref:RefObject<HTMLDialogElement|null>){
 useEffect(()=>{
  const dialog=ref.current;if(!dialog)return;
  function keydown(event:KeyboardEvent){
   if(event.key!=='Tab'||!dialog!.open)return;
   const controls=Array.from(dialog!.querySelectorAll<HTMLElement>('button:not(:disabled),a[href],input:not(:disabled):not([type="hidden"]),select:not(:disabled),textarea:not(:disabled),[tabindex="0"]')).filter(element=>{
    if(element.closest('[hidden],[inert]'))return false;
    for(let node:HTMLElement|null=element;node&&node!==dialog;node=node.parentElement){const style=getComputedStyle(node);if(style.display==='none'||style.visibility==='hidden')return false;}
    return true;
   });
   const first=controls[0],last=controls.at(-1);
   if(!first){event.preventDefault();return;}
   if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}
   else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
  }
  dialog.addEventListener('keydown',keydown);return()=>dialog.removeEventListener('keydown',keydown);
 },[ref]);
}
