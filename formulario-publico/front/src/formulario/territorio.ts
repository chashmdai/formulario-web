/** Catálogo congelado del mockup 13f9cf6; fuente compartida con la validación backend. */
import catalogo from './territorio.json';
export const regiones: Record<string,string> = catalogo.regiones;
export const comunas: Record<string,readonly string[]> = catalogo.comunas;
