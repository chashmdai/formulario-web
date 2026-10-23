import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import StartPage from './StartPage';

beforeEach(() => {
  localStorage.clear();
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
});
afterEach(() => { cleanup(); localStorage.clear(); });
describe('Inicio institucional en migración', () => {
  it('conserva selección de públicos y un botón CU hacia el backend real', async () => {
    render(<StartPage />);
    expect(screen.getByRole('heading', {name: 'Estamos aquí para escucharte.'})).toBeTruthy();
    expect(screen.getByRole('button', {name: /Soy niño, niña o adolescente/})).toBeTruthy();
    await userEvent.click(screen.getByRole('button', {name: /Soy una persona adulta/}));
    expect(screen.getByRole('dialog')).toBeTruthy();
    expect(screen.getByRole('link', {name: 'Iniciar sesión con ClaveÚnica'}).getAttribute('href')).toBe('/oauth2/authorization/claveunica');
    expect(screen.queryByText(/USUARIO DEMO/)).toBeNull();
  });
  it('no fabrica un ingreso adulto cuando esa ruta todavía no está implementada', async () => {
    const fetch = vi.spyOn(window, 'fetch');
    render(<StartPage />);
    await userEvent.click(screen.getByRole('button', {name: /Soy una persona adulta/}));
    await userEvent.click(screen.getByRole('button', {name: /No tengo ClaveÚnica/}));
    expect(screen.getByRole('heading', {name: 'Antes de continuar, conoce cómo cuidaremos tus datos'})).toBeTruthy();
    expect(screen.getByRole('button', {name: 'Continuar'}).hasAttribute('disabled')).toBe(true);
    await userEvent.click(screen.getByRole('radio', {name: /^Acepto/}));
    await userEvent.click(screen.getByRole('button', {name: 'Continuar'}));
    expect(screen.getByText('Este acceso todavía no está disponible. Puedes volver al inicio.')).toBeTruthy();
    expect(fetch).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', {name: 'Cerrar y volver al inicio'}));
    expect(screen.queryByRole('dialog')).toBeNull();
  });
  it('ofrece otros canales al rechazar, permite cambiar respuesta y reinicia la elección al volver', async () => {
    const fetch = vi.spyOn(window, 'fetch');
    render(<StartPage />);
    await userEvent.click(screen.getByRole('button', {name: /Soy una persona adulta/}));
    await userEvent.click(screen.getByRole('link', {name: 'Iniciar sesión con ClaveÚnica'}));
    await userEvent.click(screen.getByRole('radio', {name: /^No acepto/}));
    await userEvent.click(screen.getByRole('button', {name: 'Continuar'}));
    expect(screen.getByRole('heading', {name: 'Otras formas de comunicarte'})).toBeTruthy();
    expect(screen.getByRole('link', {name: /Atención de usuarios/}).getAttribute('href')).toBe('tel:+56224979602');
    expect(document.activeElement).toBe(screen.getByRole('button', {name: 'Volver al inicio'}));
    await userEvent.click(screen.getByRole('button', {name: 'Cambiar mi respuesta'}));
    expect(screen.getByRole('button', {name: 'Continuar'}).hasAttribute('disabled')).toBe(true);
    await userEvent.click(screen.getByRole('button', {name: 'Volver'}));
    await userEvent.click(screen.getByRole('button', {name: /Soy una persona adulta/}));
    await userEvent.click(screen.getByRole('button', {name: /No tengo ClaveÚnica/}));
    expect((screen.getByRole('radio', {name: /^Acepto/}) as HTMLInputElement).checked).toBe(false);
    expect(fetch).not.toHaveBeenCalled();
    expect(Object.keys(localStorage).sort()).toEqual(['siger:font-scale', 'siger:theme']);
  });
  it('aplica apariencia y escala acotada sin almacenar datos de personas', async () => {
    render(<StartPage />);
    await userEvent.selectOptions(screen.getByRole('combobox', {name: 'Apariencia: claro, oscuro o alto contraste'}), 'contrast');
    expect(document.documentElement.dataset.theme).toBe('contrast');
    for (let i = 0; i < 5; i++) await userEvent.click(screen.getByRole('button', {name: 'Aumentar tamaño de texto'}));
    expect(document.documentElement.style.getPropertyValue('--ui-scale')).toBe('1.3');
    expect(Object.keys(localStorage).sort()).toEqual(['siger:font-scale', 'siger:theme']);
    expect(localStorage.getItem('siger:font-scale')).toBe('1.3');
  });
});
