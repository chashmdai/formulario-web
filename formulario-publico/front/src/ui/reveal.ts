import { useLayoutEffect, useRef, useState } from 'react';

/**
 * Lleva el inicio de la sección al viewport sin animar el desplazamiento: el contenido nuevo aparece en su lugar
 * (con una entrada breve por CSS) en vez de recorrer la página. Si el título ya está a la vista, no se mueve.
 */
export function revealSection(element:HTMLElement|null){
  if(!element)return;
  const top=element.getBoundingClientRect().top;
  if(top<0||top>window.innerHeight*.45)element.scrollIntoView?.({block:'start',behavior:'instant'});
}

/**
 * Solicita mostrar y enfocar un elemento (título de etapa o resumen de errores). Se ejecuta tras el commit en que el
 * formulario deja de estar ocupado (inerte), antes de pintar: el foco no se pierde y no hay un cuadro intermedio con
 * la posición anterior. La última solicitud prevalece.
 */
export function useCommitFocus(blocked:boolean){
  const pending=useRef<(()=>{section:HTMLElement|null;target:HTMLElement|null})|null>(null);
  const [request,setRequest]=useState(0);
  useLayoutEffect(()=>{
    if(!pending.current||blocked)return;
    const {section,target}=pending.current();pending.current=null;
    revealSection(section);target?.focus({preventScroll:true});
  },[request,blocked]);
  return (locate:()=>{section:HTMLElement|null;target:HTMLElement|null})=>{pending.current=locate;setRequest(n=>n+1);};
}
