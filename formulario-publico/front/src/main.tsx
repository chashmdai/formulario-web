import { lazy, StrictMode, Suspense } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/700.css';
const App = lazy(() => import('./App'));
const StartPage = lazy(() => import('./prototipo/StartPage'));
const NnaPage = lazy(() => import('./formulario/NnaPage'));
const inicio = new URLSearchParams(window.location.search).get('vista') === 'inicio';
const nna = new URLSearchParams(window.location.search).get('vista') === 'formulario-nna';

createRoot(document.getElementById('root')!).render(<StrictMode><Suspense fallback={<p role="status">Cargando formulario…</p>}>{inicio ? <StartPage /> : nna ? <NnaPage /> : <App />}</Suspense></StrictMode>);
