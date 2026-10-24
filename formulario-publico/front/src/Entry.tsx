import { lazy, Suspense, useEffect, useState } from 'react';
import { getSession } from './api';
import { getCurrentJourney } from './formulario/api';
import type { Journey } from './formulario/api';
const App=lazy(()=>import('./App'));
const StartPage=lazy(()=>import('./prototipo/StartPage'));
const NnaPage=lazy(()=>import('./formulario/NnaPage'));
const AdultPage=lazy(()=>import('./formulario/AdultPage'));
/** Retoma la intención del formulario después del callback oficial, conservando el ingreso provisional anterior. */
function Resume(){
  const [route,setRoute]=useState<{journey?:Journey;legacy?:boolean}|null>(null);
  useEffect(()=>{let active=true;void Promise.all([getSession(),getCurrentJourney().catch(()=>null)]).then(([s,j])=>{
    if(active)setRoute(j&&j.tipo!=='NNA'&&j.aceptaCanal?{journey:j}:{legacy:s.authenticated});
  }).catch(()=>{if(active)setRoute({});});return()=>{active=false;};},[]);
  if(!route)return <p role="status">Abriendo tu formulario…</p>;
  return route.journey?<AdultPage initialJourney={route.journey} mode={route.journey.tipo==='ADULTO_CU'?'cu':'manual'} />:route.legacy?<App />:<StartPage />;
}
export default function Entry(){
  const params=new URLSearchParams(window.location.search),view=params.get('vista');
  return <Suspense fallback={<p role="status">Cargando formulario…</p>}>{view==='inicio'?<StartPage />:view==='formulario-nna'?<NnaPage />:view==='formulario-adulto'?<AdultPage mode={params.get('modo')==='cu'?'cu':'manual'} />:params.has('formulario')||view==='provisional'?<App />:<Resume />}</Suspense>;
}
