import {render, screen} from '@testing-library/react';
import {beforeEach, expect, test, vi} from 'vitest';
import CertificationPage from './CertificationPage';
import {getCsrf, getSession} from './api';
vi.mock('./api',()=>({getSession:vi.fn(),getCsrf:vi.fn()}));
beforeEach(()=>{vi.resetAllMocks();window.history.replaceState({},'', '/');});
test('offers direct official OAuth and no citizen form or journey',async()=>{
  vi.mocked(getSession).mockResolvedValue({authenticated:false,identity:null,environment:'PRODUCTION',loginAvailable:true,certificationMode:true});
  render(<CertificationPage/>);
  expect(await screen.findByRole('link',{name:'Iniciar sesión con ClaveÚnica'})).toHaveAttribute('href','/oauth2/authorization/claveunica');
  expect(screen.getByText('Recepción de comunicaciones suspendida')).toBeVisible();
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  expect(screen.queryByText('Autenticación realizada correctamente')).not.toBeInTheDocument();
});
test('uses backend session result and a native CSRF-protected logout form',async()=>{
  vi.mocked(getSession).mockResolvedValue({authenticated:true,identity:null,environment:'PRODUCTION',loginAvailable:true,certificationMode:true});
  vi.mocked(getCsrf).mockResolvedValue({token:'unit-test-token',parameterName:'_csrf',headerName:'X-CSRF-TOKEN'});
  render(<CertificationPage/>);
  expect(await screen.findByText('Autenticación realizada correctamente')).toBeVisible();
  const button=screen.getByRole('button',{name:'Cerrar sesión'});
  expect(button.closest('form')).toHaveAttribute('action','/auth/logout');
  expect(button.closest('form')).toHaveAttribute('method','post');
  expect(button.closest('form')?.querySelector('input[name="_csrf"]')).toHaveValue('unit-test-token');
});
