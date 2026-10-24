import { StrictMode } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import NnaPage from './NnaPage';
import type { IdentityView, Journey } from './api';
import type { Attachment } from './NnaFlow';
import type { SubmissionState } from './submission';

const journey:Journey={id:'11111111-1111-4111-8111-111111111111',tipo:'NNA',avisoVersion:'NNA_LAB_1',aceptaCanal:false,estado:'BORRADOR',venceEn:'2026-10-24T00:00:00Z'};
const proof={algoritmo:'Ed25519',keyId:'test',payload:'sello-sintetico',firma:'firma-sintetica'};
let created:boolean,identity:IdentityView|null,received:SubmissionState|null,attachments:Attachment[],available:boolean,loseResponse:boolean,failResponse:boolean;
const sends:{key:string;body:unknown}[]=[];
const response=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
beforeEach(()=>{
  created=false;identity=null;received=null;attachments=[];available=true;loseResponse=false;failResponse=false;sends.length=0;localStorage.clear();
  HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');};
  vi.spyOn(window,'fetch').mockImplementation(async(input,init)=>{
    const path=String(input),method=init?.method||'GET',body=typeof init?.body==='string'?JSON.parse(init.body):null;
    if(path==='/api/csrf')return response({token:'csrf-test',parameterName:'_csrf',headerName:'X-CSRF-TOKEN'});
    if(path==='/api/session')return response({authenticated:false,identity:null,environment:'SANDBOX',loginAvailable:true});
    if(path==='/api/formulario/capacidades')return response({disponible:true,avisoNna:'NNA_LAB_1',avisoAdulto:'ADULTO_LAB_1'});
    if(path==='/api/formulario/recepcion/capacidades')return response({nna:available,adultos:false});
    if(path==='/api/formulario/actual')return created?response({...journey,estado:received?'RECIBIDO':'BORRADOR'}):response({},404);
    if(path==='/api/formulario/recorridos'&&method==='POST'){created=true;return response(journey);}
    if(path.endsWith('/personas/presentador-nna')&&method==='GET')return identity?response(identity):response({},404);
    if(path.endsWith('/identificacion')&&method==='POST'){
      identity={personaId:'presentador-nna',estado:'REVISADO',autenticada:false,runInformado:!!body.runNumero,camposFaltantes:['primerNombre','primerApellido','segundoNombre','segundoApellido','edad'],camposObligatorios:['primerNombre','primerApellido'],valoresDeclarados:{},mensaje:'Completa tus datos.'};return response(identity);
    }
    if(path.endsWith('/declaracion')&&method==='POST'){identity={...identity!,valoresDeclarados:body.atributos};return response(identity);}
    if(path.endsWith('/adjuntos')){
      if(method==='POST'){expect(init?.body).toBeInstanceOf(FormData);expect((init?.headers as Record<string,string>)['Content-Type']).toBeUndefined();
        attachments=[{id:'22222222-2222-4222-8222-222222222222',nombre:'respaldo.txt',tamanio:5,sha256:'a'.repeat(64),estado:'VALIDADO'}];}
      if(method==='DELETE'){attachments=[];return new Response(null,{status:204});}return response(attachments);
    }
    if(path.endsWith('/comunicaciones')&&method==='POST'){
      const headers=init?.headers as Record<string,string>;expect(headers['X-CSRF-TOKEN']).toBe('csrf-test');sends.push({key:headers['Idempotency-Key'],body});
      if(failResponse){failResponse=false;return response({},503);}
      received={id:headers['Idempotency-Key'],estado:'PENDIENTE_REGISTRO_SIGER',custodia:proof,registro:null};
      if(loseResponse){loseResponse=false;throw new TypeError('Respuesta perdida controlada');}return response(received);
    }
    if(path.endsWith('/comunicacion')||path.startsWith('/api/formulario/comunicaciones/'))return received?response(received):response({},404);
    throw new Error(`Solicitud inesperada en prueba: ${path}`);
  });
});
afterEach(()=>{vi.restoreAllMocks();localStorage.clear();});
async function fill(){
  await userEvent.type(await screen.findByRole('textbox',{name:/^Primer nombre/}),'NOMBRE');
  await userEvent.type(screen.getByRole('textbox',{name:/^Primer apellido/}),'APELLIDO');
  await userEvent.selectOptions(screen.getByRole('combobox',{name:/^Región/}),'13');
  await userEvent.selectOptions(screen.getByRole('combobox',{name:/¿Cómo podemos contactarte/}),'unsafe');
  await userEvent.click(screen.getByRole('button',{name:'Continuar'}));
  await userEvent.type(await screen.findByRole('textbox',{name:/^Cuéntanos qué pasó/}),'Relato sintético para enviar.');
}
async function review(){await userEvent.click(screen.getByRole('button',{name:'Revisar mi comunicación'}));return await screen.findByRole('dialog');}
describe('Página NNA con API completa controlada',()=>{
  it('revisa antes de enviar, usa persistencia real por API y no presenta folio sin ACK',async()=>{
    render(<StrictMode><NnaPage /></StrictMode>);await fill();
    expect(vi.mocked(fetch).mock.calls.filter(([p,init])=>String(p)==='/api/formulario/recorridos'&&init?.method==='POST')).toHaveLength(1);
    expect(screen.queryByText(/Resultado SRCeI.*simulado/)).toBeNull();expect(screen.queryByText(/Cargar datos de demostración/)).toBeNull();
    const dialog=await review();expect(within(dialog).getByText('Relato sintético para enviar.')).toBeTruthy();expect(sends).toHaveLength(0);
    await userEvent.click(within(dialog).getByRole('button',{name:'Enviar comunicación'}));expect(await screen.findByRole('heading',{name:'Recibimos tu comunicación'})).toBeTruthy();
    expect(sends).toHaveLength(1);expect(sends[0].body).toEqual({campos:{'nna-region':'13','nna-primary-method':'unsafe','nna-story':'Relato sintético para enviar.'},personas:[],instituciones:[],adjuntos:[]});
    expect(screen.getByRole('button',{name:'Descargar comprobante de recepción'})).toBeTruthy();expect(screen.queryByRole('button',{name:'Descargar comprobante institucional'})).toBeNull();
    expect(Object.keys(localStorage).sort()).toEqual(['siger:font-scale','siger:theme']);expect(Object.values(localStorage).join(' ')).not.toContain('NOMBRE');
  });
  it('no confirma un envío fallido y reintenta exactamente la misma clave y captura',async()=>{
    failResponse=true;render(<NnaPage />);await fill();const dialog=await review();await userEvent.click(within(dialog).getByRole('button',{name:'Enviar comunicación'}));
    expect(await within(dialog).findByRole('alert')).toBeTruthy();expect(screen.queryByRole('heading',{name:'Recibimos tu comunicación'})).toBeNull();
    await userEvent.click(within(dialog).getByRole('button',{name:'Reintentar el mismo envío'}));await screen.findByRole('heading',{name:'Recibimos tu comunicación'});
    expect(sends).toHaveLength(2);expect(sends[0]).toEqual(sends[1]);
  });
  it('recupera una respuesta perdida sin duplicar el POST',async()=>{
    loseResponse=true;render(<NnaPage />);await fill();const dialog=await review();await userEvent.click(within(dialog).getByRole('button',{name:'Enviar comunicación'}));
    await userEvent.click(await within(dialog).findByRole('button',{name:'Comprobar recepción'}));await screen.findByRole('heading',{name:'Recibimos tu comunicación'});expect(sends).toHaveLength(1);
  });
  it('recarga un recorrido recibido y recupera el estado sin reconstruir el formulario',async()=>{
    created=true;received={id:'33333333-3333-4333-8333-333333333333',estado:'PENDIENTE_REGISTRO_SIGER',custodia:proof,registro:null};
    render(<NnaPage />);await screen.findByRole('heading',{name:'Recibimos tu comunicación'});expect(screen.queryByRole('textbox',{name:/^Primer nombre/})).toBeNull();
    expect(vi.mocked(fetch).mock.calls.filter(([p])=>String(p).endsWith('/identificacion'))).toHaveLength(0);expect(sends).toHaveLength(0);
  });
  it('adjunta por multipart y referencia únicamente los archivos confirmados por servidor',async()=>{
    render(<NnaPage />);await fill();await userEvent.upload(screen.getByLabelText(/¿Quieres agregar fotos o archivos/),new File(['texto'],'respaldo.txt',{type:'text/plain'}));
    await screen.findByText('respaldo.txt');const dialog=await review();expect(within(dialog).getByText(/respaldo.txt/)).toBeTruthy();
    await userEvent.click(within(dialog).getByRole('button',{name:'Enviar comunicación'}));await screen.findByRole('heading',{name:'Recibimos tu comunicación'});
    expect((sends[0].body as {adjuntos:string[]}).adjuntos).toEqual(['22222222-2222-4222-8222-222222222222']);
  });
  it('recepción cerrada permite revisar sin inventar una confirmación ni enviar',async()=>{
    available=false;render(<NnaPage />);await fill();expect(screen.getByText(/La recepción está temporalmente cerrada/)).toBeTruthy();const dialog=await review();
    await userEvent.click(within(dialog).getByRole('button',{name:'Enviar comunicación'}));expect(await within(dialog).findByRole('alert')).toBeTruthy();expect(sends).toHaveLength(0);
    await userEvent.click(within(dialog).getByRole('button',{name:'Volver al formulario'}));expect(screen.queryByRole('dialog')).toBeNull();
  });
});
