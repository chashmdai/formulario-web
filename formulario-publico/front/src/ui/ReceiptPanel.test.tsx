import {StrictMode} from 'react';
import {act,fireEvent,render,screen} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {ReceiptPanel} from './ReceiptPanel';
import type {SubmissionState} from '../formulario/submission';

const pending:SubmissionState={id:'11111111-1111-4111-8111-111111111111',estado:'PENDIENTE_REGISTRO_SIGER',custodia:{algoritmo:'Ed25519',keyId:'test',payload:'sintetico',firma:'sintetica'},registro:null};
const confirmed:SubmissionState={...pending,estado:'REGISTRADA_SIGER',registro:{registro:{folio:'COM-000123'},comprobante:pending.custodia}};
beforeEach(()=>vi.useFakeTimers());
afterEach(()=>{vi.clearAllTimers();vi.useRealTimers();});
describe('Confirmación ciudadana con actualización automática',()=>{
 it('consulta a los cuatro segundos una sola vez incluso con StrictMode',async()=>{
  const check=vi.fn().mockResolvedValue(undefined);
  render(<StrictMode><ReceiptPanel receipt={pending} busy={false} onCheck={check} onDownload={vi.fn()}/></StrictMode>);
  await act(()=>vi.advanceTimersByTimeAsync(3999));expect(check).not.toHaveBeenCalled();
  await act(()=>vi.advanceTimersByTimeAsync(1));expect(check).toHaveBeenCalledTimes(1);
 });
 it('espera a terminar una consulta y reanuda sin solapar solicitudes',async()=>{
  const check=vi.fn().mockResolvedValue(undefined),download=vi.fn();
  const {rerender}=render(<ReceiptPanel receipt={pending} busy={false} onCheck={check} onDownload={download}/>);
  await act(()=>vi.advanceTimersByTimeAsync(4000));expect(check).toHaveBeenCalledTimes(1);
  rerender(<ReceiptPanel receipt={pending} busy onCheck={check} onDownload={download}/>);
  await act(()=>vi.advanceTimersByTimeAsync(20000));expect(check).toHaveBeenCalledTimes(1);
  expect((screen.getByRole('button',{name:'Descargar comprobante de recepción'}) as HTMLButtonElement).disabled).toBe(false);
  rerender(<ReceiptPanel receipt={pending} busy={false} onCheck={check} onDownload={download}/>);
  await act(()=>vi.advanceTimersByTimeAsync(4000));expect(check).toHaveBeenCalledTimes(2);
 });
 it('muestra el folio y comprobante institucional al llegar ACK y detiene las consultas',async()=>{
  const check=vi.fn().mockResolvedValue(undefined),download=vi.fn();
  const {rerender}=render(<ReceiptPanel receipt={pending} busy={false} onCheck={check} onDownload={download}/>);
  expect(document.querySelector('.receipt-waiting .ui-spinner')?.getAttribute('aria-hidden')).toBe('true');
  expect(screen.queryByRole('button',{name:'Descargar comprobante institucional'})).toBeNull();
  rerender(<ReceiptPanel receipt={confirmed} busy={false} onCheck={check} onDownload={download}/>);
  expect(screen.getByRole('heading',{name:'Comunicación registrada'})).toBeTruthy();expect(screen.getByText('COM-000123')).toBeTruthy();
  expect(screen.queryByRole('button',{name:'Actualizar estado'})).toBeNull();expect(screen.getByRole('heading',{name:'Guarda tus comprobantes'})).toBeTruthy();expect(screen.getByText(/Anota y guarda el folio/)).toBeTruthy();
  fireEvent.click(screen.getByRole('button',{name:'Descargar comprobante institucional'}));
  expect(download).toHaveBeenCalledWith(confirmed.registro,'comprobante-institucional-'+pending.id+'.json');
  await act(()=>vi.advanceTimersByTimeAsync(90000));expect(check).not.toHaveBeenCalled();expect(document.querySelector('.receipt-waiting')).toBeNull();
 });
 it('explica una demora y conserva el comprobante público ante errores',async()=>{
  const download=vi.fn(),check=vi.fn().mockResolvedValue(undefined);
  render(<ReceiptPanel receipt={pending} busy={false} error="No pudimos actualizar el estado." onCheck={check} onDownload={download}/>);
  await act(()=>vi.advanceTimersByTimeAsync(60000));
  expect(screen.getByText(/El registro está tardando más/)).toBeTruthy();expect(screen.getByRole('alert').textContent).toContain('No pudimos actualizar');
  fireEvent.click(screen.getByRole('button',{name:'Reintentar consulta'}));expect(check).toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button',{name:'Descargar comprobante de recepción'}));
  expect(download).toHaveBeenCalledWith(pending.custodia,'comprobante-custodia-'+pending.id+'.json');
  expect(screen.queryByRole('button',{name:/Enviar/})).toBeNull();
 });
 it('cancela los temporizadores al salir y no consulta una recepción ya registrada',async()=>{
  const check=vi.fn().mockResolvedValue(undefined);
  const view=render(<ReceiptPanel receipt={pending} busy={false} onCheck={check} onDownload={vi.fn()}/>);
  view.unmount();await act(()=>vi.advanceTimersByTimeAsync(90000));expect(check).not.toHaveBeenCalled();
  render(<ReceiptPanel receipt={confirmed} busy={false} onCheck={check} onDownload={vi.fn()}/>);
  await act(()=>vi.advanceTimersByTimeAsync(90000));expect(check).not.toHaveBeenCalled();
 });
});
