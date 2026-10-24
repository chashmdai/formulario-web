import { useEffect, useState } from 'react';

/** Exclusivamente preferencias visuales: nunca identidad, captura ni comprobantes. */
export function usePreferences(full: boolean) {
  const [theme, setTheme] = useState(() => {try {const v=localStorage.getItem('siger:theme');return v && ['light','dark','contrast'].includes(v) ? v : 'light';}catch {return 'light';}});
  const [scale, setScale] = useState(() => {try {const v=Number(localStorage.getItem('siger:font-scale') || 1);return Number.isFinite(v) ? Math.min(130,Math.max(100,Math.round(v*100))) : 100;}catch {return 100;}});
  useEffect(() => {
    document.documentElement.dataset.theme=theme;document.documentElement.dataset.view=full?'full':'steps';
    document.documentElement.style.setProperty('--ui-scale',String(scale/100));
    try {localStorage.setItem('siger:theme',theme);localStorage.setItem('siger:font-scale',String(scale/100));}catch {/* Preferencias opcionales. */}
  }, [theme,scale,full]);
  return {theme,scale,onTheme:(v:string)=>setTheme(['light','dark','contrast'].includes(v)?v:'light'),onIncrease:()=>setScale(v=>Math.min(130,v+10)),onDecrease:()=>setScale(v=>Math.max(100,v-10))};
}
