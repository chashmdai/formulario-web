// Comprobación visual final con API controlada: no crea recorridos ni consulta proveedores.
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../formulario-publico/front/package.json',import.meta.url)),{chromium}=require('playwright');
const output=fileURLToPath(new URL('../../.local/evidencias/adultos-navegador',import.meta.url));
const browser=await chromium.launch({headless:true,channel:'msedge'}),evidence=[];
try{
 for(const [size,width,height] of [['escritorio',1440,900],['movil',390,844]]){
  const context=await browser.newContext({viewport:{width,height},ignoreHTTPSErrors:false}),page=await context.newPage();let view=null;
  await page.route('**/api/**',async route=>{
   const r=route.request(),url=new URL(r.url());let data,status=200;
   if(url.pathname==='/api/session')data={authenticated:false,identity:null,environment:'SANDBOX',loginAvailable:true};
   else if(url.pathname==='/api/csrf')data={token:'CONTROL_VISUAL',headerName:'X-CSRF-TOKEN',parameterName:'_csrf'};
   else if(url.pathname.endsWith('/recepcion/capacidades'))data={adultos:true,nna:true};
   else if(url.pathname.endsWith('/actual'))data={id:'11111111-1111-4111-8111-111111111111',tipo:'ADULTO_DECLARADO',avisoVersion:'CONTROL_VISUAL',aceptaCanal:true,estado:'BORRADOR',venceEn:'2026-10-25T00:00:00Z'};
   else if(url.pathname.endsWith('/adjuntos'))data=[];
   else if(url.pathname.endsWith('/identificacion'))data=view={personaId:'presentador-adulto',estado:'REVISADO',autenticada:false,runInformado:true,camposFaltantes:['primerNombre','segundoNombre','primerApellido','segundoApellido','fechaNacimiento','sexo'],camposObligatorios:['primerNombre','primerApellido'],valoresDeclarados:{},mensaje:'Completa los datos solicitados. La revisión del RUN no acredita tu identidad.'};
   else if(url.pathname.endsWith('/declaracion'))data=view={...view,valoresDeclarados:r.postDataJSON().atributos};
   else if(url.pathname.endsWith('/personas/presentador-adulto')){data=view||{};status=view?200:404;}
   else throw new Error('Ruta ajena al control visual');
   await route.fulfill({status,contentType:'application/json',body:JSON.stringify(data)});
  });
  await page.goto('https://claveunica.formularioadulto.defensorianinez.gob.cl/?vista=formulario-adulto&modo=manual',{waitUntil:'domcontentloaded'});await page.locator('#adult-run').waitFor();await page.locator('#adult-run').fill('12345678');await page.locator('#adult-run-dv').fill('5');await page.locator('#verify-manual').click();await page.locator('#adult-names').fill('NOMBRE');await page.locator('#adult-first').fill('APELLIDO');
  for(const theme of ['light','dark','contrast']){
   await page.locator('#theme-choice').selectOption(theme);await page.evaluate(()=>document.fonts.ready);
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
   await page.screenshot({path:path.join(output,`presentacion-controlada-${size}-${theme}.png`),fullPage:true});
   evidence.push({size,theme,apiControlada:true,overflow:false,geometry:await page.locator('#identificacion').boundingBox()});
  }
  await page.locator('#theme-choice').selectOption('light');await page.getByRole('button',{name:/Aumentar/}).click();await page.getByRole('button',{name:/Aumentar/}).click();await page.getByRole('button',{name:/Aumentar/}).click();assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await page.screenshot({path:path.join(output,`presentacion-controlada-${size}-130.png`),fullPage:true});await context.close();
 }
}finally{await fs.writeFile(path.join(output,'presentacion-controlada.json'),JSON.stringify(evidence,null,2));await browser.close();}
