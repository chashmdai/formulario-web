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
import './prototipo.css';
import './inicio.css';

/** Pantalla de paridad opt-in mientras se migran los recorridos completos. Sin simulaciones de identidad. */
export default function StartPage() {
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
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.dataset.view = 'steps';
    document.documentElement.style.setProperty('--ui-scale', String(scale / 100));
    document.title = 'Formulario de contacto | Defensoría de la Niñez';
    try { localStorage.setItem('siger:theme', theme); localStorage.setItem('siger:font-scale', String(scale / 100)); } catch { /* Preferencias opcionales, sin datos personales. */ }
  }, [theme, scale]);
  function close() { dialog.current?.close(); setManualUnavailable(false); }
  function beginAdult(route: 'cu' | 'manual') { close(); setPrivacyRoute(route); }
  function acceptChannel() {
    const route = privacyRoute;
    setPrivacyRoute(null);
    if (route === 'cu') window.location.assign('/oauth2/authorization/claveunica');
    else { setManualUnavailable(true); dialog.current?.showModal(); }
  }
  return <>
    <a className="skip" href="#landing">Saltar a la selección</a>
    <InstitutionalHeader theme={theme} scale={scale} onTheme={v => setTheme(['light', 'dark', 'contrast'].includes(v) ? v : 'light')}
      onIncrease={() => setScale(v => Math.min(130, v + 10))} onDecrease={() => setScale(v => Math.max(100, v - 10))} />
    <AudienceSelection onNna={() => window.location.assign('/?formulario=nna')} onAdult={() => { setManualUnavailable(false); dialog.current?.showModal(); }} />
    <AdultAccess dialogRef={dialog} onClose={close} onManual={() => beginAdult('manual')} onCu={() => beginAdult('cu')} manualUnavailable={manualUnavailable} />
    {privacyRoute && <PrivacyDialog onClose={() => setPrivacyRoute(null)} onAccept={acceptChannel} />}
    <InstitutionalFooter />
  </>;
}
