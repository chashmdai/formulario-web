import { createRef } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
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
  it('la corrección abre campos vacíos y envía solo declaraciones sin reconsultar', async () => {
    vi.mocked(identifyPerson).mockResolvedValue({...empty(), camposFaltantes: [], camposObligatorios: []});
    mount();await run();await userEvent.click(screen.getByRole('button', {name: 'Revisar RUN'}));
    await userEvent.click(await screen.findByRole('button', {name:'Aportar datos distintos o faltantes'}));
    const name=screen.getByRole('textbox', {name:/^Primer nombre/}) as HTMLInputElement;
    expect(name.value).toBe('');
    expect((screen.getByRole('textbox',{name:/^Primer apellido/}) as HTMLInputElement).value).toBe('');
    await userEvent.type(name,'CORRECCION DECLARADA');
    await userEvent.click(screen.getByRole('button',{name:'Guardar identificación'}));
    await waitFor(()=>expect(declarePerson).toHaveBeenCalledWith(journey.id,'presentador-adulto',{primerNombre:'CORRECCION DECLARADA'},true));
    expect(identifyPerson).toHaveBeenCalledTimes(1);
  });
  it.each(['VACIO','ERROR'])('el fallback %s solicita datos sin afirmar existencia', async () => {
    vi.mocked(identifyPerson).mockResolvedValue(empty());
    mount();await run();await userEvent.click(screen.getByRole('button',{name:'Revisar RUN'}));
    expect(await screen.findByRole('textbox',{name:/^Primer nombre/})).toBeTruthy();
    expect(screen.getByRole('textbox',{name:/^Primer apellido/})).toBeTruthy();
    expect(screen.queryByText(/Los antecedentes obtenidos/)).toBeNull();
  });
  it('el fallback manual no omite nombres obligatorios ni acepta texto de marcado como nombre', async () => {
    mount();await run();await userEvent.click(screen.getByRole('button', {name: 'Revisar RUN'}));await screen.findByRole('textbox', {name: /^Primer nombre/});
    await userEvent.click(screen.getByRole('button', {name: 'Guardar identificación'}));expect(declarePerson).not.toHaveBeenCalled();
    expect(screen.getByText('Completa Primer nombre.')).toBeTruthy();
    await userEvent.type(screen.getByRole('textbox', {name: /^Primer nombre/}), '<script>');
    await userEvent.type(screen.getByRole('textbox', {name: /^Primer apellido/}), 'PRUEBA');
    await userEvent.click(screen.getByRole('button', {name: 'Guardar identificación'}));expect(declarePerson).not.toHaveBeenCalled();
    expect(screen.getByText("Usa solo letras, espacios, guion (-), apóstrofo (') o punto.")).toBeTruthy();
  });
  it('advierte al salir del campo, sin esperar a guardar, y retira el aviso al corregir', async () => {
    mount();await run();await userEvent.click(screen.getByRole('button', {name: 'Revisar RUN'}));
    const name = await screen.findByRole('textbox', {name: /^Primer nombre/});
    await userEvent.type(name, 'Ana2');await userEvent.tab();
    expect(screen.getByText('No incluyas números.')).toBeTruthy();expect(name.getAttribute('aria-invalid')).toBe('true');
    await userEvent.clear(name);await userEvent.type(name, 'J');await userEvent.tab();
    expect(screen.getByText('Escribe el nombre completo, no solo la inicial.')).toBeTruthy();
    await userEvent.type(name, 'uana');
    expect(screen.queryByText('Escribe el nombre completo, no solo la inicial.')).toBeNull();expect(name.getAttribute('aria-invalid')).toBe('false');
    expect(declarePerson).not.toHaveBeenCalled();
  });
  it('avisa números y símbolos al escribir o pegar sin bloquear la escritura, y espera a salir para la forma', async () => {
    mount();await run();await userEvent.click(screen.getByRole('button', {name: 'Revisar RUN'}));
    const name = await screen.findByRole('textbox', {name: /^Primer nombre/});
    await userEvent.type(name, 'Ana2');expect((name as HTMLInputElement).value).toBe('Ana2');expect(screen.getByText('No incluyas números.')).toBeTruthy();
    await userEvent.type(name, '{backspace}');expect(screen.queryByText('No incluyas números.')).toBeNull();
    await userEvent.clear(name);await userEvent.click(name);await userEvent.paste('María@José');
    expect((name as HTMLInputElement).value).toBe('María@José');expect(screen.getByText(/^Usa solo letras/)).toBeTruthy();
    await userEvent.clear(name);await userEvent.type(name, 'Jean-');expect(name.getAttribute('aria-invalid')).toBe('false');
    await userEvent.type(name, 'Pierre');await userEvent.tab();expect(name.getAttribute('aria-invalid')).toBe('false');
    await userEvent.clear(name);await userEvent.click(name);await userEvent.paste("D\u2019Ángelo");await userEvent.tab();expect((name as HTMLInputElement).value).toBe("D'Ángelo");
    expect(name.getAttribute('aria-invalid')).toBe('false');
  });
  it('normaliza espacios y apóstrofos tipográficos antes de enviar nombres legítimos', async () => {
    mount();await run();await userEvent.click(screen.getByRole('button', {name: 'Revisar RUN'}));
    await userEvent.type(await screen.findByRole('textbox', {name: /^Primer nombre/}), '  María   José ');
    await userEvent.type(screen.getByRole('textbox', {name: /^Primer apellido/}), 'O’Higgins');
    await userEvent.click(screen.getByRole('button', {name: 'Guardar identificación'}));
    await waitFor(() => expect(declarePerson).toHaveBeenCalledWith(journey.id, 'presentador-adulto', expect.objectContaining({primerNombre: 'María José', primerApellido: "O'Higgins"})));
  });
  it('explica un rechazo de formato del servidor en lugar de un error genérico', async () => {
    vi.mocked(declarePerson).mockRejectedValue(new ApiError(400));
    mount();await run();await userEvent.click(screen.getByRole('button', {name: 'Revisar RUN'}));
    await userEvent.type(await screen.findByRole('textbox', {name: /^Primer nombre/}), 'Ana');
    await userEvent.type(screen.getByRole('textbox', {name: /^Primer apellido/}), 'Rojas');
    await userEvent.click(screen.getByRole('button', {name: 'Guardar identificación'}));
    expect(await screen.findByText(/Algunos datos no tienen un formato válido/)).toBeTruthy();
  });
  it('valida el documento extranjero y la fecha antes de enviarlos', async () => {
    const foreign:IdentityView={...empty(),runInformado:false,camposFaltantes:['primerNombre','primerApellido','fechaNacimiento','nacionalidad','tipoDocumento','numeroDocumento'],camposObligatorios:['primerNombre','primerApellido','nacionalidad','tipoDocumento','numeroDocumento'],valoresDeclarados:{primerNombre:'DECLARADO',primerApellido:'APELLIDO',nacionalidad:'Perú',tipoDocumento:'PASAPORTE',numeroDocumento:'x'}};
    vi.mocked(getPerson).mockResolvedValue(foreign);mount();
    await screen.findByDisplayValue('x');
    fireEvent.change(screen.getByLabelText(/^Fecha de nacimiento/), {target: {value: '2999-01-01'}});
    await userEvent.click(screen.getByRole('button',{name:'Guardar identificación'}));
    expect(screen.getByText(/Escribe el número completo/)).toBeTruthy();expect(screen.getByText(/no puede ser posterior a hoy/)).toBeTruthy();
    expect(declarePerson).not.toHaveBeenCalled();
  });
  it('en el recorrido NNA conserva la regla anterior sobre iniciales', async () => {
    vi.mocked(getPerson).mockResolvedValue({...empty(), personaId:'presentador-nna', runInformado:false, camposFaltantes:['primerNombre','primerApellido'], camposObligatorios:['primerNombre','primerApellido'], valoresDeclarados:{}});
    mount({nna: true});
    await userEvent.type(await screen.findByRole('textbox', {name: /^Primer nombre/}), 'J');
    await userEvent.type(screen.getByRole('textbox', {name: /^Primer apellido/}), 'Pérez');
    await userEvent.click(screen.getByRole('button', {name: 'Guardar identificación'}));
    await waitFor(() => expect(declarePerson).toHaveBeenCalledWith(journey.id, 'presentador-nna', expect.objectContaining({primerNombre: 'J'})));
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
    const verified = await screen.findByRole('region', {name: 'Datos verificados con ClaveÚnica'});
    // Valores tal como llegan de la sesión, como texto estático: sin campos editables ni de solo lectura.
    expect(within(verified).getByText('NOMBRE')).toBeTruthy();expect(within(verified).getByText('CONTROLADO', {selector: '#adult-middle'})).toBeTruthy();
    expect(within(verified).getByText('12.345.678-5')).toBeTruthy();expect(within(verified).queryAllByRole('textbox')).toHaveLength(0);
    expect(screen.queryByRole('textbox', {name: /^Primer nombre/})).toBeNull();expect(screen.queryByRole('textbox', {name: /^RUN/})).toBeNull();
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
