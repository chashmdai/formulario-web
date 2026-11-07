import { useEffect, useState } from 'react';

const THEMES = ['light', 'dark', 'contrast'];
function stored(key: string): string | null { try { return localStorage.getItem(key); } catch { return null; } }
function store(key: string, value: string) { try { localStorage.setItem(key, value); } catch { /* Preferencias opcionales. */ } }
function query(media: string): boolean { try { return typeof window.matchMedia === 'function' && window.matchMedia(media).matches; } catch { return false; } }
/** Sin elección explícita se sigue el sistema: alto contraste, luego modo oscuro. */
export function systemTheme(): string { return query('(prefers-contrast: more)') || query('(forced-colors: active)') ? 'contrast' : query('(prefers-color-scheme: dark)') ? 'dark' : 'light'; }

/** Exclusivamente preferencias visuales: nunca identidad, captura ni comprobantes. */
export function usePreferences(full: boolean) {
  const [chosen, setChosen] = useState<string | null>(() => { const v = stored('siger:theme'); return v && THEMES.includes(v) ? v : null; });
  const [system, setSystem] = useState(systemTheme);
  const [scale, setScale] = useState(() => { const v = Number(stored('siger:font-scale') || 1); return Number.isFinite(v) ? Math.min(130, Math.max(100, Math.round(v * 100))) : 100; });
  const theme = chosen ?? system;
  useEffect(() => {
    if (chosen || typeof window.matchMedia !== 'function') return;
    const lists = ['(prefers-color-scheme: dark)', '(prefers-contrast: more)', '(forced-colors: active)'].map(m => window.matchMedia(m));
    const update = () => setSystem(systemTheme());
    lists.forEach(list => list.addEventListener?.('change', update));
    return () => lists.forEach(list => list.removeEventListener?.('change', update));
  }, [chosen]);
  useEffect(() => {
    document.documentElement.dataset.theme = theme; document.documentElement.dataset.view = full ? 'full' : 'steps';
    document.documentElement.style.setProperty('--ui-scale', String(scale / 100));
    store('siger:font-scale', String(scale / 100));
  }, [theme, scale, full]);
  return {theme, scale,
    onTheme: (v: string) => { const next = THEMES.includes(v) ? v : 'light'; setChosen(next); store('siger:theme', next); },
    onIncrease: () => setScale(v => Math.min(130, v + 10)), onDecrease: () => setScale(v => Math.max(100, v - 10))};
}
