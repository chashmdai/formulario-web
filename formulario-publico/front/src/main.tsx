import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/700.css';
import Entry from './Entry';
import './ui/loading.css';
try { const theme=localStorage.getItem('siger:theme'); document.documentElement.dataset.theme=theme&&['light','dark','contrast'].includes(theme)?theme:'light'; } catch { document.documentElement.dataset.theme='light'; }

createRoot(document.getElementById('root')!).render(<StrictMode><Entry /></StrictMode>);
