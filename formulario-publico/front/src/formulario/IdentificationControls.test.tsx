import { createRef } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { IdentificationControls } from './IdentificationControls';
import type { IdentificationHandle } from './IdentificationControls';
import type { IdentityView, Journey } from './api';
import { ApiError } from '../api';
import { declarePerson, getPerson, identifyPerson, identifyForeignPerson, resetPerson } from './api';
vi.mock('./api', () => ({getPerson: vi.fn(), identifyPerson: vi.fn(), identifyForeignPerson: vi.fn(), declarePerson: vi.fn(), resetPerson: vi.fn()}));
const journey: Journey = {id: 'recorrido-sintetico', tipo: 'ADULTO_DECLARADO', avisoVersion: 'LAB_1', aceptaCanal: true, estado: 'BORRADOR', venceEn: '2026-10-24T00:00:00Z'};
const empty = (): IdentityView => ({personaId: 'presentador-adulto', estado: 'REVISADO', autenticada: false, runInformado: true,
  camposFaltantes: ['primerNombre', 'segundoNombre', 'primerApellido', 'segundoApellido', 'fechaNacimiento', 'sexo'], camposObligatorios: ['primerNombre', 'primerApellido'], valoresDeclarados: {}, mensaje: 'Completa los datos solicitados.'});
