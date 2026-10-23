import { lazy, StrictMode, Suspense } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/700.css';
const App = lazy(() => import('./App'));
const StartPage = lazy(() => import('./prototipo/StartPage'));
const inicio = new URLSearchParams(window.location.search).get('vista') === 'inicio';

createRoot(document.getElementById('root')!).render(<StrictMode><Suspense fallback={<p role="status">Cargando formulario…</p>}>{inicio ? <StartPage /> : <App />}</Suspense></StrictMode>);
