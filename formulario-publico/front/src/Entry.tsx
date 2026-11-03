import {LoadingState} from './ui/FormChrome';
import { lazy, Suspense, useEffect, useState } from 'react';
import { getSession } from './api';
import { getCurrentJourney } from './formulario/api';
import type { Journey } from './formulario/api';
const App=lazy(()=>import('./App'));
const StartPage=lazy(()=>import('./prototipo/StartPage'));
const NnaPage=lazy(()=>import('./formulario/NnaPage'));
const AdultPage=lazy(()=>import('./formulario/AdultPage'));
const CertificationPage=lazy(()=>import('./CertificationPage'));
/** Retoma la intención del formulario después del callback oficial, conservando el ingreso provisional anterior. */
function Resume(){
  const [route,setRoute]=useState<{journey?:Journey}|null>(null);
  useEffect(()=>{let active=true;void Promise.all([getSession(),getCurrentJourney().catch(()=>null)]).then(([,j])=>{
    if(active)setRoute(j&&j.tipo!=='NNA'&&j.aceptaCanal?{journey:j}:{});
  }).catch(()=>{if(active)setRoute({});});return()=>{active=false;};},[]);
  if(!route)return <LoadingState full />;
  return route.journey?<AdultPage initialJourney={route.journey} mode={route.journey.tipo==='ADULTO_CU'?'cu':'manual'} />:<StartPage />;
}
function RegularEntry(){
  const params=new URLSearchParams(window.location.search),view=params.get('vista');
  return <Suspense fallback={<LoadingState full />}>{view==='inicio'?<StartPage />:view==='formulario-nna'?<NnaPage />:view==='formulario-adulto'?<AdultPage mode={params.get('modo')==='cu'?'cu':'manual'} />:params.has('formulario')||view==='provisional'?<App />:<Resume />}</Suspense>;
}
export default function Entry(){
  const [mode,setMode]=useState<boolean|null>(null);
  const [failed,setFailed]=useState(false);
  useEffect(()=>{let active=true;void getSession().then(session=>{if(active)setMode(session.certificationMode===true);})
    .catch(()=>{if(active)setFailed(true);});return()=>{active=false;};},[]);
  if(failed)return <div role="alert">El servicio está temporalmente no disponible. Recarga la página para volver a intentarlo.</div>;
  if(mode===null)return <LoadingState full label="Estamos abriendo el sitio…" />;
  return <Suspense fallback={<LoadingState full label="Estamos abriendo el sitio…" />}>{mode?<CertificationPage />:<RegularEntry />}</Suspense>;
}
