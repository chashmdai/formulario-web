import {render, screen} from '@testing-library/react';
import {beforeEach, expect, test, vi} from 'vitest';
import CertificationPage from './CertificationPage';
import {getCsrf, getSession} from './api';
vi.mock('./api',()=>({getSession:vi.fn(),getCsrf:vi.fn()}));
beforeEach(()=>{vi.resetAllMocks();window.history.replaceState({},'', '/');});
test('offers direct official OAuth and no citizen form or journey',async()=>{
  vi.mocked(getSession).mockResolvedValue({authenticated:false,identity:null,environment:'PRODUCTION',loginAvailable:true,certificationMode:true});
  render(<CertificationPage/>);
  expect((await screen.findByRole('link',{name:'Iniciar sesión con ClaveÚnica'})).getAttribute('href')).toBe('/oauth2/authorization/claveunica');
  expect(screen.getByText('Recepción de comunicaciones suspendida')).not.toBeNull();
  expect(screen.queryByRole('textbox')).toBeNull();
  expect(screen.queryByText('Autenticación realizada correctamente')).toBeNull();
});
test('uses backend session result and a native CSRF-protected logout form',async()=>{
  vi.mocked(getSession).mockResolvedValue({authenticated:true,identity:null,environment:'PRODUCTION',loginAvailable:true,certificationMode:true});
  vi.mocked(getCsrf).mockResolvedValue({token:'unit-test-token',parameterName:'_csrf',headerName:'X-CSRF-TOKEN'});
  render(<CertificationPage/>);
  expect(await screen.findByText('Autenticación realizada correctamente')).not.toBeNull();
  const button=screen.getByRole('button',{name:'Cerrar sesión'});
  expect(button.closest('form')?.getAttribute('action')).toBe('/auth/logout');
  expect(button.closest('form')?.getAttribute('method')).toBe('post');
  expect(button.closest('form')?.querySelector<HTMLInputElement>('input[name="_csrf"]')?.value).toBe('unit-test-token');
});
