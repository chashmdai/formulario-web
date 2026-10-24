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
import './prototipo.css';
import './inicio.css';

/** Portada institucional: registra el aviso y el recorrido antes de abrir el formulario o ClaveÚnica. */
export default function StartPage({navigate=(url:string)=>window.location.assign(url)}:{navigate?:(url:string)=>void}={}) {
  const [theme, setTheme] = useState(() => {
    try { const value = localStorage.getItem('siger:theme'); return value && ['light', 'dark', 'contrast'].includes(value) ? value : 'light'; }
    catch { return 'light'; }
  });
  const [scale, setScale] = useState(() => {
    try { const value = Number(localStorage.getItem('siger:font-scale') || 1); return Number.isFinite(value) ? Math.min(130, Math.max(100, Math.round(value * 100))) : 100; }
    catch { return 100; }
  });
  const [manualUnavailable, setManualUnavailable] = useState(false);
  const [privacyRoute, setPrivacyRoute] = useState<'cu' | 'manual' | null>(null);
  const dialog = useRef<HTMLDialogElement>(null);
  const [starting,setStarting]=useState(false),[startError,setStartError]=useState('');
  const pending=useRef(false);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.dataset.view = 'steps';
    document.documentElement.style.setProperty('--ui-scale', String(scale / 100));
    document.title = 'Formulario de contacto | Defensoría de la Niñez';
    try { localStorage.setItem('siger:theme', theme); localStorage.setItem('siger:font-scale', String(scale / 100)); } catch { /* Preferencias opcionales, sin datos personales. */ }
  }, [theme, scale]);
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
    }catch{setStartError('Este acceso está temporalmente cerrado. Inténtalo nuevamente más tarde.');}
    finally{pending.current=false;setStarting(false);}
  }
  return <>
    <a className="skip" href="#landing">Saltar a la selección</a>
    <InstitutionalHeader theme={theme} scale={scale} onTheme={v => setTheme(['light', 'dark', 'contrast'].includes(v) ? v : 'light')}
      onIncrease={() => setScale(v => Math.min(130, v + 10))} onDecrease={() => setScale(v => Math.max(100, v - 10))} />
    <AudienceSelection onNna={() => window.location.assign('/?vista=formulario-nna')} onAdult={() => { setManualUnavailable(false); dialog.current?.showModal(); }} />
    <AdultAccess dialogRef={dialog} onClose={close} onManual={() => beginAdult('manual')} onCu={() => beginAdult('cu')} manualUnavailable={manualUnavailable} />
    {privacyRoute && <PrivacyDialog busy={starting} error={startError} onClose={() => setPrivacyRoute(null)} onAccept={()=>{void acceptChannel();}} />}
    <InstitutionalFooter />
  </>;
}
