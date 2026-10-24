// Un único ingreso real Sandbox, únicamente con identidad de prueba autorizada fuera de Git.
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../formulario-publico/front/package.json',import.meta.url)),{chromium}=require('playwright');
const root=fileURLToPath(new URL('../../',import.meta.url)),output=path.join(root,'.local/evidencias/adultos-navegador');
assert(process.env.CU_TEST_CREDENTIALS,'Indicar archivo privado autorizado');
assert(!path.resolve(process.env.CU_TEST_CREDENTIALS).startsWith(root),'Credenciales fuera de Git');
const credentials=JSON.parse(await fs.readFile(process.env.CU_TEST_CREDENTIALS,'utf8'));
const origin='https://claveunica.formularioadulto.defensorianinez.gob.cl';
const logoutOnly=process.env.CU_LOGOUT_ONLY==='1';
const browser=await chromium.launch({headless:true,channel:'msedge'}),result={sandboxReal:true,recorridos:[]};
try{
 const context=await browser.newContext({viewport:{width:1440,height:900},ignoreHTTPSErrors:false}),page=await context.newPage();
 const errors=[];page.on('pageerror',e=>{if(page.url().startsWith(origin))errors.push(e.message);});
 await page.goto(origin+(logoutOnly?'/oauth2/authorization/claveunica':'/?vista=formulario-adulto&modo=cu'),{waitUntil:'domcontentloaded'});
 if(!logoutOnly){const privacy=page.getByRole('dialog');await privacy.waitFor();await privacy.getByRole('radio',{name:/^Acepto/}).check();await privacy.getByRole('button',{name:'Continuar',exact:true}).click();}
 await page.locator('input[type="password"]').waitFor({timeout:30000});
 assert.equal(new URL(page.url()).hostname,'accounts.claveunica.gob.cl');
 await page.waitForLoadState('load');
 const form=page.locator('form').filter({has:page.locator('input[type="password"]')});
 result.etapa='ingreso';await form.locator('input[type="text"]').first().pressSequentially(credentials.run,{delay:30});await form.locator('input[type="password"]').pressSequentially(credentials.password,{delay:30});await form.locator('input[type="password"]').press('Tab');
 await page.locator('#login-submit').click();
 result.etapa='callback';await page.waitForURL(url=>url.origin===origin,{timeout:45000,waitUntil:'domcontentloaded'});
 const session=await page.evaluate(async()=>await (await fetch('/api/session')).json());assert.equal(session.authenticated,true);assert(session.identity);result.callback=true;
 for(const [size,width,height] of (logoutOnly?[]:[['escritorio',1440,900],['movil',390,844]])){
  await page.setViewportSize({width,height});
  if(size==='movil')await page.locator('#adult-prev').click().then(()=>page.locator('#adult-prev').click());
  await page.locator('#adult-run').waitFor();assert.equal(await page.locator('#adult-run').getAttribute('readonly'),'');assert.equal(await page.locator('#adult-names').getAttribute('readonly'),'');
  await page.locator('#adult-next').click();await page.locator('#motivo').selectOption('consulta');if(await page.getByRole('dialog').isVisible()){await page.getByRole('checkbox',{name:/Leí esta orientación/}).check();await page.getByRole('button',{name:'Entendido, continuar'}).click();}await page.locator('#tema').selectOption('general');await page.locator('#adult-next').click();await page.locator('#consulta-general-detalle').fill('Consulta sintética autenticada '+size);await page.locator('#adult-region').selectOption('13');await page.locator('#revisar').click();await page.getByRole('dialog').waitFor();
  if(size==='escritorio'){result.recorridos.push({size,soloLectura:true,revision:true});await page.getByRole('button',{name:'Volver al formulario'}).click();continue;}
  const sent=page.waitForRequest(r=>r.url().endsWith('/comunicaciones')&&r.method()==='POST');await page.getByRole('button',{name:'Enviar comunicación',exact:true}).click();const request=await sent;await page.locator('#communication-receipt').waitFor();assert.match(await page.locator('#communication-receipt').innerText(),/registro en SIGER está pendiente/);
  const body=request.postDataJSON();assert(!JSON.stringify(body).includes(session.identity.run));assert(!JSON.stringify(body).includes(session.identity.nombres));
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  await page.screenshot({path:path.join(output,`cu-${size}-custodia.png`),fullPage:true});result.recorridos.push({size,id:request.headers()['idempotency-key'],soloLectura:true,sinIdentidadEnCaptura:true,custodia:true});
 }
 result.etapa='logout';await page.getByRole('button',{name:'Cerrar sesión',exact:true}).click();await page.waitForURL(url=>url.origin===origin&&url.searchParams.get('session')==='closed',{waitUntil:'domcontentloaded'});const anonymous=await page.evaluate(async()=>await (await fetch('/api/session')).json());assert.equal(anonymous.authenticated,false);result.logout=true;result.errores=errors;assert.deepEqual(errors,[]);console.log('ClaveÚnica Sandbox: callback, identidad de sesión, custodia adulta escritorio/móvil y cierre correctos.');await context.close();
}catch(e){result.error=String(e.message).split('\n')[0];console.log('Verificación CU detenida: '+result.error);process.exitCode=1;}
finally{await fs.writeFile(path.join(output,logoutOnly?'claveunica-logout.json':'claveunica.json'),JSON.stringify(result,null,2));await browser.close();}
