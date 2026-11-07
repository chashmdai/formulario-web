import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/700.css';
import Entry from './Entry';
import './ui/loading.css';
import { systemTheme } from './formulario/preferences';
// Tema antes del primer render: elección guardada; si no existe, claro (o alto contraste del sistema).
try { const theme=localStorage.getItem('siger:theme'); document.documentElement.dataset.theme=theme&&['light','dark','contrast'].includes(theme)?theme:systemTheme(); } catch { document.documentElement.dataset.theme='light'; }

createRoot(document.getElementById('root')!).render(<StrictMode><Entry /></StrictMode>);
