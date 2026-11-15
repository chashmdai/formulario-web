import {LoadingState} from './ui/FormChrome';
import { lazy, Suspense, useEffect, useState } from 'react';
import { getSession } from './api';
import type { Session } from './api';
import { getCurrentJourney } from './formulario/api';
import type { Journey } from './formulario/api';
const App=lazy(()=>import('./App'));
const StartPage=lazy(()=>import('./prototipo/StartPage'));
const NnaPage=lazy(()=>import('./formulario/NnaPage'));
const AdultPage=lazy(()=>import('./formulario/AdultPage'));
const CertificationPage=lazy(()=>import('./CertificationPage'));
/** Retoma la intención del formulario después del callback oficial, conservando el ingreso provisional anterior. */
function Resume({session}:{session:Session}){
  const [route,setRoute]=useState<{journey?:Journey}|null>(null);
  // La sesión ya se consultó al abrir el sitio; aquí solo falta el recorrido vigente.
  useEffect(()=>{let active=true;void getCurrentJourney().catch(()=>null).then(j=>{
    if(active)setRoute(j&&j.tipo!=='NNA'&&j.aceptaCanal?{journey:j}:{});
  });return()=>{active=false;};},[]);
  if(!route)return <LoadingState full label="Estamos abriendo el sitio…" />;
  return route.journey?<AdultPage initialJourney={route.journey} initialSession={session} mode={route.journey.tipo==='ADULTO_CU'?'cu':'manual'} />:<StartPage />;
}
/** La sesión consultada al abrir el sitio se entrega a la vista: no vuelve a pedirse en la primera carga. */
function RegularEntry({session}:{session:Session}){
  const params=new URLSearchParams(window.location.search),view=params.get('vista');
  return <Suspense fallback={<LoadingState full label="Estamos abriendo el sitio…" />}>{view==='inicio'?<StartPage />:view==='formulario-nna'?<NnaPage initialSession={session} />:view==='formulario-adulto'?<AdultPage initialSession={session} mode={params.get('modo')==='cu'?'cu':'manual'} />:params.has('formulario')||view==='provisional'?<App />:<Resume session={session} />}</Suspense>;
}
export default function Entry(){
  const [session,setSession]=useState<Session|null>(null);
  const [failed,setFailed]=useState(false);
  useEffect(()=>{let active=true;void getSession().then(value=>{if(active)setSession(value);})
    .catch(()=>{if(active)setFailed(true);});return()=>{active=false;};},[]);
  if(failed)return <div role="alert">El servicio está temporalmente no disponible. Recarga la página para volver a intentarlo.</div>;
  if(session===null)return <LoadingState full label="Estamos abriendo el sitio…" />;
  return <Suspense fallback={<LoadingState full label="Estamos abriendo el sitio…" />}>{session.certificationMode===true?<CertificationPage />:<RegularEntry session={session} />}</Suspense>;
}
