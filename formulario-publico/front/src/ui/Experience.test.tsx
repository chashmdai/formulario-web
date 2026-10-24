import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {FormProgress,FormToolbar} from './FormChrome';
import {ReceiptPanel} from './ReceiptPanel';
import {AttachmentStatus} from './AttachmentStatus';
import {ExitDialog} from './ExitDialog';
import type {SubmissionState} from '../formulario/submission';
const receipt:SubmissionState={id:'referencia-controlada',estado:'PENDIENTE_REGISTRO_SIGER',custodia:{algoritmo:'Ed25519',keyId:'controlado',payload:'controlado',firma:'controlada'},registro:null};
beforeEach(()=>{HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');};});
afterEach(()=>{cleanup();vi.restoreAllMocks();});
describe('Experiencia ciudadana',()=>{
 it('anuncia el paso actual y conserva navegación accesible en ambos públicos',async()=>{
  const go=vi.fn();const {rerender}=render(<FormProgress step={2} full={false} disabled={false} onNavigate={go}/>);
  expect(screen.getByRole('progressbar').getAttribute('aria-valuenow')).toBe('2');expect(screen.getByRole('button',{name:'Motivo de contacto'}).getAttribute('aria-current')).toBe('step');
  await userEvent.click(screen.getByRole('button',{name:'Tus datos'}));expect(go).toHaveBeenCalledWith(1);
  rerender(<FormProgress nna step={1} full disabled onNavigate={go}/>);expect(screen.getAllByRole('button').every(b=>(b as HTMLButtonElement).disabled)).toBe(true);expect(screen.getByRole('progressbar').getAttribute('aria-valuemax')).toBe('2');
 });
 it('el selector de vista comunica su estado y devuelve la elección sin modificar datos',async()=>{
  const view=vi.fn();render(<FormToolbar title="Persona adulta" full={false} step={1} total={3} onView={view}/>);
  expect(screen.getByRole('button',{name:'Por pasos'}).getAttribute('aria-pressed')).toBe('true');await userEvent.click(screen.getByRole('button',{name:'Completo'}));expect(view).toHaveBeenCalledWith(true);
 });
 it('una custodia pendiente no inventa folio ni ofrece comprobante institucional',async()=>{
  const download=vi.fn(),check=vi.fn();render(<ReceiptPanel receipt={receipt} busy={false} onDownload={download} onCheck={check}/>);
  expect(document.activeElement?.id).toBe('communication-receipt');expect(screen.getByRole('heading',{name:'Recibimos tu comunicación'})).toBeTruthy();expect(screen.queryByText(/^Folio:/)).toBeNull();expect(screen.queryByRole('button',{name:'Descargar comprobante institucional'})).toBeNull();
  await userEvent.click(screen.getByRole('button',{name:'Descargar comprobante de recepción'}));expect(download).toHaveBeenCalledWith(receipt.custodia,'comprobante-custodia-referencia-controlada.json');await userEvent.click(screen.getByRole('button',{name:'Actualizar estado'}));expect(check).toHaveBeenCalledOnce();
 });
 it('el registro confirmado muestra el folio y descarga el objeto firmado recibido sin alterarlo',async()=>{
  const registered:SubmissionState={...receipt,estado:'REGISTRADA_SIGER',registro:{registro:{folio:'FOLIO-CONTROLADO'},comprobante:receipt.custodia}},download=vi.fn();render(<ReceiptPanel receipt={registered} busy={false} onDownload={download} onCheck={vi.fn()}/>);
  expect(screen.getByText('FOLIO-CONTROLADO')).toBeTruthy();await userEvent.click(screen.getByRole('button',{name:'Descargar comprobante institucional'}));expect(download).toHaveBeenCalledWith(registered.registro,'comprobante-institucional-referencia-controlada.json');
 });
 it('una consulta de estado ocupada conserva el estado real y muestra el error recibido',()=>{
  render(<ReceiptPanel receipt={receipt} busy error="No pudimos actualizar el estado." onDownload={vi.fn()} onCheck={vi.fn()}/>);expect((screen.getByRole('button',{name:'Consultando estado…'}) as HTMLButtonElement).disabled).toBe(true);expect(screen.getByRole('alert').textContent).toBe('No pudimos actualizar el estado.');expect(screen.getByText('Registro institucional pendiente')).toBeTruthy();
 });
 it('el reemplazo rechazado informa que los archivos anteriores se conservan',()=>{
  render(<AttachmentStatus id="archivos" files={[{id:'archivo',nombre:'respaldo.txt',tamanio:2048,sha256:'controlado',estado:'VALIDADO'}]} pending={false} error="Archivo rechazado." feedback=""/>);expect(screen.getByRole('alert').textContent).toBe('Archivo rechazado.');expect(screen.getByText('respaldo.txt')).toBeTruthy();expect(screen.getByText('2 KB')).toBeTruthy();expect(screen.getByText('Los archivos recibidos anteriormente se conservan.')).toBeTruthy();
 });
 it('volver al inicio exige una elección y seguir completando conserva el formulario',async()=>{
  const stay=vi.fn(),leave=vi.fn();render(<ExitDialog onStay={stay} onLeave={leave}/>);expect(screen.getByRole('dialog')).toBeTruthy();expect(leave).not.toHaveBeenCalled();const stayButton=screen.getByRole('button',{name:'Seguir completando'});stayButton.focus();fireEvent.keyDown(stayButton,{key:'Tab'});expect(document.activeElement).toBe(screen.getByRole('button',{name:'Cerrar y seguir completando'}));fireEvent.keyDown(document.activeElement!,{key:'Tab',shiftKey:true});expect(document.activeElement).toBe(stayButton);await userEvent.click(stayButton);expect(stay).toHaveBeenCalledOnce();expect(leave).not.toHaveBeenCalled();await userEvent.click(screen.getByRole('button',{name:'Volver al inicio'}));expect(leave).toHaveBeenCalledOnce();
 });
});
