import {StrictMode, createRef} from 'react';
import {act, cleanup, fireEvent, render, screen} from '@testing-library/react';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ApiError} from '../api';
import {IdentificationControls} from './IdentificationControls';
import type {IdentificationHandle} from './IdentificationControls';
import {PersonIdentity} from './PersonIdentity';
import {declarePerson, getPerson, identifyPerson, resetPerson} from './api';
import type {IdentityView, Journey} from './api';
vi.mock('./api',()=>({getPerson:vi.fn(),identifyPerson:vi.fn(),declarePerson:vi.fn(),resetPerson:vi.fn()}));
const journey:Journey={id:'automatico-sintetico',tipo:'ADULTO_DECLARADO',avisoVersion:'LAB_1',aceptaCanal:true,estado:'BORRADOR',venceEn:'2026-10-25T00:00:00Z'};
const empty=():IdentityView=>({personaId:'presentador-adulto',estado:'REVISADO',autenticada:false,runInformado:true,camposFaltantes:['primerNombre','primerApellido'],camposObligatorios:['primerNombre','primerApellido'],valoresDeclarados:{},mensaje:'Completa los datos solicitados.'});
beforeEach(()=>{vi.resetAllMocks();vi.mocked(getPerson).mockRejectedValue(new ApiError(404));vi.mocked(identifyPerson).mockResolvedValue(empty());vi.mocked(resetPerson).mockResolvedValue(undefined);vi.mocked(declarePerson).mockImplementation(async(_j,_p,values)=>({...empty(),valoresDeclarados:values}));});
afterEach(()=>{cleanup();vi.useRealTimers();});
async function mount(props:Partial<Parameters<typeof IdentificationControls>[0]>={}){
 const identityRef=createRef<IdentificationHandle>();const result=render(<StrictMode><IdentificationControls journey={journey} session={null} identityRef={identityRef} {...props}/></StrictMode>);
 await act(async()=>{await Promise.resolve();});return {...result,identityRef};
}
function type(number='12345678',dv='5'){
 fireEvent.change(screen.getByRole('textbox',{name:/^RUN/}),{target:{value:number}});fireEvent.change(screen.getByRole('textbox',{name:'Dígito verificador del RUN'}),{target:{value:dv}});
}
const advance=async(ms=650)=>{await act(async()=>{await vi.advanceTimersByTimeAsync(ms);});};
describe('RUN automático sin consultas en cada pulsación',()=>{
 it('espera RUN/DV válido y 650 ms estables; deduplica botón, renders y guardado',async()=>{
  const {identityRef}=await mount();vi.useFakeTimers();type('12345678','0');await advance(2000);expect(identifyPerson).not.toHaveBeenCalled();
  type();await advance(400);type('11111111','1');await advance(649);expect(identifyPerson).not.toHaveBeenCalled();await advance(1);
  expect(identifyPerson).toHaveBeenCalledTimes(1);expect(identifyPerson).toHaveBeenLastCalledWith(journey.id,'presentador-adulto','PRESENTADOR','11111111','1');
  fireEvent.click(screen.getByRole('button',{name:'Revisar RUN'}));await advance(2000);expect(identifyPerson).toHaveBeenCalledTimes(1);
  fireEvent.change(screen.getByRole('textbox',{name:/^Primer nombre/}),{target:{value:'NOMBRE'}});fireEvent.change(screen.getByRole('textbox',{name:/^Primer apellido/}),{target:{value:'APELLIDO'}});await act(async()=>{await identityRef.current!.save();});expect(identifyPerson).toHaveBeenCalledTimes(1);
 });
 it.each(['completo','parcial','vacio','error','timeout'])('resultado %s solo presenta faltantes o fallback, nunca valores registrales',async state=>{
  const fields:IdentityView['camposFaltantes']=state==='completo'?[]:state==='parcial'?['primerApellido']:['primerNombre','primerApellido'];
  vi.mocked(identifyPerson).mockResolvedValue({...empty(),camposFaltantes:fields,camposObligatorios:fields});await mount();vi.useFakeTimers();type();await advance();
  expect(identifyPerson).toHaveBeenCalledTimes(1);expect(screen.queryByRole('textbox',{name:/^Primer nombre/})!==null).toBe(fields.includes('primerNombre'));expect(screen.queryByRole('textbox',{name:/^Primer apellido/})!==null).toBe(fields.includes('primerApellido'));
  expect(screen.queryByText(/REGISTRAL PRIVADO/)).toBeNull();
 });
 it('no activa consulta si falta aceptar el canal',async()=>{await mount({journey:{...journey,aceptaCanal:false}});vi.useFakeTimers();type();await advance(2000);expect(identifyPerson).not.toHaveBeenCalled();});
 it('botón durante debounce usa una sola solicitud y no admite cambios mientras consulta',async()=>{
  let complete!:(view:IdentityView)=>void;vi.mocked(identifyPerson).mockImplementation(()=>new Promise(resolve=>{complete=resolve;}));await mount();vi.useFakeTimers();type();await advance(300);fireEvent.click(screen.getByRole('button',{name:'Revisar RUN'}));await advance(1000);expect(identifyPerson).toHaveBeenCalledTimes(1);expect((screen.getByRole('textbox',{name:/^RUN/}) as HTMLInputElement).disabled).toBe(true);await act(async()=>{complete(empty());});expect((screen.getByRole('textbox',{name:/^RUN/}) as HTMLInputElement).disabled).toBe(false);
 });
 it('al cambiar RUN retira la identidad anterior una sola vez, aun si el nuevo valor queda inválido',async()=>{
  await mount();vi.useFakeTimers();type();await advance();fireEvent.change(screen.getByRole('textbox',{name:/^Primer nombre/}),{target:{value:'ANTERIOR'}});
  fireEvent.change(screen.getByRole('textbox',{name:/^RUN/}),{target:{value:'1'}});fireEvent.change(screen.getByRole('textbox',{name:/^RUN/}),{target:{value:'11'}});await advance(2000);expect(resetPerson).toHaveBeenCalledTimes(1);expect(screen.queryByDisplayValue('ANTERIOR')).toBeNull();expect(identifyPerson).toHaveBeenCalledTimes(1);
  type('11111111','1');await advance();expect(identifyPerson).toHaveBeenCalledTimes(2);expect(resetPerson).toHaveBeenCalledTimes(1);
 });
 it('restaura declaraciones propias sin exponer el RUN ni repetir la consulta concluida',async()=>{
  vi.mocked(getPerson).mockResolvedValue({...empty(),valoresDeclarados:{primerNombre:'DECLARADO'}});await mount();vi.useFakeTimers();await advance(3000);expect(screen.getByDisplayValue('DECLARADO')).toBeTruthy();expect(identifyPerson).not.toHaveBeenCalled();expect(screen.queryByRole('textbox',{name:/^RUN/})).toBeNull();
 });
 it('una reserva vigente se restaura con GET y conserva declaraciones al resolverse, sin POST',async()=>{
  const pending:IdentityView={...empty(),estado:'CONSULTANDO',camposFaltantes:[],camposObligatorios:[],valoresDeclarados:{}};vi.mocked(getPerson).mockResolvedValue(pending);vi.useFakeTimers();await mount();vi.mocked(getPerson).mockResolvedValue({...empty(),valoresDeclarados:{primerNombre:'PROPIO'}});await advance(2100);expect(screen.getByDisplayValue('PROPIO')).toBeTruthy();expect(identifyPerson).not.toHaveBeenCalled();
 });
 it('NNA solo envía un RUN cuando lo entrega voluntariamente; sin RUN no consulta registros',async()=>{
  vi.mocked(identifyPerson).mockImplementation(async(_j,_p,_role,run)=>({...empty(),personaId:'presentador-nna',runInformado:!!run}));await mount({journey:{...journey,tipo:'NNA',aceptaCanal:false},nna:true});expect(identifyPerson).toHaveBeenCalledTimes(1);expect(identifyPerson).toHaveBeenCalledWith(journey.id,'presentador-nna','PRESENTADOR');
  fireEvent.click(screen.getByRole('radio',{name:'Sí, quiero ingresarlo'}));vi.useFakeTimers();type();await advance();expect(identifyPerson).toHaveBeenCalledTimes(2);expect(identifyPerson).toHaveBeenLastCalledWith(journey.id,'presentador-nna','PRESENTADOR','12345678','5');
 });
 it('una respuesta del componente desmontado no modifica otro recorrido',async()=>{
  let complete!:(view:IdentityView)=>void;const onResolved=vi.fn();vi.mocked(identifyPerson).mockImplementation(()=>new Promise(resolve=>{complete=resolve;}));const mounted=await mount({onResolved});vi.useFakeTimers();type();await advance();mounted.unmount();const before=onResolved.mock.calls.length;await act(async()=>{complete(empty());});expect(onResolved).toHaveBeenCalledTimes(before);
 });
 it('si otro tab cambia la identidad, no adopta su proyección como respuesta del RUN escrito',async()=>{
  vi.mocked(identifyPerson).mockRejectedValue(new ApiError(409));await mount();vi.useFakeTimers();type();await advance();expect(screen.getByText(/Los datos cambiaron en otra ventana/)).toBeTruthy();expect(screen.queryByRole('textbox',{name:/^Primer nombre/})).toBeNull();expect(getPerson).toHaveBeenCalledTimes(2);await advance(3000);expect(identifyPerson).toHaveBeenCalledTimes(1);
 });
 it('una restauración tardía no sustituye el RUN que el ciudadano empezó a corregir',async()=>{
  let restore!:(view:IdentityView)=>void;vi.mocked(getPerson).mockImplementation(()=>new Promise(resolve=>{restore=resolve;}));await mount();vi.useFakeTimers();type('11111111','1');await act(async()=>{restore({...empty(),valoresDeclarados:{primerNombre:'ANTIGUO'}});});await advance();expect(screen.queryByDisplayValue('ANTIGUO')).toBeNull();expect(resetPerson).toHaveBeenCalledTimes(1);expect(identifyPerson).toHaveBeenLastCalledWith(journey.id,'presentador-adulto','PRESENTADOR','11111111','1');
 });
 it('si falla restaurar mientras se escribe, no consulta hasta resolver el estado previo con un reinicio explícito',async()=>{
  let reject!:(error:ApiError)=>void;vi.mocked(getPerson).mockImplementation(()=>new Promise((_resolve,fail)=>{reject=fail;}));await mount();vi.useFakeTimers();type();expect((screen.getByRole('button',{name:'Revisar RUN'}) as HTMLButtonElement).disabled).toBe(true);await act(async()=>{reject(new ApiError(503));});await advance(2000);expect(identifyPerson).not.toHaveBeenCalled();fireEvent.click(screen.getByRole('button',{name:'Volver a intentar'}));await advance();expect(resetPerson).toHaveBeenCalledTimes(1);expect(identifyPerson).toHaveBeenCalledTimes(1);
 });
 it('el RUN de terceros sigue siendo opcional y no tiene un disparador automático',async()=>{
  render(<PersonIdentity journey={journey.id} person="relacionado-1" role="ADULTO_RELACIONADO"/>);await act(async()=>{await Promise.resolve();});vi.mocked(identifyPerson).mockClear();vi.useFakeTimers();type();await advance(3000);expect(identifyPerson).not.toHaveBeenCalled();
 });
});
