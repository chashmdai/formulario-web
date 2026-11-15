import { ApiError } from '../api';

/** Pantalla de autorización y aviso de privacidad del formulario adulto: el recorrido adulto se crea solo al aceptar. */
export const ADULT_PRIVACY_ROUTE = '/?vista=formulario-adulto&modo=manual';

/**
 * Señal del servidor, sin datos personales: la fuente registral acredita mayoría de edad y el recorrido NNA quedó
 * invalidado. El navegador no calcula edades ni decide la derivación; solo reacciona a esta respuesta.
 */
export const isAdultDerivation = (error: unknown): boolean => error instanceof ApiError && error.status === 410;

/** Reemplaza la entrada del historial para que «Atrás» no regrese al recorrido invalidado. No traslada datos. */
export function redirectToAdult(location: Pick<Location, 'replace'> = window.location) {
  location.replace(ADULT_PRIVACY_ROUTE);
}
