import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NnaFlow } from './NnaFlow';
import type { AttachmentPort } from './NnaFlow';
import type { IdentityView, Journey } from './api';
import { ApiError } from '../api';
import { declarePerson, getPerson, identifyPerson, resetPerson } from './api';
vi.mock('./api', () => ({getPerson: vi.fn(), identifyPerson: vi.fn(), declarePerson: vi.fn(), resetPerson: vi.fn()}));
const journey: Journey = {id: 'nna-sintetico', tipo: 'NNA', avisoVersion: 'NNA_LAB_1', aceptaCanal: false, estado: 'BORRADOR', venceEn: '2026-10-24T00:00:00Z'};
const view = (): IdentityView => ({personaId: 'presentador-nna', estado: 'REVISADO', autenticada: false, runInformado: false,
  camposFaltantes: ['primerNombre','segundoNombre','primerApellido','segundoApellido','edad'], camposObligatorios: ['primerNombre','primerApellido'], valoresDeclarados: {}, mensaje: 'Completa los datos solicitados.'});
beforeEach(() => {
  vi.resetAllMocks();vi.mocked(getPerson).mockRejectedValue(new ApiError(404));vi.mocked(identifyPerson).mockResolvedValue(view());vi.mocked(resetPerson).mockResolvedValue(undefined);
  vi.mocked(declarePerson).mockImplementation(async (_j, _p, values) => ({...view(), valoresDeclarados: values}));
});
afterEach(() => cleanup());
function mount(full = false, files?: AttachmentPort) {
  const review = vi.fn().mockResolvedValue(undefined), step = vi.fn();
  const port = files || {replace: vi.fn().mockResolvedValue([]), clear: vi.fn().mockResolvedValue(undefined)};
  render(<NnaFlow journey={journey} session={null} full={full} filePort={port} onReview={review} onStep={step} />);
  return {review, step, port};
}
async function identity() {
  await userEvent.type(await screen.findByRole('textbox', {name: /^Primer nombre/}), 'NOMBRE');
  await userEvent.type(screen.getByRole('textbox', {name: /^Primer apellido/}), 'APELLIDO');
}
async function minimal() {
  await identity();await userEvent.selectOptions(screen.getByRole('combobox', {name: /^Región/}), '13');
  await userEvent.selectOptions(screen.getByRole('combobox', {name: /¿Cómo podemos contactarte/}), 'unsafe');
}
describe('Recorrido NNA del mockup', () => {
  it('sin RUN ni CU completa dos pasos, conserva mínimos y admite contacto no seguro', async () => {
    const {review, step} = mount();await minimal();
    expect(identifyPerson).toHaveBeenCalledWith(journey.id, 'presentador-nna', 'PRESENTADOR');
    await userEvent.click(screen.getByRole('button', {name: 'Continuar'}));
    expect(await screen.findByRole('heading', {name: '¿Qué quieres contarnos?'})).toBeTruthy();
    expect(step).toHaveBeenCalledWith(2);
    await userEvent.type(screen.getByRole('textbox', {name: /^Cuéntanos qué pasó/}), 'Relato sintético para revisar.');
    await userEvent.click(screen.getByRole('button', {name: 'Revisar mi comunicación'}));
    await waitFor(() => expect(review).toHaveBeenCalledTimes(1));
    expect(review.mock.calls[0][0]).toEqual({'nna-region': '13', 'nna-primary-method': 'unsafe', 'nna-story': 'Relato sintético para revisar.'});
    expect(review.mock.calls[0][1].autenticada).toBe(false);expect(review.mock.calls[0][2]).toEqual([]);
  });
  it('no avanza si falta región y explica los mínimos de identificación', async () => {
    const {review, step} = mount();await identity();
    await userEvent.selectOptions(screen.getByRole('combobox', {name: /¿Cómo podemos contactarte/}), 'unsafe');
    await userEvent.click(screen.getByRole('button', {name: 'Continuar'}));
    expect(screen.getAllByText('Completa Región.').length).toBeGreaterThan(0);expect(step).not.toHaveBeenCalled();expect(review).not.toHaveBeenCalled();
    expect(screen.getByText(/necesitamos tu primer nombre, primer apellido y región/)).toBeTruthy();
  });
  it('la comuna es opcional, depende de región y se limpia al cambiarla', async () => {
    mount();await identity();const commune = screen.getByRole('combobox', {name: /^Comuna/}) as HTMLSelectElement;
    expect(commune.disabled).toBe(true);await userEvent.selectOptions(screen.getByRole('combobox', {name: /^Región/}), '13');
    await userEvent.selectOptions(commune, 'Santiago');expect(commune.value).toBe('Santiago');
    await userEvent.selectOptions(screen.getByRole('combobox', {name: /^Región/}), '15');expect(commune.value).toBe('');
    expect(screen.queryByRole('option', {name: 'Santiago'})).toBeNull();expect(screen.getByRole('option', {name: 'Arica'})).toBeTruthy();
  });
  it('pregunta solo por el canal elegido y excluye del resumen los datos de canales inactivos', async () => {
    const {review} = mount(true);await identity();await userEvent.selectOptions(screen.getByRole('combobox', {name: /^Región/}), '13');
    await userEvent.selectOptions(screen.getByRole('combobox', {name: /¿Cómo podemos contactarte/}), 'email');
    await userEvent.type(screen.getByRole('textbox', {name: 'Correo electrónico'}), 'nna@example.invalid');
    await userEvent.selectOptions(screen.getByRole('combobox', {name: /¿Cómo podemos contactarte/}), 'unsafe');
    await userEvent.type(screen.getByRole('textbox', {name: /^Cuéntanos qué pasó/}), 'Relato sintético.');
    await userEvent.click(screen.getByRole('button', {name: 'Revisar mi comunicación'}));
    await waitFor(() => expect(review).toHaveBeenCalledTimes(1));expect(review.mock.calls[0][0]).not.toHaveProperty('nna-primary-email');
    expect(screen.queryByRole('button', {name: 'Continuar'})).toBeNull();expect(screen.queryByRole('button', {name: 'Volver a mis datos'})).toBeNull();
  });
  it('contacto social alternativo valida campos condicionales y conserva canal secundario opcional', async () => {
    const {review} = mount(true);await minimal();
    await userEvent.click(screen.getByRole('checkbox', {name: /Quiero agregar otra forma/}));
    await userEvent.selectOptions(screen.getByRole('combobox', {name: 'Otra forma de contacto'}), 'social');
    await userEvent.selectOptions(screen.getByRole('combobox', {name: '¿Qué red social utilizas?'}), 'otra');
    await userEvent.type(screen.getByRole('textbox', {name: /^Cuéntanos qué pasó/}), 'Relato sintético.');
    await userEvent.click(screen.getByRole('button', {name: 'Revisar mi comunicación'}));expect(review).not.toHaveBeenCalled();
    await userEvent.type(screen.getByRole('textbox', {name: '¿Cuál red social?'}), 'Red de prueba');
    await userEvent.type(screen.getByRole('textbox', {name: 'Tu usuario o enlace de perfil'}), '@persona-sintetica');
    await userEvent.click(screen.getByRole('button', {name: 'Revisar mi comunicación'}));
    await waitFor(() => expect(review).toHaveBeenCalledTimes(1));expect(review.mock.calls[0][0]['nna-secondary-other-network']).toBe('Red de prueba');
  });
  it('un archivo rechazado no se presenta como recibido ni permite revisar hasta retirarlo', async () => {
    const port = {replace: vi.fn().mockRejectedValue(new Error('Rechazo controlado')), clear: vi.fn().mockResolvedValue(undefined)};
    const {review} = mount(true, port);await minimal();await userEvent.type(screen.getByRole('textbox', {name: /^Cuéntanos qué pasó/}), 'Relato sintético.');
    const input = document.getElementById('nna-files') as HTMLInputElement;
    await userEvent.upload(input, new File(['contenido sintético'], 'prueba.pdf', {type: 'application/pdf'}));
    expect((await screen.findAllByText('No pudimos recibir estos archivos. Revisa su formato o vuelve a intentarlo.')).length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', {name: 'Revisar mi comunicación'}));expect(review).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', {name: 'Quitar archivos seleccionados'}));
    await waitFor(() => expect(port.clear).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('button', {name: 'Revisar mi comunicación'}));
    await waitFor(() => expect(review).toHaveBeenCalledTimes(1));expect(review.mock.calls[0][2]).toEqual([]);
  });
  it('escribe celular con prefijo fijo y revisa contactos normalizados sin exigir un canal inseguro',async()=>{
    const {review}=mount(true);await identity();await userEvent.selectOptions(screen.getByRole('combobox',{name:/^Región/}),'13');await userEvent.selectOptions(screen.getByRole('combobox',{name:/¿Cómo podemos contactarte/}),'celular');
    const phone=screen.getByRole('textbox',{name:/^Número de celular/}) as HTMLInputElement;await userEvent.type(phone,'12345678');expect(phone.value).toBe('12345678');await userEvent.click(screen.getByRole('checkbox',{name:/Quiero agregar otra forma/}));await userEvent.selectOptions(screen.getByRole('combobox',{name:'Otra forma de contacto'}),'email');await userEvent.type(screen.getByRole('textbox',{name:'Correo electrónico'}),'Usuario+Alias@sub.correo.algo');await userEvent.type(screen.getByRole('textbox',{name:/^Cuéntanos qué pasó/}),'Relato sintético');await userEvent.click(screen.getByRole('button',{name:'Revisar mi comunicación'}));await waitFor(()=>expect(review).toHaveBeenCalledOnce());expect(review.mock.calls[0][0]).toMatchObject({'nna-primary-celular':'+56912345678','nna-secondary-email':'Usuario+Alias@sub.correo.algo'});
  });

});
