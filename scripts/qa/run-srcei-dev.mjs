// Una consulta DEV autorizada desde el formulario. No usar como campaña ni con datos productivos.
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../formulario-publico/front/package.json',import.meta.url)),{chromium}=require('playwright');
const root=fileURLToPath(new URL('../../',import.meta.url)),output=path.join(root,'.local/evidencias/run-srcei-dev');
assert(process.env.RUN_DEV_AUTHORIZATION,'Indicar la allowlist privada autorizada fuera de Git');
const authorization=path.resolve(process.env.RUN_DEV_AUTHORIZATION);assert(!authorization.startsWith(root));
const authorized=JSON.parse(await fs.readFile(authorization,'utf8'));assert(!Array.isArray(authorized));
const statePath=path.join(path.dirname(authorization),'qa-formulario-browser.json'),resume=process.env.RUN_DEV_RESUME==='1';
const origin='https://claveunica.formularioadulto.defensorianinez.gob.cl';await fs.mkdir(output,{recursive:true});
const browser=await chromium.launch({headless:true,channel:'msedge'}),result={proveedorReal:true,consultaAcotada:true,resume,etapa:'inicio'};
let context;
try{
 context=await browser.newContext({viewport:{width:1440,height:900},ignoreHTTPSErrors:false,...(resume?{storageState:statePath}:{})});
 const page=await context.newPage(),errors=[],requests=[];page.on('pageerror',()=>errors.push('ERROR_JAVASCRIPT'));page.on('request',r=>{if(r.method()==='POST'&&r.url().includes('/personas/presentador-adulto/identificacion'))requests.push(true);});
 await page.goto(origin+'/?vista=formulario-adulto&modo=manual',{waitUntil:'domcontentloaded'});
 if(!resume){const d=page.getByRole('dialog');await d.waitFor();await d.getByRole('radio',{name:/^Acepto/}).check();await d.getByRole('button',{name:'Continuar',exact:true}).click();}
 await page.locator('#identificacion').waitFor();const journey=await (await context.request.get(origin+'/api/formulario/actual')).json();result.recorrido=journey.id;
 await fs.writeFile(statePath,JSON.stringify(await context.storageState()),{mode:0o600});
 if(!resume){
  result.etapa='disparador';const response=page.waitForResponse(r=>r.url().endsWith('/personas/presentador-adulto/identificacion')&&r.request().method()==='POST');
  await page.locator('#adult-run').fill(String(authorized.numero));assert.equal(requests.length,0);await page.locator('#adult-run-dv').fill(String(authorized.dv));
  const received=await response;assert.equal(received.status(),200);const projection=await received.json();
  assert.equal(Object.keys(projection.valoresDeclarados).length,0);assert.equal(projection.autenticada,false);
  assert(Object.keys(projection).every(k=>['personaId','estado','autenticada','runInformado','camposFaltantes','camposObligatorios','valoresDeclarados','mensaje'].includes(k)));
  assert.equal(projection.mensaje,'Completa los datos solicitados. La revisión del RUN no acredita tu identidad.');
  result.faltantes=projection.camposFaltantes;result.sinAtributosRegistrales=true;result.disparadorSinBoton=requests.length===1;
  await page.reload({waitUntil:'domcontentloaded'});await page.getByText(/Ya informaste un RUN/).waitFor();assert.equal(requests.length,1);
 }else{await page.getByText(/Ya informaste un RUN/).waitFor();assert.equal(requests.length,0);}
 result.restauracionSinReconsulta=true;
 const view=await (await context.request.get(origin+`/api/formulario/recorridos/${journey.id}/personas/presentador-adulto`)).json();result.faltantes=view.camposFaltantes;
 for(const [field,id,value] of [['primerNombre','names','NOMBRE'],['segundoNombre','middle','PROPIO'],['primerApellido','first','APELLIDO'],['segundoApellido','second','DECLARADO']])if(view.camposFaltantes.includes(field))await page.locator('#adult-'+id).fill(value);
 result.etapa='declaracion';await page.locator('#adult-next').click();await page.locator('#motivo').waitFor();
 const csrf=await (await context.request.get(origin+'/api/csrf')).json();const headers={[csrf.headerName]:csrf.token};
 const third=await context.request.post(origin+`/api/formulario/recorridos/${journey.id}/personas/relacionado-1/identificacion`,{headers,data:{rol:'ADULTO_RELACIONADO',runNumero:String(authorized.numero),runDv:String(authorized.dv)}});assert.equal(third.status(),200);const t=await third.json();assert.equal(t.autenticada,false);assert(t.camposFaltantes.includes('primerNombre'));
 const own=await context.request.post(origin+`/api/formulario/recorridos/${journey.id}/personas/relacionado-1/declaracion`,{headers,data:{atributos:{primerNombre:'PROPIO',primerApellido:'DECLARADO'}}});assert.equal(own.status(),200);
 const restored=await (await context.request.get(origin+`/api/formulario/recorridos/${journey.id}/personas/relacionado-1`)).json();assert.equal(restored.valoresDeclarados.primerNombre,'PROPIO');result.declaracionTerceroConservada=true;
 await page.locator('#motivo').selectOption('consulta');await page.getByRole('checkbox',{name:/Leí esta orientación/}).check();await page.getByRole('button',{name:'Entendido, continuar'}).click();await page.locator('#tema').selectOption('general');await page.locator('#adult-next').click();await page.locator('#consulta-general-detalle').fill('Consulta sintética de integración automática DEV');await page.locator('#adult-region').selectOption('13');await page.locator('#revisar').click();await page.getByRole('dialog').waitFor();
 result.etapa='recepcion';const sent=page.waitForRequest(r=>r.url().endsWith('/comunicaciones')&&r.method()==='POST');await page.getByRole('button',{name:'Enviar comunicación',exact:true}).click();const request=await sent;await page.locator('#communication-receipt').waitFor();assert.match(await page.locator('#communication-receipt').innerText(),/registro institucional está pendiente/);
 result.comunicacion=request.headers()['idempotency-key'];
 const repeated=await context.request.post(request.url(),{headers:{...headers,'Idempotency-Key':result.comunicacion},data:request.postDataJSON()});assert.equal(repeated.status(),200);
 const conflict=await context.request.post(request.url(),{headers:{...headers,'Idempotency-Key':result.comunicacion},data:{...request.postDataJSON(),campos:{...request.postDataJSON().campos,'adult-address':'cambio'}}});assert.equal(conflict.status(),409);result.idempotencia=true;
 for(const [size,width,height] of [['escritorio',1440,900],['movil',390,844]]){await page.setViewportSize({width,height});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await page.screenshot({path:path.join(output,`custodia-${size}.png`),fullPage:true});}
 assert.deepEqual(errors,[]);result.erroresJs=0;result.etapa='completado';console.log('DEV desde formulario: disparador, proyección privada, restauración, declaración y recepción/idempotencia verificados.');
}catch{result.fallo=true;process.exitCode=1;console.log('Verificación DEV detenida en etapa '+result.etapa+'. Conservar sesión privada; no repetir la consulta sin revisar el estado persistido.');}
finally{await fs.writeFile(path.join(output,'resultado.json'),JSON.stringify(result,null,2));await browser.close();}
