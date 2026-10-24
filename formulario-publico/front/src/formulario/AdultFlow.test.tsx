import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api';
import { getPerson, identifyPerson, declarePerson, resetPerson } from './api';
import type { IdentityView, Journey } from './api';
import { AdultFlow } from './AdultFlow';
import type { AttachmentPort } from './NnaFlow';
vi.mock('./api',()=>({getPerson:vi.fn(),identifyPerson:vi.fn(),declarePerson:vi.fn(),resetPerson:vi.fn()}));
const journey:Journey={id:'adulto-sintetico',tipo:'ADULTO_DECLARADO',avisoVersion:'ADULTO_LAB_1',aceptaCanal:true,estado:'BORRADOR',venceEn:'2026-10-25T00:00:00Z'};
let authenticated:boolean;const stored=new Map<string,IdentityView>();
const byId=(id:string)=>document.getElementById(id)!;
beforeEach(()=>{
  authenticated=false;stored.clear();vi.resetAllMocks();
  HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');};
  vi.mocked(getPerson).mockImplementation(async(_j,p)=>{if(!stored.has(p))throw new ApiError(404);return stored.get(p)!;});
  vi.mocked(identifyPerson).mockImplementation(async(_j,p,role,run)=>{
    const cu=role==='PRESENTADOR'&&authenticated;const fields:IdentityView['camposFaltantes']=cu?['fechaNacimiento','sexo']:role==='AFECTADO_NNA'?['primerNombre','segundoNombre','primerApellido','segundoApellido','edad']:role==='PRESENTADOR'?['primerNombre','segundoNombre','primerApellido','segundoApellido','fechaNacimiento','sexo']:['primerNombre','segundoNombre','primerApellido','segundoApellido'];
    const v:IdentityView={personaId:p,estado:'REVISADO',autenticada:cu,runInformado:cu||!!run,camposFaltantes:fields,camposObligatorios:role==='PRESENTADOR'&&!cu?['primerNombre','primerApellido']:[],valoresDeclarados:{},mensaje:'Completa los datos solicitados.'};stored.set(p,v);return v;
  });
  vi.mocked(declarePerson).mockImplementation(async(_j,p,values)=>{const v={...stored.get(p)!,valoresDeclarados:values};stored.set(p,v);return v;});
  vi.mocked(resetPerson).mockImplementation(async(_j,p)=>{stored.delete(p);});
});
function mount(full=false,port?:AttachmentPort,cu=false){
  authenticated=cu;const review=vi.fn().mockResolvedValue(undefined),step=vi.fn();const files=port||{replace:vi.fn().mockResolvedValue([]),clear:vi.fn().mockResolvedValue(undefined)};
  render(<AdultFlow journey={{...journey,tipo:cu?'ADULTO_CU':'ADULTO_DECLARADO'}} session={cu?{authenticated:true,identity:{run:'12345678-5',nombres:'NOMBRE CU',apellidos:'APELLIDO CU',nombresComponentes:['NOMBRE','CU'],apellidosComponentes:['APELLIDO','CU']},environment:'SANDBOX',loginAvailable:true}:null} full={full} filePort={files} onStep={step} onReview={review} />);return {review,step,files};
}
async function identity(cu=false){
  if(cu){await screen.findByDisplayValue('NOMBRE');return;}
  await userEvent.type(screen.getByRole('textbox',{name:/^RUN/}),'12345678');await userEvent.type(screen.getByRole('textbox',{name:'Dígito verificador del RUN'}),'5');await userEvent.click(screen.getByRole('button',{name:'Revisar RUN'}));
  await userEvent.type(await screen.findByRole('textbox',{name:/^Primer nombre/}),'NOMBRE');await userEvent.type(screen.getByRole('textbox',{name:/^Primer apellido/}),'APELLIDO');
}
async function reason(value:string,topic='general'){
  await userEvent.click(screen.getByRole('button',{name:'Continuar'}));await userEvent.selectOptions(byId('motivo'),value);
  if(value==='consulta'){
    const dialog=await screen.findByRole('dialog');await userEvent.click(within(dialog).getByRole('checkbox',{name:/Leí esta orientación/}));await userEvent.click(within(dialog).getByRole('button',{name:'Entendido, continuar'}));await userEvent.selectOptions(byId('tema'),topic);
  }
}
async function last(){await userEvent.click(screen.getByRole('button',{name:'Continuar'}));await screen.findByRole('heading',{name:'Cuéntanos sobre tu comunicación'});}
describe('Recorridos adultos trasladados del mockup',()=>{
  it('sin CU completa consulta general y prepara captura con identidad declarada persistida',async()=>{
    const {review}=mount();await identity();await reason('consulta');await last();await userEvent.type(byId('consulta-general-detalle'),'Consulta sintética');await userEvent.selectOptions(byId('adult-region'),'13');await userEvent.click(screen.getByRole('button',{name:'Revisar respuestas'}));
    await waitFor(()=>expect(review).toHaveBeenCalledTimes(1));expect(review.mock.calls[0][0]).toEqual({campos:{motivo:'consulta',tema:'general','consulta-confirmada':'true','consulta-general-detalle':'Consulta sintética','adult-region':'13'},personas:[],instituciones:[],adjuntos:[]});expect(review.mock.calls[0][1].autenticada).toBe(false);
    expect(screen.queryByText(/Cargar datos de demostración/)).toBeNull();expect(screen.queryByText(/Resultado SRCeI.*simulado/)).toBeNull();
  });
  it('CU conserva nombre de sesión de solo lectura y valida el otro perfil de entrevista',async()=>{
    const {review}=mount(false,undefined,true);await identity(true);expect((screen.getByDisplayValue('NOMBRE') as HTMLInputElement).readOnly).toBe(true);await reason('consulta','entrevista');await last();
    await userEvent.type(byId('entrevista-motivo'),'Entrevista sintética');await userEvent.selectOptions(byId('entrevista-rol'),'otro');await userEvent.selectOptions(byId('adult-region'),'13');await userEvent.click(screen.getByRole('button',{name:'Revisar respuestas'}));expect(review).not.toHaveBeenCalled();
    await userEvent.type(byId('entrevista-rol-otro'),'Perfil sintético');await userEvent.click(screen.getByRole('button',{name:'Revisar respuestas'}));await waitFor(()=>expect(review).toHaveBeenCalledTimes(1));expect(review.mock.calls[0][1].autenticada).toBe(true);expect(review.mock.calls[0][0].campos['entrevista-rol-otro']).toBe('Perfil sintético');
  });
  it('estadísticas se habilita solo tras revisar fuentes y orientación',async()=>{
    const {review,step}=mount();await identity();await reason('consulta','estadisticas');await userEvent.click(screen.getByRole('button',{name:'Continuar'}));expect(step).not.toHaveBeenCalledWith(3);
    await userEvent.click(screen.getByRole('checkbox',{name:/Ya revisé el material/}));await last();await userEvent.type(byId('stats-descripcion'),'Datos no publicados');await userEvent.type(byId('stats-periodo'),'2025');await userEvent.selectOptions(byId('adult-region'),'13');await userEvent.click(screen.getByRole('button',{name:'Revisar respuestas'}));await waitFor(()=>expect(review).toHaveBeenCalledTimes(1));expect(review.mock.calls[0][0].campos['stats-confirm']).toBe('true');
  });
  it('empleo muestra el canal oficial y no permite revisar una comunicación',async()=>{
    const {review,step}=mount();await identity();await reason('consulta','empleo');expect(screen.getByRole('link',{name:/Ir a Empleos Públicos/}).getAttribute('href')).toBe('https://www.empleospublicos.cl/');await userEvent.click(screen.getByRole('button',{name:'Continuar'}));expect(step).not.toHaveBeenCalledWith(3);expect(review).not.toHaveBeenCalled();expect(screen.queryByRole('button',{name:'Revisar respuestas'})).toBeNull();
  });
  it('promoción exige organización cuando corresponde y conserva actividad, público y relato',async()=>{
    const {review}=mount();await identity();await reason('promocion');await last();await userEvent.click(screen.getByRole('radio',{name:'Sí'}));await userEvent.selectOptions(byId('actividad'),'Taller');await userEvent.type(byId('publico'),'Adolescentes');await userEvent.type(byId('relato'),'Relato sintético');await userEvent.selectOptions(byId('adult-region'),'13');
    await userEvent.click(screen.getByRole('button',{name:'Revisar respuestas'}));expect(review).not.toHaveBeenCalled();await userEvent.selectOptions(byId('tipo-organizacion'),'ONG o fundación');await userEvent.type(byId('nombre-organizacion'),'Entidad sintética');await userEvent.click(screen.getByRole('button',{name:'Revisar respuestas'}));await waitFor(()=>expect(review).toHaveBeenCalledTimes(1));expect(review.mock.calls[0][0].campos['nombre-organizacion']).toBe('Entidad sintética');
  });
  it('vulneración captura NNA desconocido, adulto relacionado e institución sin inventar identidades',async()=>{
    const {review}=mount();await identity();await reason('vulneracion');await last();await userEvent.click(byId('riesgo-no'));await userEvent.selectOptions(byId('relacion'),'testigo');await userEvent.type(byId('relato'),'Relato sintético');await userEvent.selectOptions(byId('adult-region'),'13');
    await userEvent.click(screen.getByRole('checkbox',{name:/No conozco otros datos/}));await userEvent.click(screen.getByRole('button',{name:/Agregar persona adulta/}));await userEvent.selectOptions(byId('ra-relationship-1'),'Testigo');
    await userEvent.click(byId('identifica-responsables'));await userEvent.selectOptions(byId('sb-kind-1'),'institucion');await userEvent.type(byId('sb-entity-1'),'Entidad sintética');await userEvent.click(screen.getByRole('button',{name:'Revisar respuestas'}));await waitFor(()=>expect(review).toHaveBeenCalledTimes(1));
    const capture=review.mock.calls[0][0];expect(capture.personas).toEqual([{personaId:'afectado-1',detalles:{datosDesconocidos:'true'}},{personaId:'relacionado-1',detalles:{relacion:'Testigo'}}]);expect(capture.instituciones[0].nombre).toBe('Entidad sintética');expect(capture.personas.some((p:{personaId:string})=>p.personaId.startsWith('senalado'))).toBe(false);
  });
  it('un archivo rechazado bloquea revisión y puede retirarse sin perder el formulario',async()=>{
    const files={replace:vi.fn().mockRejectedValue(new ApiError(400)),clear:vi.fn().mockResolvedValue(undefined)};const {review}=mount(false,files);await identity();await reason('consulta');await last();await userEvent.type(byId('consulta-general-detalle'),'Consulta sintética');await userEvent.selectOptions(byId('adult-region'),'13');
    await userEvent.upload(byId('adjuntos'),new File(['texto'],'respaldo.txt',{type:'text/plain'}));await screen.findAllByText(/No pudimos recibir estos archivos/);await userEvent.click(screen.getByRole('button',{name:'Revisar respuestas'}));expect(review).not.toHaveBeenCalled();await userEvent.click(screen.getByRole('button',{name:'Quitar archivos seleccionados'}));await userEvent.click(screen.getByRole('button',{name:'Revisar respuestas'}));await waitFor(()=>expect(review).toHaveBeenCalledTimes(1));expect(files.clear).toHaveBeenCalledTimes(1);
  });
  it('en vista completa conserva una tarjeta si falla su retiro y la quita solo tras confirmar backend',async()=>{
    mount(true);await identity();await userEvent.selectOptions(byId('motivo'),'vulneracion');await userEvent.click(screen.getByRole('button',{name:/Agregar persona adulta/}));
    const card=await screen.findByRole('region',{name:'Persona adulta relacionada 1'});vi.mocked(resetPerson).mockRejectedValueOnce(new ApiError(503));
    await userEvent.click(within(card).getByRole('button',{name:'Quitar'}));await screen.findByText(/No pudimos quitar esta persona/);expect(byId('relacionado-1')).toBeTruthy();
    await userEvent.click(within(card).getByRole('button',{name:'Quitar'}));await waitFor(()=>expect(document.getElementById('relacionado-1')).toBeNull());expect(resetPerson).toHaveBeenLastCalledWith(journey.id,'relacionado-1');
  });
});
