import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/700.css';
import '@fontsource/cabin/400.css';
import '@fontsource/cabin/500.css';
import '@fontsource/cabin/600.css';
import '@fontsource/cabin/700.css';
import cabin400 from '@fontsource/cabin/files/cabin-latin-400-normal.woff2?url';
import cabin500 from '@fontsource/cabin/files/cabin-latin-500-normal.woff2?url';
import cabin600 from '@fontsource/cabin/files/cabin-latin-600-normal.woff2?url';
import cabin700 from '@fontsource/cabin/files/cabin-latin-700-normal.woff2?url';
import Entry from './Entry';
import './ui/loading.css';
import { systemTheme } from './formulario/preferences';
// Tema antes del primer render: elección guardada; si no existe, claro (o alto contraste del sistema).
try { const theme=localStorage.getItem('siger:theme'); document.documentElement.dataset.theme=theme&&['light','dark','contrast'].includes(theme)?theme:systemTheme(); } catch { document.documentElement.dataset.theme='light'; }

// Tipografía institucional (latín) en paralelo con la sesión y el código de la vista: evita el salto de diseño
// que producía su llegada tardía. Mismo origen y mismos archivos que declaran las hojas de @fontsource.
for (const href of [cabin400, cabin500, cabin600, cabin700]) {
  const link = document.createElement('link'); link.rel = 'preload'; link.as = 'font'; link.type = 'font/woff2'; link.crossOrigin = 'anonymous'; link.href = href;
  document.head.appendChild(link);
}

createRoot(document.getElementById('root')!).render(<StrictMode><Entry /></StrictMode>);
