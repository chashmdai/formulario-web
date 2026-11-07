import {useDialogFocus} from '../ui/useDialogFocus';
import { useEffect, useRef, useState } from 'react';
import '@fontsource/cabin/400.css';
import '@fontsource/cabin/500.css';
import '@fontsource/cabin/600.css';
import '@fontsource/cabin/700.css';
import { InstitutionalHeader } from './InstitutionalHeader';
import { AudienceSelection } from './AudienceSelection';
import { AdultAccess } from './AdultAccess';
import { PrivacyDialog } from './PrivacyDialog';
import { InstitutionalFooter } from './InstitutionalFooter';
import { ApiError, getSession } from '../api';
import { getJourneyCapabilities, startJourney } from '../formulario/api';
import { usePreferences } from '../formulario/preferences';
import './prototipo.css';
import './inicio.css';
import '../ui/experience.css';

/** Portada institucional: registra el aviso y el recorrido antes de abrir el formulario o ClaveÚnica. */
export default function StartPage({navigate=(url:string)=>window.location.assign(url)}:{navigate?:(url:string)=>void}={}) {
  const preferences = usePreferences(false);
  const [manualUnavailable, setManualUnavailable] = useState(false);
  const [privacyRoute, setPrivacyRoute] = useState<'cu' | 'manual' | null>(null);
  const dialog = useRef<HTMLDialogElement>(null);useDialogFocus(dialog);
  const [starting,setStarting]=useState(false),[startError,setStartError]=useState('');
  const pending=useRef(false);
  useEffect(() => { document.title = 'Formulario de contacto | Defensoría de la Niñez'; }, []);
  function close() { dialog.current?.close(); setManualUnavailable(false); }
  function beginAdult(route: 'cu' | 'manual') { close(); setStartError('');setPrivacyRoute(route); }
  async function acceptChannel() {
    if(pending.current)return;pending.current=true;setStarting(true);setStartError('');
    const route = privacyRoute;
    try {
      const [caps,session]=await Promise.all([getJourneyCapabilities(),getSession()]);
      if(!caps.disponible||route==='cu'&&!session.authenticated&&!session.loginAvailable)throw new ApiError(503);
      await startJourney(route==='cu'?'ADULTO_CU':'ADULTO_DECLARADO',caps.avisoAdulto,true);
      navigate(route==='cu'&&!session.authenticated?'/oauth2/authorization/claveunica':`/?vista=formulario-adulto&modo=${route==='cu'?'cu':'manual'}`);
    }catch(e){setStartError(e instanceof ApiError&&e.status===429?'Se iniciaron muchos formularios desde esta conexión en la última hora. Espera unos minutos e inténtalo nuevamente.':'Este acceso está temporalmente cerrado. Inténtalo nuevamente más tarde.');}
    finally{pending.current=false;setStarting(false);}
  }
  return <>
    <a className="skip" href="#landing">Saltar a la selección</a>
    <InstitutionalHeader {...preferences} />
    <AudienceSelection onNna={() => window.location.assign('/?vista=formulario-nna')} onAdult={() => { setManualUnavailable(false); dialog.current?.showModal(); }} />
    <AdultAccess dialogRef={dialog} onClose={close} onManual={() => beginAdult('manual')} onCu={() => beginAdult('cu')} manualUnavailable={manualUnavailable} />
    {privacyRoute && <PrivacyDialog busy={starting} error={startError} onClose={() => setPrivacyRoute(null)} onAccept={()=>{void acceptChannel();}} />}
    <InstitutionalFooter />
  </>;
}
