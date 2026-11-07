import { regiones } from './territorio';

/** Orden geográfico norte-sur del catálogo compartido con el backend (los códigos no cambian). */
export const REGION_ORDER = ['15', '01', '02', '03', '04', '05', '13', '06', '07', '16', '08', '09', '14', '10', '11', '12'] as const;

export function RegionOptions({placeholder = 'Selecciona una región'}: {placeholder?: string}) {
  return <><option value="">{placeholder}</option>{REGION_ORDER.map(code => <option key={code} value={code}>{regiones[code]}</option>)}</>;
}
