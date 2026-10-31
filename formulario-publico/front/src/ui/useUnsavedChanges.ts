import {useEffect,useRef} from 'react';
/** Solo advierte pérdida: no guarda borradores ni modifica el historial o almacenamiento del navegador. */
export function useUnsavedChanges(enabled:boolean){
 const approvedLeave=useRef(false);
 useEffect(()=>{
  if(!enabled){approvedLeave.current=false;return;}
  const beforeUnload=(event:BeforeUnloadEvent)=>{
   if(approvedLeave.current)return;
   event.preventDefault();event.returnValue='';
  };
  window.addEventListener('beforeunload',beforeUnload);
  return()=>window.removeEventListener('beforeunload',beforeUnload);
 },[enabled]);
 return ()=>{approvedLeave.current=true;};
}
