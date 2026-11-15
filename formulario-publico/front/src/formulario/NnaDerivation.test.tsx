import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api';
import NnaPage from './NnaPage';
import AdultPage from './AdultPage';
import type { IdentityView, Journey } from './api';
import { ADULT_PRIVACY_ROUTE, isAdultDerivation, redirectToAdult } from './derivation';

const redirect = vi.hoisted(() => ({calls: 0}));
vi.mock('./derivation', async original => {
  const actual = await original<typeof import('./derivation')>();
  return {...actual, redirectToAdult: () => { redirect.calls++; }};
});

const journey: Journey = {id: '11111111-1111-4111-8111-111111111111', tipo: 'NNA', avisoVersion: 'NNA_LAB_1', aceptaCanal: false, estado: 'BORRADOR', venceEn: '2026-10-24T00:00:00Z'};
const revised = (runInformado: boolean): IdentityView => ({personaId: 'presentador-nna', estado: 'REVISADO', autenticada: false, runInformado,
  camposFaltantes: ['primerNombre', 'segundoNombre', 'primerApellido', 'segundoApellido', 'edad'], camposObligatorios: ['primerNombre', 'primerApellido'], valoresDeclarados: {}, mensaje: 'Completa tus datos.'});
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), {status, headers: {'Content-Type': 'application/json'}});
/** Respuesta del servidor ante la derivación: un problema genérico, sin fecha, edad ni antecedentes registrales. */
const gone = () => response({type: 'about:blank', title: 'Gone', status: 410, detail: 'Operación no completada. Conserve su clave o identificador para reintentar.'}, 410);
let identity: IdentityView | null, withRun: 'menor' | 'adulto' | 'falla', restoreGone: boolean, submitGone: boolean, actualGone: boolean;
const requests: {path: string; method: string}[] = [];

beforeEach(() => {
  identity = null; withRun = 'adulto'; restoreGone = false; submitGone = false; actualGone = false; redirect.calls = 0; requests.length = 0;
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
  vi.spyOn(window, 'fetch').mockImplementation(async (input, init) => {
    const path = String(input), method = init?.method || 'GET', body = typeof init?.body === 'string' ? JSON.parse(init.body) : null;
    requests.push({path, method});
    if (path === '/api/csrf') return response({token: 'csrf-test', parameterName: '_csrf', headerName: 'X-CSRF-TOKEN'});
    if (path === '/api/session') return response({authenticated: false, identity: null, environment: 'SANDBOX', loginAvailable: true});
    if (path === '/api/formulario/capacidades') return response({disponible: true, avisoNna: 'NNA_LAB_1', avisoAdulto: 'ADULTO_LAB_1'});
    if (path === '/api/formulario/recepcion/capacidades') return response({nna: true, adultos: true});
    if (path === '/api/formulario/actual') { if (actualGone) { actualGone = false; return gone(); } return response(journey); }
    if (path === '/api/formulario/recorridos' && method === 'POST') return response({...journey, tipo: body.tipo, aceptaCanal: body.aceptaCanalDigital, avisoVersion: body.avisoVersion});
    if (path.endsWith('/personas/presentador-nna') && method === 'GET') return restoreGone ? gone() : identity ? response(identity) : response({}, 404);
    if (path.endsWith('/identificacion') && method === 'DELETE') { identity = null; return new Response(null, {status: 204}); }
    if (path.endsWith('/identificacion') && method === 'POST') {
      if (!body.runNumero) { identity = revised(false); return response(identity); }
      if (withRun === 'adulto') return gone();
      if (withRun === 'falla') return response({}, 503);
      identity = revised(true); return response(identity);
    }
    if (path.endsWith('/declaracion') && method === 'POST') { identity = {...identity!, valoresDeclarados: body.atributos}; return response(identity); }
    if (path.endsWith('/adjuntos')) return response([]);
    if (path.endsWith('/comunicaciones') && method === 'POST') return submitGone ? gone() : response({}, 503);
    throw new Error(`Solicitud inesperada en prueba: ${path}`);
  });
});
afterEach(() => { vi.restoreAllMocks(); });

async function enterRun() {
  await userEvent.click(await screen.findByRole('radio', {name: /Sí, quiero ingresarlo/}));
  await userEvent.type(screen.getByRole('textbox', {name: /^RUN/}), '12345678');
  await userEvent.type(screen.getByRole('textbox', {name: 'Dígito verificador del RUN'}), '5');
}
const unloadBlocked = () => { const event = new Event('beforeunload', {cancelable: true}); window.dispatchEvent(event); return event.defaultPrevented; };
const noWarnings = () => {
  expect(screen.queryByRole('alert')).toBeNull();
  expect(document.body.textContent).not.toMatch(/mayor de edad|18 años|derivad|redirig|Registro Civil/i);
};

