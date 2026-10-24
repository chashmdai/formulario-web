import { afterEach, describe, expect, it, vi } from 'vitest';
import { declarePerson, identifyPerson, resetPerson } from './api';
import { validRun } from './run';
const view = {personaId: 'presentador-adulto', estado: 'REVISADO', autenticada: false, runInformado: true,
  camposFaltantes: ['primerNombre', 'primerApellido'], camposObligatorios: ['primerNombre', 'primerApellido'], valoresDeclarados: {}, mensaje: 'Completa los datos solicitados.'};
const response = (value: unknown) => new Response(JSON.stringify(value), {status: 200, headers: {'Content-Type': 'application/json'}});
function mockFetch(value: unknown) {
  return vi.spyOn(window, 'fetch').mockImplementation(async input => String(input) === '/api/csrf'
    ? response({token: 'csrf-sintetico', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf'}) : response(value));
}
afterEach(() => vi.restoreAllMocks());
describe('Cliente de identificación real', () => {
  it('envía RUN y DV separados con CSRF, sin autorizar procedencia desde el cliente', async () => {
    const fetch = mockFetch(view);
    await identifyPerson('recorrido', 'presentador-adulto', 'PRESENTADOR', '12345678', '5');
    const request = fetch.mock.calls.find(([url]) => String(url).endsWith('/identificacion'))!;
    expect(request[1]?.credentials).toBe('same-origin');expect(request[1]?.cache).toBe('no-store');
    expect(request[1]?.headers).toEqual({'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'csrf-sintetico'});
    expect(JSON.parse(request[1]?.body as string)).toEqual({rol: 'PRESENTADOR', runNumero: '12345678', runDv: '5'});
  });
  it('rechaza una ampliación accidental que incluya atributos registrales', async () => {
    mockFetch({...view, atributos: {nombres: 'REGISTRAL SINTETICO'}});
    await expect(identifyPerson('r-privado', 'presentador-adulto', 'PRESENTADOR', '12345678', '5')).rejects.toMatchObject({status: 502});
  });
  it('rechaza declaraciones sobre campos que la vista no permite', async () => {
    mockFetch({...view, camposFaltantes: ['primerApellido'], camposObligatorios: ['primerApellido'], valoresDeclarados: {primerNombre: 'REGISTRAL SINTETICO'}});
    await expect(declarePerson('r', 'p', {primerApellido: 'PRUEBA'})).rejects.toMatchObject({status: 502});
  });
  it('deduplica una identificación pendiente, sin repetir POST al montar dos veces', async () => {
    const fetch = mockFetch(view);
    const one = identifyPerson('r-deduplicado', 'presentador-adulto', 'PRESENTADOR');
    const two = identifyPerson('r-deduplicado', 'presentador-adulto', 'PRESENTADOR');
    expect(one).toBe(two);await one;
    expect(fetch.mock.calls.filter(([url]) => String(url).endsWith('/identificacion'))).toHaveLength(1);
  });
  it('el reinicio acepta 204 y mantiene CSRF sin leer un JSON inexistente', async () => {
    const fetch = vi.spyOn(window, 'fetch').mockImplementation(async input => String(input) === '/api/csrf'
      ? response({token: 'csrf-sintetico', headerName: 'X-CSRF-TOKEN'}) : new Response(null, {status: 204}));
    await expect(resetPerson('r', 'presentador-adulto')).resolves.toBeUndefined();
    expect(fetch.mock.calls[1][1]?.method).toBe('DELETE');
  });
  it('valida módulo 11 sin confundir formato con identidad', () => {
    expect(validRun('12345678', '5')).toBe(true);expect(validRun('12345678', '0')).toBe(false);
    expect(validRun('', '')).toBe(false);expect(validRun('12.345.678', '5')).toBe(false);
  });
});
