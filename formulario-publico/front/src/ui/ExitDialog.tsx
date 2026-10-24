import {useDialogFocus} from './useDialogFocus';
import {useEffect,useRef} from 'react';
import {Icon} from './FormChrome';
export function ExitDialog({onStay,onLeave}:{onStay:()=>void;onLeave:()=>void}){
 const dialog=useRef<HTMLDialogElement>(null);useDialogFocus(dialog);useEffect(()=>{const d=dialog.current;d?.showModal();return()=>d?.close();},[]);
 return <dialog ref={dialog} className="dialog exit-dialog" aria-labelledby="exit-title" aria-describedby="exit-description" onCancel={onStay}><div className="dialog-head"><div><span className="mini-tag">Tu comunicación está en curso</span><h2 id="exit-title">¿Quieres volver al inicio?</h2></div><button type="button" className="dialog-close" aria-label="Cerrar y seguir completando" onClick={onStay}><Icon name="x"/></button></div><div className="dialog-body"><p id="exit-description">Aún no has enviado tu comunicación. Los cambios que no hayas enviado pueden perderse si sales del formulario.</p></div><div className="dialog-foot"><div className="dialog-buttons"><button type="button" className="btn btn-secondary" onClick={onLeave}>Volver al inicio</button><button type="button" className="btn btn-primary" onClick={onStay} autoFocus>Seguir completando</button></div></div></dialog>;
}
