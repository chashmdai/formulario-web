import {afterEach,describe,expect,it,vi} from 'vitest';
import {getSubmission} from './submission';
afterEach(()=>{vi.restoreAllMocks();if(vi.isFakeTimers()){vi.clearAllTimers();vi.useRealTimers();}});
describe('Consulta del estado existente',()=>{
 it('solo hace GET privado sin caché y libera su temporizador tras responder',async()=>{
  vi.useFakeTimers();
  const value={id:'test',estado:'PENDIENTE_REGISTRO_SIGER',custodia:{algoritmo:'Ed25519'},registro:null};
  const fetch=vi.spyOn(window,'fetch').mockResolvedValue(new Response(JSON.stringify(value)));
  expect(await getSubmission('id con espacios')).toEqual(value);
  expect(fetch).toHaveBeenCalledWith('/api/formulario/comunicaciones/id%20con%20espacios',expect.objectContaining({credentials:'same-origin',cache:'no-store',signal:expect.any(AbortSignal)}));
  expect(fetch.mock.calls[0][1]?.method).toBeUndefined();expect(vi.getTimerCount()).toBe(0);
 });
 it('corta una consulta bloqueada a los doce segundos para permitir el próximo intento',async()=>{
  vi.useFakeTimers();
  vi.spyOn(window,'fetch').mockImplementation((_url,init)=>new Promise((_resolve,reject)=>{
   init!.signal!.addEventListener('abort',()=>reject(new DOMException('Timeout','AbortError')),{once:true});
  }));
  const pending=getSubmission('test');const rejection=expect(pending).rejects.toMatchObject({name:'AbortError'});
  await vi.advanceTimersByTimeAsync(12000);await rejection;expect(vi.getTimerCount()).toBe(0);
 });
});
