// Flujo NNA real local con RUN sintético fuera de allowlist: nunca debe llegar al proveedor.
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../formulario-publico/front/package.json',import.meta.url)),{chromium}=require('playwright');
const root=fileURLToPath(new URL('../../',import.meta.url)),output=path.join(root,'.local/evidencias/run-srcei-dev');
assert(process.env.RUN_DEV_AUTHORIZATION);const authorized=JSON.parse(await fs.readFile(process.env.RUN_DEV_AUTHORIZATION,'utf8'));assert.notEqual(String(authorized.numero),'12345678');
const origin='https://claveunica.formularioadulto.defensorianinez.gob.cl',browser=await chromium.launch({headless:true,channel:'msedge'}),result={proveedorReal:false,etapa:'inicio'};
try{
 const context=await browser.newContext({viewport:{width:390,height:844},ignoreHTTPSErrors:false}),page=await context.newPage(),requests=[];
 page.on('request',r=>{if(r.method()==='POST'&&r.url().includes('/personas/presentador-nna/identificacion'))requests.push(true);});
 await page.goto(origin+'/?vista=formulario-nna',{waitUntil:'domcontentloaded'});await page.locator('#nna-names').waitFor();const journey=await (await context.request.get(origin+'/api/formulario/actual')).json();result.recorrido=journey.id;
 assert.equal(await page.locator('#nna-run').isVisible(),false);await page.locator('#nna-names').fill('NOMBRE');await page.locator('#nna-first').fill('APELLIDO');await page.locator('#nna-region').selectOption('13');await page.locator('#nna-primary-method').selectOption('unsafe');await page.locator('#nna-next').click();await page.locator('#nna-story').waitFor();result.sinRun=true;
 await page.locator('#nna-back').click();await page.getByRole('radio',{name:'Sí, quiero ingresarlo'}).check();result.etapa='voluntario';await page.locator('#nna-run').fill('12345678');await page.locator('#nna-run-dv').fill('5');await page.locator('#nna-names').waitFor();assert.equal(requests.length,2);
 await page.locator('#nna-names').fill('NOMBRE');await page.locator('#nna-first').fill('APELLIDO');await page.locator('#nna-next').click();await page.locator('#nna-story').waitFor();
 await page.reload({waitUntil:'domcontentloaded'});await page.getByText(/Ya informaste un RUN/).waitFor();assert.equal(await page.locator('#nna-names').inputValue(),'NOMBRE');assert.equal(await page.locator('#nna-first').inputValue(),'APELLIDO');assert.equal(requests.length,2);result.voluntarioAutomatico=true;result.restauracionSinPost=true;result.declaracionesConservadas=true;
 await page.locator('#nna-region').selectOption('13');await page.locator('#nna-primary-method').selectOption('unsafe');await page.locator('#nna-next').click();await page.locator('#nna-story').fill('Relato sintético NNA de regresión del RUN voluntario');await page.locator('#nna-review').click();await page.getByRole('dialog').waitFor();result.etapa='recepcion';const sent=page.waitForRequest(r=>r.url().endsWith('/comunicaciones')&&r.method()==='POST');await page.getByRole('button',{name:'Enviar comunicación',exact:true}).click();const request=await sent;await page.locator('#communication-receipt').waitFor();result.comunicacion=request.headers()['idempotency-key'];assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await page.screenshot({path:path.join(output,'nna-custodia-movil.png'),fullPage:true});result.etapa='completado';console.log('NNA: sin RUN, RUN voluntario sintético automático, restauración y recepción local correctos.');
}catch{result.fallo=true;process.exitCode=1;console.log('Regresión NNA detenida en etapa '+result.etapa);}
finally{await fs.writeFile(path.join(output,'nna.json'),JSON.stringify(result,null,2));await browser.close();}
