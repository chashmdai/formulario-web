import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NnaForm } from './NnaForm';
import * as api from './api';
import { validRun } from './fields';
vi.mock('./api', () => ({ getNnaCapabilities: vi.fn(), nnaAdapter: vi.fn() }));
describe('NNA provisional', () => {
  beforeEach(() => {
    vi.mocked(api.getNnaCapabilities).mockResolvedValue({ recepcionDisponible: true, avisoVersion: 'lab', aviso: 'Solo pruebas' });
    vi.mocked(api.nnaAdapter).mockReturnValue({ capabilities: api.getNnaCapabilities, get: vi.fn(), send: vi.fn().mockResolvedValue({ id: 'sintetico', estado: 'PENDIENTE_REGISTRO_SIGER', confirmacion: null }), receiptUrl: () => '/test/comprobante' });
  });
  it('valida módulo 11 con DV separado y permite omitir ambos', () => {
    expect(validRun(null, null)).toBe(true); expect(validRun('44444444', '4')).toBe(true);
    expect(validRun('44444444', '5')).toBe(false); expect(validRun('44444444', null)).toBe(false);
    expect(validRun(null, '4')).toBe(false);
  });
  it('sin RUN prepara y envía datos declarados; una nueva carga empieza vacía', async () => {
    const user = userEvent.setup(); const first = render(<NnaForm />);
    await screen.findByRole('button', { name: 'Revisar mis respuestas' });
    await user.type(screen.getByLabelText('Primer nombre'), 'María José');
    await user.type(screen.getByLabelText('Primer apellido'), 'Del Río');
    await user.type(screen.getByLabelText('Región'), 'Región sintética');
    await user.type(screen.getByLabelText('Cuéntanos'), 'Relato sintético');
    await user.click(screen.getByRole('button', { name: 'Revisar mis respuestas' }));
    await user.click(await screen.findByRole('checkbox')); await user.click(screen.getByRole('button', { name: 'Enviar comunicación' }));
    expect(await screen.findByText(/registro institucional está pendiente/)).toBeTruthy();
    expect(vi.mocked(api.nnaAdapter).mock.calls.at(-1)?.[0]).toMatchObject({ primerNombre: 'María José', primerApellido: 'Del Río', runNumero: null, runDv: null });
    first.unmount(); render(<NnaForm />); await screen.findByRole('button', { name: 'Revisar mis respuestas' });
    expect((screen.getByLabelText('Primer nombre') as HTMLInputElement).value).toBe('');
    expect((screen.getByLabelText('Cuéntanos') as HTMLTextAreaElement).value).toBe('');
    expect(screen.queryByText(/Referencia de recepción/)).toBeNull();
  });
  it('no muestra campos de ingreso si está cerrado', async () => {
    vi.mocked(api.getNnaCapabilities).mockResolvedValue({ recepcionDisponible: false, avisoVersion: '', aviso: '' });
    render(<NnaForm />); expect(await screen.findByText('El ingreso NNA está deshabilitado.')).toBeTruthy();
    expect(screen.queryByLabelText('Primer nombre')).toBeNull();
  });
});