describe('Derivación NNA → adulto por edad registral', () => {
  it('con 18 o más según la fuente redirige directo al aviso adulto, sin avisos ni bloqueo de salida', async () => {
    render(<NnaPage />);
    await userEvent.type(await screen.findByRole('textbox', {name: /^Primer nombre/}), 'NOMBRE');
    expect(unloadBlocked()).toBe(true);
    await enterRun();
    await waitFor(() => expect(redirect.calls).toBe(1), {timeout: 3000});
    noWarnings();
    expect(unloadBlocked()).toBe(false);
    // No se declara, adjunta ni envía nada del recorrido NNA tras la señal, ni se crea un recorrido adulto aquí.
    expect(requests.filter(r => r.method === 'POST' && /declaracion|comunicaciones|recorridos$/.test(r.path))).toEqual([]);
  });
  it('con menos de 18 permanece en el recorrido NNA', async () => {
    withRun = 'menor'; render(<NnaPage />);
    await enterRun();
    await waitFor(() => expect(requests.some(r => r.method === 'POST' && r.path.endsWith('/identificacion'))).toBe(true));
    await act(async () => { await new Promise(resolve => setTimeout(resolve, 800)); });
    expect(redirect.calls).toBe(0);
    expect(screen.getByRole('textbox', {name: /^Primer nombre/})).toBeTruthy();
  });
  it('una consulta fallida mantiene el recorrido NNA y muestra el error habitual', async () => {
    withRun = 'falla'; render(<NnaPage />);
    await enterRun();
    expect(await screen.findByText(/No pudimos guardar o revisar tus datos/, {}, {timeout: 3000})).toBeTruthy();
    expect(redirect.calls).toBe(0);
  });
  it('al recargar un recorrido ya derivado redirige sin mensajes', async () => {
    restoreGone = true; render(<NnaPage />);
    await waitFor(() => expect(redirect.calls).toBe(1));
    noWarnings();
  });
  it('si la respuesta de derivación se perdió, la recarga no abre otro recorrido NNA y redirige', async () => {
    actualGone = true; render(<NnaPage />);
    await waitFor(() => expect(redirect.calls).toBe(1));
    noWarnings();
    expect(requests.filter(r => r.method === 'POST')).toEqual([]);
  });
  it('el formulario adulto muestra su aviso de privacidad y solo crea el recorrido al aceptar', async () => {
    actualGone = true; render(<AdultPage mode="manual" />);
    const dialog = await screen.findByRole('dialog', {name: /conoce cómo cuidaremos tus datos/});
    expect(screen.queryByRole('alert')).toBeNull();
    expect(requests.filter(r => r.method === 'POST')).toEqual([]);
    expect(screen.queryByRole('textbox', {name: /Relato|Cuéntanos/})).toBeNull();
    await userEvent.click(within(dialog).getByRole('radio', {name: /^Acepto/}));
    expect(requests.filter(r => r.method === 'POST')).toEqual([]);
    await userEvent.click(within(dialog).getByRole('button', {name: 'Continuar'}));
    await waitFor(() => expect(requests.filter(r => r.method === 'POST' && r.path === '/api/formulario/recorridos')).toHaveLength(1));
  });
  it('si el servidor deriva al enviar, no muestra error ni comprobante y redirige', async () => {
    submitGone = true; render(<NnaPage />);
    await userEvent.click(await screen.findByRole('radio', {name: /No sé mi RUN/}));
    await userEvent.type(await screen.findByRole('textbox', {name: /^Primer nombre/}), 'NOMBRE');
    await userEvent.type(screen.getByRole('textbox', {name: /^Primer apellido/}), 'APELLIDO');
    await userEvent.selectOptions(screen.getByRole('combobox', {name: /^Región/}), '13');
    await userEvent.selectOptions(screen.getByRole('combobox', {name: /¿Cómo podemos contactarte/}), 'unsafe');
    await userEvent.click(screen.getByRole('button', {name: 'Continuar'}));
    await userEvent.type(await screen.findByRole('textbox', {name: /^Cuéntanos qué pasó/}), 'Relato sintético.');
    await userEvent.click(screen.getByRole('button', {name: 'Revisar mi comunicación'}));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', {name: 'Enviar comunicación'}));
    await waitFor(() => expect(redirect.calls).toBe(1));
    noWarnings();
    expect(screen.queryByRole('heading', {name: 'Recibimos tu comunicación'})).toBeNull();
  });
});

describe('Señal de derivación', () => {
  it('solo un 410 del servidor deriva; el navegador no decide por edad', () => {
    expect(isAdultDerivation(new ApiError(410))).toBe(true);
    for (const status of [400, 404, 409, 429, 502, 503]) expect(isAdultDerivation(new ApiError(status))).toBe(false);
    expect(isAdultDerivation(new Error('410'))).toBe(false);
    expect(isAdultDerivation({status: 410})).toBe(false);
  });
  it('el destino es el aviso de privacidad adulto, sin datos del recorrido NNA', async () => {
    const url = new URL(ADULT_PRIVACY_ROUTE, 'https://localhost');
    expect(Object.fromEntries(url.searchParams)).toEqual({vista: 'formulario-adulto', modo: 'manual'});
    const actual = await vi.importActual<typeof import('./derivation')>('./derivation');
    const replace = vi.fn();
    actual.redirectToAdult({replace});
    expect(replace).toHaveBeenCalledExactlyOnceWith(ADULT_PRIVACY_ROUTE);
    expect(redirectToAdult).not.toBe(actual.redirectToAdult);
  });
});
