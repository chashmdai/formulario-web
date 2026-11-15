import {StrictMode} from 'react';
import {act,fireEvent,render,screen} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {ReceiptPanel} from './ReceiptPanel';
import type {SubmissionState} from '../formulario/submission';
import {b64url,pending,registered as confirmed,registryContent} from '../comprobante/fixtures.test-utils';

/** Estado declarado como registrado cuyo contenido firmado no corresponde al folio o a la referencia. */
const incoherent:SubmissionState[]=[
 {...confirmed,registro:{...confirmed.registro!,registro:{folio:'COM-999999'}}},
 {...confirmed,registro:{...confirmed.registro!,comprobante:{...confirmed.registro!.comprobante,payload:b64url({...registryContent,operacionPublicaId:'99999999-9999-4999-8999-999999999999'})}}},
 {...confirmed,registro:{...confirmed.registro!,comprobante:pending.custodia}},
 {...confirmed,registro:{registro:{folio:'COM-000123'},comprobante:{algoritmo:'Ed25519',keyId:'x',payload:'no-decodificable',firma:'x'}}},
];
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
  expect((screen.getByRole('button',{name:'Descargar evidencia de custodia (JSON)'}) as HTMLButtonElement).disabled).toBe(false);
  rerender(<ReceiptPanel receipt={pending} busy={false} onCheck={check} onDownload={download}/>);
  await act(()=>vi.advanceTimersByTimeAsync(4000));expect(check).toHaveBeenCalledTimes(2);
 });
 it('muestra el folio y comprobante institucional al llegar ACK y detiene las consultas',async()=>{
  const check=vi.fn().mockResolvedValue(undefined),download=vi.fn();
  const {rerender}=render(<ReceiptPanel receipt={pending} busy={false} onCheck={check} onDownload={download}/>);
  expect(document.querySelector('.receipt-waiting .ui-spinner')?.getAttribute('aria-hidden')).toBe('true');
  expect(screen.queryByRole('button',{name:'Descargar registro institucional (JSON)'})).toBeNull();
  rerender(<ReceiptPanel receipt={confirmed} busy={false} onCheck={check} onDownload={download}/>);
  expect(screen.getByRole('heading',{name:'Comunicación registrada'})).toBeTruthy();expect(screen.getByText('COM-000123')).toBeTruthy();
  expect(screen.queryByRole('button',{name:'Actualizar estado'})).toBeNull();expect(screen.getByRole('heading',{name:'Guarda tu comprobante'})).toBeTruthy();expect(screen.getByText(/Anota y guarda el folio/)).toBeTruthy();
  fireEvent.click(screen.getByRole('button',{name:'Descargar registro institucional (JSON)'}));
  expect(download).toHaveBeenCalledWith(confirmed.registro,'comprobante-institucional-'+pending.id+'.json');
  await act(()=>vi.advanceTimersByTimeAsync(90000));expect(check).not.toHaveBeenCalled();expect(document.querySelector('.receipt-waiting')).toBeNull();
 });
 it('explica una demora y conserva el comprobante público ante errores',async()=>{
  const download=vi.fn(),check=vi.fn().mockResolvedValue(undefined);
  render(<ReceiptPanel receipt={pending} busy={false} error="No pudimos actualizar el estado." onCheck={check} onDownload={download}/>);
  await act(()=>vi.advanceTimersByTimeAsync(60000));
  expect(screen.getByText(/El registro está tardando más/)).toBeTruthy();expect(screen.getByRole('alert').textContent).toContain('No pudimos actualizar');
  fireEvent.click(screen.getByRole('button',{name:'Reintentar consulta'}));expect(check).toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button',{name:'Descargar evidencia de custodia (JSON)'}));
  expect(download).toHaveBeenCalledWith(pending.custodia,'comprobante-custodia-'+pending.id+'.json');
  expect(screen.queryByRole('button',{name:/Enviar/})).toBeNull();
 });
 it('el PDF es la acción principal, no se duplica mientras se prepara y un fallo conserva las evidencias',async()=>{
  let finish:(value?:unknown)=>void=()=>{};const pdf=vi.fn(()=>new Promise(resolve=>{finish=resolve;}));const download=vi.fn();
  render(<ReceiptPanel receipt={confirmed} busy={false} onCheck={vi.fn()} onDownload={download} onPdf={pdf}/>);
  const button=screen.getByRole('button',{name:'Descargar comprobante PDF'});expect(button.className).toContain('btn-primary');
  fireEvent.click(button);expect(pdf).toHaveBeenCalledWith(confirmed,expect.any(Function));
  const busy=screen.getByRole('button',{name:'Preparando comprobante…'}) as HTMLButtonElement;expect(busy.disabled).toBe(true);fireEvent.click(busy);expect(pdf).toHaveBeenCalledTimes(1);
  await act(async()=>{finish();});expect(screen.getByRole('button',{name:'Descargar comprobante PDF'})).toBeTruthy();expect(screen.queryByRole('alert')).toBeNull();
  pdf.mockRejectedValueOnce(new Error('fallo controlado'));await act(async()=>{fireEvent.click(screen.getByRole('button',{name:'Descargar comprobante PDF'}));});
  expect(screen.getByRole('alert').textContent).toMatch(/No pudimos preparar el comprobante PDF/);
  expect(screen.getByText('Evidencias técnicas originales (JSON)').closest('details')).toBeTruthy();
  fireEvent.click(screen.getByRole('button',{name:'Descargar evidencia de custodia (JSON)'}));expect(download).toHaveBeenCalledWith(confirmed.custodia,'comprobante-custodia-'+pending.id+'.json');
  expect(screen.getByText(/no está firmado digitalmente/)).toBeTruthy();
 });
 it('mientras el registro está pendiente no existe descarga PDF y se conservan la referencia y la custodia',async()=>{
  const pdf=vi.fn().mockResolvedValue(undefined),download=vi.fn();
  render(<ReceiptPanel receipt={pending} busy={false} onCheck={vi.fn()} onDownload={download} onPdf={pdf}/>);
  expect(screen.getByText('Registro institucional pendiente')).toBeTruthy();expect(screen.queryByRole('button',{name:/comprobante PDF/})).toBeNull();
  expect(screen.queryByRole('button',{name:'Descargar registro institucional (JSON)'})).toBeNull();expect(screen.queryByText(/^Folio:/)).toBeNull();
  expect(screen.getByText(pending.id)).toBeTruthy();expect(screen.getByRole('heading',{name:'Guarda tu referencia de recepción'})).toBeTruthy();
  fireEvent.click(screen.getByRole('button',{name:'Descargar evidencia de custodia (JSON)'}));expect(download).toHaveBeenCalledWith(pending.custodia,'comprobante-custodia-'+pending.id+'.json');
  await act(()=>vi.advanceTimersByTimeAsync(60000));expect(screen.queryByRole('button',{name:/comprobante PDF/})).toBeNull();expect(pdf).not.toHaveBeenCalled();
 });
 it('un registro incoherente no se presenta como confirmado ni habilita PDF o JSON institucional',async()=>{
  for(const receipt of incoherent){
   const pdf=vi.fn().mockResolvedValue(undefined),check=vi.fn().mockResolvedValue(undefined);
   const view=render(<ReceiptPanel receipt={receipt} busy={false} onCheck={check} onDownload={vi.fn()} onPdf={pdf}/>);
   expect(screen.getByText('Registro institucional en verificación')).toBeTruthy();expect(screen.queryByRole('heading',{name:'Comunicación registrada'})).toBeNull();
   expect(screen.queryByText(/^Folio:/)).toBeNull();expect(screen.queryByText(/COM-\d/)).toBeNull();
   expect(screen.queryByRole('button',{name:/comprobante PDF/})).toBeNull();expect(screen.queryByRole('button',{name:'Descargar registro institucional (JSON)'})).toBeNull();
   expect(screen.getByRole('button',{name:'Descargar evidencia de custodia (JSON)'})).toBeTruthy();
   await act(()=>vi.advanceTimersByTimeAsync(4000));expect(check).toHaveBeenCalledTimes(1);expect(pdf).not.toHaveBeenCalled();
   view.unmount();
  }
 });
 it('una descarga en curso consulta el estado vigente al terminar de prepararse',async()=>{
  let finish:(value?:unknown)=>void=()=>{};const pdf=vi.fn((_:SubmissionState,current:()=>SubmissionState)=>{void current;return new Promise(resolve=>{finish=resolve;});});
  const {rerender}=render(<ReceiptPanel receipt={confirmed} busy={false} onCheck={vi.fn()} onDownload={vi.fn()} onPdf={pdf}/>);
  fireEvent.click(screen.getByRole('button',{name:'Descargar comprobante PDF'}));const current=pdf.mock.calls[0][1];expect(current()).toBe(confirmed);
  rerender(<ReceiptPanel receipt={pending} busy={false} onCheck={vi.fn()} onDownload={vi.fn()} onPdf={pdf}/>);
  expect(current()).toBe(pending);expect(screen.queryByRole('button',{name:/comprobante PDF|Preparando/})).toBeNull();
  await act(async()=>{finish();});
 });
 it('al volver a pendiente tras un estado confirmado retira el PDF y no lo genera',()=>{
  const pdf=vi.fn().mockResolvedValue(undefined);
  const {rerender}=render(<ReceiptPanel receipt={confirmed} busy={false} onCheck={vi.fn()} onDownload={vi.fn()} onPdf={pdf}/>);
  expect(screen.getByRole('button',{name:'Descargar comprobante PDF'})).toBeTruthy();
  rerender(<ReceiptPanel receipt={incoherent[0]} busy={false} onCheck={vi.fn()} onDownload={vi.fn()} onPdf={pdf}/>);
  expect(screen.queryByRole('button',{name:/comprobante PDF/})).toBeNull();expect(pdf).not.toHaveBeenCalled();
 });
 it('cancela los temporizadores al salir y no consulta una recepción ya registrada',async()=>{
  const check=vi.fn().mockResolvedValue(undefined);
  const view=render(<ReceiptPanel receipt={pending} busy={false} onCheck={check} onDownload={vi.fn()}/>);
  view.unmount();await act(()=>vi.advanceTimersByTimeAsync(90000));expect(check).not.toHaveBeenCalled();
  render(<ReceiptPanel receipt={confirmed} busy={false} onCheck={check} onDownload={vi.fn()}/>);
  await act(()=>vi.advanceTimersByTimeAsync(90000));expect(check).not.toHaveBeenCalled();
 });
});
