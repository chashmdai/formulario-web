import { StrictMode } from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import AdultPage from './AdultPage';
import Entry from '../Entry';
import type { IdentityView, Journey } from './api';
import type { SubmissionState } from './submission';
const base:Journey={id:'11111111-1111-4111-8111-111111111111',tipo:'ADULTO_DECLARADO',avisoVersion:'ADULTO_LAB_1',aceptaCanal:true,estado:'BORRADOR',venceEn:'2026-10-25T00:00:00Z'};
const proof={algoritmo:'Ed25519',keyId:'test',payload:'sello-sintetico',firma:'firma-sintetica'};
let exists:boolean,cu:boolean,loggedOut:boolean,available:boolean,lost:boolean,failed:boolean,received:SubmissionState|null;
const people=new Map<string,IdentityView>(),posts:{key:string;body:unknown}[]=[];
const response=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
const byId=(id:string)=>document.getElementById(id)!;
beforeEach(()=>{
  exists=false;cu=false;loggedOut=false;available=true;lost=false;failed=false;received=null;people.clear();posts.length=0;localStorage.clear();window.history.replaceState({},'','/');
  HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');};
  vi.spyOn(window,'fetch').mockImplementation(async(input,init)=>{
    const path=String(input),method=init?.method||'GET',body=typeof init?.body==='string'?JSON.parse(init.body):null;
    if(path==='/api/csrf')return response({token:'csrf-controlado',headerName:'X-CSRF-TOKEN',parameterName:'_csrf'});
    if(path==='/api/session')return response({authenticated:cu&&!loggedOut,identity:cu&&!loggedOut?{run:'12345678-5',nombres:'NOMBRE CU',apellidos:'APELLIDO CU',nombresComponentes:['NOMBRE','CU'],apellidosComponentes:['APELLIDO','CU']}:null,environment:'SANDBOX',loginAvailable:true});
    if(path==='/api/formulario/recepcion/capacidades')return response({nna:true,adultos:available});
    if(path==='/api/formulario/capacidades')return response({disponible:true,avisoAdulto:'ADULTO_LAB_1',avisoNna:'NNA_LAB_1'});
    if(path==='/api/formulario/actual')return exists?response({...base,tipo:cu?'ADULTO_CU':'ADULTO_DECLARADO',estado:received?'RECIBIDO':'BORRADOR'}):response({},404);
    if(path==='/api/formulario/recorridos'&&method==='POST'){exists=true;expect(body.aceptaCanalDigital).toBe(true);expect(body.avisoVersion).toBe('ADULTO_LAB_1');return response({...base,tipo:body.tipo});}
    if(path.endsWith('/adjuntos'))return response([]);
    const person=/\/personas\/([^/]+)(?:\/(identificacion|declaracion))?$/.exec(path);
    if(person){const id=person[1];if(method==='GET')return people.has(id)?response(people.get(id)):response({},404);
      if(person[2]==='identificacion'){const v:IdentityView={personaId:id,estado:'REVISADO',autenticada:cu,runInformado:cu||!!body.runNumero,camposFaltantes:cu?['fechaNacimiento','sexo']:['primerNombre','segundoNombre','primerApellido','segundoApellido','fechaNacimiento','sexo'],camposObligatorios:cu?[]:['primerNombre','primerApellido'],valoresDeclarados:{},mensaje:'Completa los datos solicitados.'};people.set(id,v);return response(v);}
      const v={...people.get(id)!,valoresDeclarados:body.atributos};people.set(id,v);return response(v);
    }
    if(path.endsWith('/comunicaciones')&&method==='POST'){
      const headers=init?.headers as Record<string,string>;expect(headers['X-CSRF-TOKEN']).toBe('csrf-controlado');posts.push({key:headers['Idempotency-Key'],body});if(failed){failed=false;return response({},503);}
      received={id:headers['Idempotency-Key'],estado:'PENDIENTE_REGISTRO_SIGER',custodia:proof,registro:null};if(lost){lost=false;throw new TypeError('Respuesta perdida controlada');}return response(received);
    }
    if(path.endsWith('/comunicacion')||path.startsWith('/api/formulario/comunicaciones/'))return received?response(received):response({},404);
    throw new Error(`Ruta inesperada de prueba: ${path}`);
  });
});
afterEach(()=>{vi.restoreAllMocks();window.history.replaceState({},'','/');localStorage.clear();});
async function fill(){
  if(!exists){const privacy=await screen.findByRole('dialog');await userEvent.click(within(privacy).getByRole('radio',{name:/^Acepto/}));await userEvent.click(within(privacy).getByRole('button',{name:'Continuar'}));}
  if(cu){await screen.findByDisplayValue('NOMBRE');await waitFor(()=>expect((byId('adult-run') as HTMLInputElement).disabled).toBe(false));}else{
    await userEvent.type(await screen.findByRole('textbox',{name:/^RUN/}),'12345678');await userEvent.type(screen.getByRole('textbox',{name:'Dígito verificador del RUN'}),'5');await userEvent.click(screen.getByRole('button',{name:'Revisar RUN'}));
    await userEvent.type(await screen.findByRole('textbox',{name:/^Primer nombre/}),'NOMBRE');await userEvent.type(screen.getByRole('textbox',{name:/^Primer apellido/}),'APELLIDO');
  }
  await userEvent.click(screen.getByRole('button',{name:'Continuar'}));await waitFor(()=>expect(byId('motivo').closest('section')?.hidden).toBe(false));await waitFor(()=>expect((byId('motivo') as HTMLSelectElement).disabled).toBe(false));await userEvent.selectOptions(byId('motivo'),'consulta');const dialog=await screen.findByRole('dialog');await userEvent.click(within(dialog).getByRole('checkbox',{name:/Leí esta orientación/}));await userEvent.click(within(dialog).getByRole('button',{name:'Entendido, continuar'}));
  await userEvent.selectOptions(byId('tema'),'general');await userEvent.click(screen.getByRole('button',{name:'Continuar'}));await screen.findByRole('heading',{name:'Cuéntanos sobre tu comunicación'});await waitFor(()=>expect(byId('formulario').getAttribute('aria-busy')).toBe('false'));await userEvent.type(byId('consulta-general-detalle'),'no');await userEvent.selectOptions(byId('adult-region'),'13');await userEvent.click(screen.getByRole('button',{name:'Revisar respuestas'}));return screen.findByRole('dialog');
}
describe('Cierre de adulto con API controlada',()=>{
  it('exige aviso antes de identificar, revisa sin alterar el relato y recibe custodia sin folio inventado',async()=>{
    render(<StrictMode><AdultPage /></StrictMode>);await screen.findByRole('dialog');expect(screen.queryByRole('textbox',{name:/^RUN/})).toBeNull();const review=await fill();expect(within(review).getByText('no',{exact:true})).toBeTruthy();expect(posts).toHaveLength(0);
    await userEvent.click(within(review).getByRole('button',{name:'Enviar comunicación'}));await screen.findByRole('heading',{name:'Recibimos tu comunicación'});expect(posts).toHaveLength(1);expect(posts[0].body).toEqual({campos:{motivo:'consulta',tema:'general','consulta-confirmada':'true','consulta-general-detalle':'no','adult-region':'13'},personas:[],instituciones:[],adjuntos:[]});expect(screen.queryByRole('button',{name:'Descargar comprobante institucional'})).toBeNull();expect(Object.keys(localStorage).sort()).toEqual(['siger:font-scale','siger:theme']);
  });
  it('retoma CU desde el callback raíz y envía referencias sin autoafirmar identidad en el cuerpo',async()=>{
    exists=true;cu=true;render(<Entry />);const review=await fill();expect(within(review).getByText('NOMBRE CU')).toBeTruthy();expect(screen.getByRole('button',{name:'Cerrar sesión'})).toBeTruthy();await userEvent.click(within(review).getByRole('button',{name:'Enviar comunicación'}));await screen.findByRole('heading',{name:'Recibimos tu comunicación'});expect(JSON.stringify(posts[0].body)).not.toContain('NOMBRE CU');expect(vi.mocked(fetch).mock.calls.some(([url,init])=>String(url)==='/api/formulario/recorridos'&&init?.method==='POST')).toBe(false);
  });
  it('un error no confirma recepción y el reintento conserva exactamente clave y cuerpo',async()=>{
    failed=true;render(<AdultPage />);const review=await fill();await userEvent.click(within(review).getByRole('button',{name:'Enviar comunicación'}));await within(review).findByRole('alert');expect(screen.queryByRole('heading',{name:'Recibimos tu comunicación'})).toBeNull();await userEvent.click(within(review).getByRole('button',{name:'Reintentar el mismo envío'}));await screen.findByRole('heading',{name:'Recibimos tu comunicación'});expect(posts[0]).toEqual(posts[1]);
  });
  it('recupera la respuesta perdida sin repetir el envío',async()=>{
    lost=true;render(<AdultPage />);const review=await fill();await userEvent.click(within(review).getByRole('button',{name:'Enviar comunicación'}));await userEvent.click(await within(review).findByRole('button',{name:'Comprobar recepción'}));await screen.findByRole('heading',{name:'Recibimos tu comunicación'});expect(posts).toHaveLength(1);
  });
  it('recepción cerrada permite revisar y volver sin emitir una comunicación',async()=>{
    available=false;render(<AdultPage />);const review=await fill();await userEvent.click(within(review).getByRole('button',{name:'Enviar comunicación'}));expect(await within(review).findByRole('alert')).toBeTruthy();expect(posts).toHaveLength(0);await userEvent.click(within(review).getByRole('button',{name:'Volver al formulario'}));expect(screen.queryByRole('dialog')).toBeNull();
  });
  it('restaura la recepción de la sesión sin reconstruir el formulario ni enviar otra vez',async()=>{
    exists=true;received={id:'33333333-3333-4333-8333-333333333333',estado:'PENDIENTE_REGISTRO_SIGER',custodia:proof,registro:null};render(<AdultPage />);await screen.findByRole('heading',{name:'Recibimos tu comunicación'});expect(posts).toHaveLength(0);expect(screen.queryByRole('textbox',{name:/Primer nombre/})).toBeNull();
  });
  it('si la sesión CU termina retira el formulario y exige autenticación de nuevo',async()=>{
    exists=true;cu=true;render(<AdultPage mode="cu" />);await screen.findByDisplayValue('NOMBRE');loggedOut=true;fireEvent(window,new Event('focus'));await screen.findByRole('heading',{name:'Ingresa con tu ClaveÚnica'});await waitFor(()=>expect(screen.queryByDisplayValue('NOMBRE')).toBeNull());expect(posts).toHaveLength(0);
  });
});