function mount(props: Partial<Parameters<typeof IdentificationControls>[0]> = {}) {
  const identityRef = createRef<IdentificationHandle>();
  render(<><IdentificationControls journey={journey} session={null} identityRef={identityRef} {...props} /><button onClick={() => void identityRef.current?.save()}>Guardar identificación</button></>);
  return identityRef;
}
beforeEach(() => {
  vi.resetAllMocks();vi.mocked(getPerson).mockRejectedValue(new ApiError(404));vi.mocked(identifyPerson).mockResolvedValue(empty());vi.mocked(resetPerson).mockResolvedValue(undefined);
  vi.mocked(declarePerson).mockImplementation(async (_j, _p, values) => ({...empty(), valoresDeclarados: values}));
});
afterEach(() => cleanup());
async function run() { await userEvent.type(screen.getByRole('textbox', {name: /^RUN/}), '12345678');await userEvent.type(screen.getByRole('textbox', {name: 'Dígito verificador del RUN'}), '5'); }
describe('Identificación ciudadana conectada al servidor', () => {
  it('restaura una identificación con RUN sin revelarlo ni repetir consulta al recargar', async () => {
    vi.mocked(getPerson).mockResolvedValue({...empty(), personaId:'presentador-nna', camposFaltantes:['primerApellido'], camposObligatorios:['primerApellido'], valoresDeclarados:{primerApellido:'DECLARADO'}});
    mount({journey:{...journey,tipo:'NNA'},nna:true});
    expect(await screen.findByDisplayValue('DECLARADO')).toBeTruthy();expect(screen.getByText(/Ya informaste un RUN/)).toBeTruthy();
    expect(identifyPerson).not.toHaveBeenCalled();expect(screen.queryByRole('textbox',{name:/^RUN/})).toBeNull();
    await userEvent.click(screen.getByRole('button',{name:'Guardar identificación'}));await waitFor(()=>expect(declarePerson).toHaveBeenCalledWith(journey.id,'presentador-nna',{primerApellido:'DECLARADO'}));
  });
  it('pegar un RUN completo reparte número y DV como en el mockup', async () => {
    mount();const input=screen.getByRole('textbox',{name:/^RUN/});
    fireEvent.paste(input,{clipboardData:{getData:()=> '12.345.678-5'}});
    expect((input as HTMLInputElement).value).toBe('12.345.678');expect((screen.getByRole('textbox',{name:'Dígito verificador del RUN'}) as HTMLInputElement).value).toBe('5');
    await userEvent.click(screen.getByRole('button',{name:'Revisar RUN'}));expect(identifyPerson).toHaveBeenCalledWith(journey.id,'presentador-adulto','PRESENTADOR','12345678','5');
  });
  it('requiere módulo 11 antes de consultar y permite corregir un DV inválido', async () => {
    mount();await userEvent.click(screen.getByRole('button', {name: 'Revisar RUN'}));
    expect(identifyPerson).not.toHaveBeenCalled();expect(screen.getByText('El número y el dígito verificador no coinciden.')).toBeTruthy();
    await run();await userEvent.click(screen.getByRole('button', {name: 'Revisar RUN'}));
    expect(identifyPerson).toHaveBeenCalledWith(journey.id, 'presentador-adulto', 'PRESENTADOR', '12345678', '5');
    expect(await screen.findByRole('textbox', {name: /^Primer nombre/})).toBeTruthy();
  });
  it('un resultado completo no muestra ni solicita atributos recuperados', async () => {
    vi.mocked(identifyPerson).mockResolvedValue({...empty(), camposFaltantes: [], camposObligatorios: []});
    mount();await run();await userEvent.click(screen.getByRole('button', {name: 'Revisar RUN'}));
    await waitFor(() => expect(screen.queryByRole('textbox', {name: /Primer nombre/})).toBeNull());
    expect(screen.queryByRole('textbox', {name: /Primer apellido/})).toBeNull();
    await userEvent.click(screen.getByRole('button', {name: 'Guardar identificación'}));
    await waitFor(() => expect(declarePerson).toHaveBeenCalledWith(journey.id, 'presentador-adulto', {}));
  });
  it('un resultado parcial solo pide faltantes y guarda la declaración por backend', async () => {
    vi.mocked(identifyPerson).mockResolvedValue({...empty(), camposFaltantes: ['primerApellido'], camposObligatorios: ['primerApellido']});
    mount();await run();await userEvent.click(screen.getByRole('button', {name: 'Revisar RUN'}));
    const surname = await screen.findByRole('textbox', {name: /^Primer apellido/});expect(screen.queryByRole('textbox', {name: /Primer nombre/})).toBeNull();
    await userEvent.type(surname, 'APELLIDO DECLARADO');await userEvent.click(screen.getByRole('button', {name: 'Guardar identificación'}));
    await waitFor(() => expect(declarePerson).toHaveBeenCalledWith(journey.id, 'presentador-adulto', {primerApellido: 'APELLIDO DECLARADO'}));
  });
  it('el fallback manual no omite nombres obligatorios ni acepta texto de marcado como nombre', async () => {
    mount();await run();await userEvent.click(screen.getByRole('button', {name: 'Revisar RUN'}));await screen.findByRole('textbox', {name: /^Primer nombre/});
    await userEvent.click(screen.getByRole('button', {name: 'Guardar identificación'}));expect(declarePerson).not.toHaveBeenCalled();
    expect(screen.getByText('Completa Primer nombre.')).toBeTruthy();
    await userEvent.type(screen.getByRole('textbox', {name: /^Primer nombre/}), '<script>');
    await userEvent.type(screen.getByRole('textbox', {name: /^Primer apellido/}), 'PRUEBA');
    await userEvent.click(screen.getByRole('button', {name: 'Guardar identificación'}));expect(declarePerson).not.toHaveBeenCalled();
    expect(screen.getByText('Escribe solo letras y separadores de nombres.')).toBeTruthy();
  });
  it('cambiar el RUN descarta los campos del anterior y reinicia el registro antes de consultar', async () => {
    mount();await run();await userEvent.click(screen.getByRole('button', {name: 'Revisar RUN'}));
    await userEvent.type(await screen.findByRole('textbox', {name: /^Primer nombre/}), 'DECLARADO ANTERIOR');
    const input = screen.getByRole('textbox', {name: /^RUN/});await userEvent.clear(input);await userEvent.type(input, '11111111');
    const dv = screen.getByRole('textbox', {name: 'Dígito verificador del RUN'});await userEvent.clear(dv);await userEvent.type(dv, '1');
    expect(screen.queryByDisplayValue('DECLARADO ANTERIOR')).toBeNull();
    await userEvent.click(screen.getByRole('button', {name: 'Revisar RUN'}));
    expect(resetPerson).toHaveBeenCalledWith(journey.id, 'presentador-adulto');expect(identifyPerson).toHaveBeenLastCalledWith(journey.id, 'presentador-adulto', 'PRESENTADOR', '11111111', '1');
  });
  it('NNA continúa sin RUN y solicita al backend esa ruta sin identificador', async () => {
    vi.mocked(identifyPerson).mockResolvedValue({...empty(), personaId: 'presentador-nna', runInformado: false, camposFaltantes: ['primerNombre', 'primerApellido', 'edad']});
    mount({journey: {...journey, tipo: 'NNA'}, nna: true});
    expect(await screen.findByRole('textbox', {name: /^Primer nombre/})).toBeTruthy();
    expect(identifyPerson).toHaveBeenCalledWith(journey.id, 'presentador-nna', 'PRESENTADOR');
    expect(screen.queryByRole('textbox', {name: /^RUN/})).toBeNull();
    expect((screen.getByRole('radio', {name: 'No sé mi RUN / continuar sin RUN'}) as HTMLInputElement).checked).toBe(true);
  });
  it('CU muestra únicamente la identidad propia de sesión y deriva el RUN en servidor', async () => {
    vi.mocked(identifyPerson).mockResolvedValue({...empty(), autenticada: true, camposFaltantes: [], camposObligatorios: []});
    mount({journey: {...journey, tipo: 'ADULTO_CU'}, session: {authenticated: true, environment: 'SANDBOX', loginAvailable: true,
      identity: {run: '12345678-5', nombres: 'NOMBRE CONTROLADO', apellidos: 'APELLIDO CONTROLADO', nombresComponentes: ['NOMBRE', 'CONTROLADO'], apellidosComponentes: ['APELLIDO', 'CONTROLADO']}}});
    const name = await screen.findByDisplayValue('NOMBRE');expect((name as HTMLInputElement).readOnly).toBe(true);
    expect(identifyPerson).toHaveBeenCalledWith(journey.id, 'presentador-adulto', 'PRESENTADOR');
    expect(screen.queryByLabelText('Fecha de nacimiento')).toBeNull();expect(screen.queryByRole('button', {name: 'Revisar RUN'})).toBeNull();
  });
  it('una declaración tardía no actualiza otro recorrido después de desmontar el panel', async () => {
    const resolved = vi.fn();let complete!: (value: IdentityView) => void;
    vi.mocked(declarePerson).mockImplementation(() => new Promise(resolve => { complete = resolve; }));
    mount({onResolved: resolved});await run();await userEvent.click(screen.getByRole('button', {name: 'Revisar RUN'}));
    await userEvent.type(await screen.findByRole('textbox', {name: /^Primer nombre/}), 'NOMBRE');
    await userEvent.type(screen.getByRole('textbox', {name: /^Primer apellido/}), 'APELLIDO');
    await userEvent.click(screen.getByRole('button', {name: 'Guardar identificación'}));
    const before = resolved.mock.calls.length;cleanup();
    await act(async () => { complete({...empty(), valoresDeclarados: {primerNombre: 'NOMBRE', primerApellido: 'APELLIDO'}}); });
    expect(resolved).toHaveBeenCalledTimes(before);
  });
  it('restaura solo las declaraciones del documento extranjero sin consultar ni pedir RUN',async()=>{
    const foreign:IdentityView={...empty(),runInformado:false,camposFaltantes:['primerNombre','primerApellido','nacionalidad','tipoDocumento','numeroDocumento'],camposObligatorios:['primerNombre','primerApellido','nacionalidad','tipoDocumento','numeroDocumento'],valoresDeclarados:{primerNombre:'DECLARADO',primerApellido:'APELLIDO',nacionalidad:'SINTÉTICA',tipoDocumento:'PASAPORTE',numeroDocumento:'AB/123-xyz'}};
    vi.mocked(getPerson).mockResolvedValue(foreign);mount();
    expect(await screen.findByDisplayValue('AB/123-xyz')).toBeTruthy();expect(screen.queryByRole('textbox',{name:/^RUN/})).toBeNull();
    expect(identifyPerson).not.toHaveBeenCalled();expect(identifyForeignPerson).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('radio',{name:'Tengo RUN chileno'}));
    await waitFor(()=>expect(resetPerson).toHaveBeenCalledWith(journey.id,'presentador-adulto'));
    expect(screen.queryByDisplayValue('AB/123-xyz')).toBeNull();
    await run();await userEvent.click(screen.getByRole('button',{name:'Revisar RUN'}));expect(identifyPerson).toHaveBeenCalledWith(journey.id,'presentador-adulto','PRESENTADOR','12345678','5');
  });

  it('guarda una nacionalidad histórica solo al confirmar la opción excepcional visible',async()=>{
    const foreign:IdentityView={...empty(),runInformado:false,camposFaltantes:['primerNombre','primerApellido','nacionalidad','tipoDocumento','numeroDocumento'],camposObligatorios:['primerNombre','primerApellido','nacionalidad','tipoDocumento','numeroDocumento'],valoresDeclarados:{primerNombre:'DECLARADO',primerApellido:'APELLIDO',nacionalidad:'Venezolana',tipoDocumento:'PASAPORTE',numeroDocumento:'AB/123-xyz'}};
    vi.mocked(getPerson).mockResolvedValue(foreign);mount();
    const other=await screen.findByRole('textbox',{name:/Indica tu nacionalidad/});expect((other as HTMLInputElement).value).toBe('Venezolana');
    await userEvent.click(screen.getByRole('button',{name:'Guardar identificación'}));
    await waitFor(()=>expect(declarePerson).toHaveBeenCalledWith(journey.id,'presentador-adulto',expect.objectContaining({nacionalidad:'Otra / situación especial: Venezolana'})));
  });
  it('no guarda una excepción sin detalle',async()=>{
    const foreign:IdentityView={...empty(),runInformado:false,camposFaltantes:['primerNombre','primerApellido','nacionalidad'],camposObligatorios:['primerNombre','primerApellido','nacionalidad'],valoresDeclarados:{primerNombre:'DECLARADO',primerApellido:'APELLIDO'}};
    vi.mocked(getPerson).mockResolvedValue(foreign);mount();
    await userEvent.selectOptions(await screen.findByRole('combobox',{name:/^Nacionalidad/}),'__OTRA__');
    await userEvent.click(screen.getByRole('button',{name:'Guardar identificación'}));
    expect(screen.getByText('Selecciona un país o completa Otra / situación especial.')).toBeTruthy();expect(declarePerson).not.toHaveBeenCalled();
  });

});
