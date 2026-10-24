import { comunas, regiones } from './territorio';

export type FormValues = Readonly<Record<string, string>>;
export type FormErrors = Record<string, string>;
const labels: Record<string, string> = {
  'nna-region': 'Región', 'nna-primary-method': '¿Cómo podemos contactarte?', 'nna-secondary-method': 'Otra forma de contacto',
  'nna-story': 'Cuéntanos qué pasó o cómo podemos ayudarte',
};
export function nnaRequired(values: FormValues): string[] {
  const required = ['nna-region', 'nna-primary-method'];
  for (const prefix of ['nna-primary', 'nna-secondary']) {
    if (prefix === 'nna-secondary' && values['nna-extra-contact'] !== 'true') continue;
    if (prefix === 'nna-secondary') required.push(`${prefix}-method`);
    const method = values[`${prefix}-method`];
    if (method === 'celular' || method === 'email') required.push(`${prefix}-${method}`);
    if (method === 'social') { required.push(`${prefix}-network`, `${prefix}-handle`); if (values[`${prefix}-network`] === 'otra') required.push(`${prefix}-other-network`); }
  }
  return required;
}
export function validMobile(value: string): boolean {
  let digits = value.replace(/\D/g, '');
  if (digits.length === 11 && digits.startsWith('56')) digits = digits.slice(2);
  return /^9\d{8}$/.test(digits);
}
export function nnaValues(values: FormValues): Record<string, string> {
  const output: Record<string, string> = {};
  for (const id of ['nna-region','nna-commune','nna-preferred','nna-treatment','nna-contact-instructions','nna-story','nna-primary-method','nna-extra-contact']) {
    if (values[id]) output[id] = values[id];
  }
  if (values['nna-treatment'] === 'otra' && values['nna-treatment-text']) output['nna-treatment-text'] = values['nna-treatment-text'];
  if (values['nna-primary-method'] && values['nna-primary-method'] !== 'unsafe' && values['nna-safe']) output['nna-safe'] = values['nna-safe'];
  for (const prefix of ['nna-primary', 'nna-secondary']) {
    if (prefix === 'nna-secondary' && values['nna-extra-contact'] !== 'true') continue;
    const method = values[`${prefix}-method`];
    if (method) output[`${prefix}-method`] = method;
    const keys = method === 'social' ? ['network','handle', ...(values[`${prefix}-network`] === 'otra' ? ['other-network'] : [])] : method === 'celular' || method === 'email' ? [method] : [];
    for (const key of keys) if (values[`${prefix}-${key}`]) output[`${prefix}-${key}`] = values[`${prefix}-${key}`];
  }
  return output;
}
export function validateNna(values: FormValues, story: boolean): FormErrors {
  const errors: FormErrors = {};
  const clean = nnaValues(values);
  for (const id of [...nnaRequired(values), ...(story ? ['nna-story'] : [])]) {
    if (!values[id]?.trim()) errors[id] = `Completa ${labels[id] || (id.endsWith('-email') ? 'Correo electrónico' : id.endsWith('-celular') ? 'Número de celular' : id.endsWith('-handle') ? 'Tu usuario o enlace de perfil' : 'este campo')}.`;
  }
  if (values['nna-region'] && !regiones[values['nna-region']]) errors['nna-region'] = 'Selecciona una región válida.';
  if (values['nna-commune'] && !(comunas[values['nna-region']] || []).includes(values['nna-commune'])) errors['nna-commune'] = 'Selecciona una comuna de la región indicada.';
  if (values['nna-primary-method'] && !['celular','email','social','unsafe'].includes(values['nna-primary-method'])) errors['nna-primary-method'] = 'Selecciona una forma de contacto.';
  if (values['nna-extra-contact'] === 'true' && values['nna-secondary-method'] && !['celular','email','social'].includes(values['nna-secondary-method'])) errors['nna-secondary-method'] = 'Selecciona una forma de contacto.';
  for (const [id, value] of Object.entries(clean)) {
    if (id === 'nna-story' && !story) continue;
    if (value.length > (id === 'nna-story' ? 5000 : 500)) errors[id] = id === 'nna-story' ? 'Tu relato admite hasta 5000 caracteres.' : 'Este campo admite hasta 500 caracteres.';
    if (id.endsWith('-email') && value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) errors[id] = 'Revisa el formato del correo electrónico.';
    if (id.endsWith('-celular') && value && !validMobile(value)) errors[id] = 'Ingresa un celular chileno de 9 dígitos: +56 9 XXXX XXXX.';
  }
  return errors;
}
