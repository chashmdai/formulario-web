import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {downloadReceiptPdf,prepareReceipt} from './download';
import {RegistrationNotConfirmedError} from './evidence';
import {adultSummary,b64url,pending,registered,registryContent} from './fixtures.test-utils';

const render=vi.hoisted(()=>({calls:0}));
vi.mock('./pdf',()=>({renderReceiptPdf:async()=>{render.calls++;return new Uint8Array([37,80,68,70,45]);}}));
vi.mock('./assets',()=>({loadPdfAssets:async()=>({fonts:{},logoSvg:''})}));
const response=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
let summary:unknown;
beforeEach(()=>{render.calls=0;summary=null;vi.spyOn(window,'fetch').mockImplementation(async()=>summary?response(summary):response({},404));
  URL.createObjectURL=vi.fn(()=>'blob:controlado');URL.revokeObjectURL=vi.fn();vi.spyOn(HTMLAnchorElement.prototype,'click').mockImplementation(()=>{});});
afterEach(()=>vi.restoreAllMocks());

describe('Generación directa del comprobante PDF',()=>{
  const incoherent=[
    {...registered,registro:{...registered.registro!,registro:{folio:'COM-999999'}}},
    {...registered,registro:{...registered.registro!,comprobante:{...registered.registro!.comprobante,payload:b64url({...registryContent,operacionPublicaId:'99999999-9999-4999-8999-999999999999'})}}},
  ];
  it('rechaza un registro pendiente o incoherente sin consultar el servidor, cargar el generador ni descargar',async()=>{
    for(const state of [pending,...incoherent]){
      await expect(downloadReceiptPdf(state)).rejects.toThrow(RegistrationNotConfirmedError);
      await expect(prepareReceipt(state)).rejects.toThrow(RegistrationNotConfirmedError);
    }
    expect(fetch).not.toHaveBeenCalled();expect(render.calls).toBe(0);expect(URL.createObjectURL).not.toHaveBeenCalled();
  });
  it('rechaza si el resumen del servidor aún informa un registro pendiente',async()=>{
    summary=adultSummary(pending,{motivo:'consulta'});
    await expect(downloadReceiptPdf(registered)).rejects.toThrow(RegistrationNotConfirmedError);expect(render.calls).toBe(0);
  });
  it('descarta el documento si el estado vigente deja de estar confirmado durante la preparación',async()=>{
    summary=adultSummary(registered,{motivo:'consulta'});
    for(const latest of [pending,...incoherent,{...registered,id:'99999999-9999-4999-8999-999999999999'}]){
      await expect(downloadReceiptPdf(registered,()=>latest)).rejects.toThrow(RegistrationNotConfirmedError);
    }
    expect(render.calls).toBe(4);expect(URL.createObjectURL).not.toHaveBeenCalled();
  });
  it('con registro confirmado genera el PDF completo o reducido',async()=>{
    summary=adultSummary(registered,{motivo:'consulta'});
    expect((await downloadReceiptPdf(registered)).reduced).toBeNull();
    summary=null;const reduced=await downloadReceiptPdf(registered);
    expect(reduced.reduced).toBe('NO_DISPONIBLE');expect(reduced.folio).toBe('COM-000123');expect(render.calls).toBe(2);
  });
});
