import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, describe, it, expect } from 'vitest';
import App from './App';
import * as api from './api';

vi.mock('./api', async (original) => ({ ...await original<typeof api>(), getSession: vi.fn(), getCsrf: vi.fn(), previewCommunication: vi.fn() }));
const identity = { run: '44444444-4', nombres: 'Ciudadana Prueba', apellidos: 'Sandbox' };
const csrf = { token: 'test-csrf', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' };
describe('Ingreso y Comunicación', () => {
  it('una nueva carga adulta conserva la identidad de sesión pero empieza el mensaje vacío', async () => {
    vi.mocked(api.getSession).mockResolvedValue({ authenticated: true, identity, environment: 'SANDBOX', loginAvailable: true });
    vi.mocked(api.getCsrf).mockResolvedValue(csrf);
    const first = render(<App />); await screen.findByLabelText('Mensaje');
    await userEvent.setup().type(screen.getByLabelText('Mensaje'), 'Borrador sintético');
    first.unmount(); render(<App />);
    expect((await screen.findByLabelText('Mensaje') as HTMLTextAreaElement).value).toBe('');
    expect((screen.getByLabelText('RUN') as HTMLInputElement).value).toBe(identity.run);
    expect(screen.queryByText(/Identificador técnico de recepción/)).toBeNull();
  });
  it('muestra RUN y DV de origen y solo dos componentes sin alterar la identidad completa', async () => {
    const structured = { ...identity, runNumero: '44444444', runDv: '4', nombresComponentes: ['  María José  ', 'De los ángeles', 'Tercero', 'Cuarto'], apellidosComponentes: ['Del Río', 'González de la Fuente', 'Tercero'] };
    vi.mocked(api.getSession).mockResolvedValue({ authenticated: true, identity: structured, environment: 'SANDBOX', loginAvailable: true });
    vi.mocked(api.getCsrf).mockResolvedValue(csrf);
    render(<App />);
    await screen.findByLabelText('Primer nombre');
    for (const [label, value] of [['Número de RUN', '44444444'], ['Dígito verificador', '4'], ['Primer nombre', '  María José  '], ['Segundo nombre', 'De los ángeles'], ['Primer apellido', 'Del Río'], ['Segundo apellido', 'González de la Fuente']]) {
      const field = screen.getByLabelText(label) as HTMLInputElement;
      expect(field.value).toBe(value); expect(field.readOnly).toBe(true);
    }
    expect(screen.queryByLabelText('Nombres')).toBeNull();
    expect(screen.queryByLabelText('Tercer nombre')).toBeNull(); expect(screen.queryByLabelText('Nombre 4')).toBeNull(); expect(screen.queryByLabelText('Apellido 3')).toBeNull();
    expect(structured.nombresComponentes).toEqual(['  María José  ', 'De los ángeles', 'Tercero', 'Cuarto']);
    expect(structured.apellidosComponentes).toHaveLength(3);
  });
  it('no fabrica segundos o terceros elementos cuando no existen', async () => {
    vi.mocked(api.getSession).mockResolvedValue({ authenticated: true, identity: { ...identity, runNumero: '44444444', runDv: '4', nombresComponentes: ['María José'], apellidosComponentes: ['Del Río'] }, environment: 'SANDBOX', loginAvailable: true });
    vi.mocked(api.getCsrf).mockResolvedValue(csrf);
    render(<App />);
    expect((await screen.findByLabelText('Primer nombre') as HTMLInputElement).value).toBe('María José');
    expect(screen.queryByLabelText('Segundo nombre')).toBeNull(); expect(screen.queryByLabelText('Tercer nombre')).toBeNull(); expect(screen.queryByLabelText('Segundo apellido')).toBeNull();
  });
  it('abre ClaveÚnica en la misma página y no pide contraseña', async () => {
    vi.mocked(api.getSession).mockResolvedValue({ authenticated: false, identity: null, environment: 'SANDBOX', loginAvailable: false });
    vi.mocked(api.getCsrf).mockResolvedValue(csrf);
    render(<App />);
    const link = await screen.findByRole('link', { name: 'Iniciar sesión con ClaveÚnica' });
    expect(link.getAttribute('href')).toBe('/oauth2/authorization/claveunica');
    expect(link.getAttribute('target')).toBeNull();
    expect(document.querySelector('input[type=password]')).toBeNull();
    expect(await screen.findByText(/pendiente de habilitación/)).toBeTruthy();
  });
  it('bloquea identidad y solo envía el mensaje a la vista previa', async () => {
    vi.mocked(api.getSession).mockResolvedValue({ authenticated: true, identity, environment: 'SANDBOX', loginAvailable: true });
    vi.mocked(api.getCsrf).mockResolvedValue(csrf);
    vi.mocked(api.previewCommunication).mockResolvedValue({ identidad: identity, mensaje: 'Mi mensaje', almacenada: false });
    render(<App />);
    const run = await screen.findByLabelText('RUN') as HTMLInputElement;
    expect(run.readOnly).toBe(true); expect(run.value).toBe(identity.run);
    expect((screen.getByLabelText('Nombres') as HTMLInputElement).readOnly).toBe(true);
    expect((screen.getByLabelText('Apellidos') as HTMLInputElement).readOnly).toBe(true);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Mensaje'), 'Mi mensaje');
    await user.click(screen.getByRole('button', { name: 'Revisar Comunicación' }));
    await waitFor(() => expect(api.previewCommunication).toHaveBeenCalledWith('Mi mensaje'));
    expect(await screen.findByText('Vista previa preparada. Este paso no envía ni almacena la Comunicación.')).toBeTruthy();
    const form = screen.getByRole('button', { name: 'Cerrar sesión' }).closest('form');
    expect(form?.getAttribute('method')).toBe('post'); expect(form?.getAttribute('action')).toBe('/auth/logout');
    expect((form?.querySelector('input') as HTMLInputElement).value).toBe(csrf.token);
  });
  it('informa fallos de conexión sin inventar identidad', async () => {
    vi.mocked(api.getSession).mockRejectedValue(new Error('offline'));
    vi.mocked(api.getCsrf).mockResolvedValue(csrf);
    render(<App />);
    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(screen.queryByLabelText('RUN')).toBeNull();
  });
});
