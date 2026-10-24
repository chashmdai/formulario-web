// Solo navega el prototipo de referencia; no modifica sus archivos.
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import fs from 'node:fs/promises';
import path from 'node:path';
const require=createRequire(new URL('../../formulario-publico/front/package.json',import.meta.url));
const {chromium}=require('playwright');
const output=fileURLToPath(new URL('../../.local/evidencias/adultos-navegador',import.meta.url));
await fs.mkdir(output,{recursive:true});
const browser=await chromium.launch({headless:true,channel:process.env.BROWSER_CHANNEL||'msedge'}),evidence=[];
try{
 for(const [size,width,height] of [['escritorio',1440,900],['movil',390,844]]){
  const context=await browser.newContext({viewport:{width,height},deviceScaleFactor:1}),page=await context.newPage();
  await page.goto(process.env.MOCKUP_URL||'http://localhost:8181/',{waitUntil:'domcontentloaded'});await page.locator('#start-adult').click();await page.locator('#use-manual').click();await page.locator('input[name="privacy-decision"][value="yes"]').check();await page.locator('#privacy-continue').click();await page.locator('#adult-run').waitFor();
  await page.locator('#demo-tools > summary').click();await page.locator('#demo-srcei').selectOption('empty');await page.locator('#demo-tools > summary').click();await page.locator('#adult-run').fill('12345678');await page.locator('#adult-run-dv').fill('5');await page.locator('#verify-manual').click();await page.locator('#adult-names').waitFor();await page.locator('#adult-names').fill('NOMBRE');await page.locator('#adult-first').fill('APELLIDO');
  async function capture(state){await page.evaluate(()=>document.fonts.ready);await page.screenshot({path:path.join(output,`mockup-${size}-${state}.png`),fullPage:true});evidence.push({size,state,geometry:await page.evaluate(()=>Object.fromEntries(['.site-header','.hero','.form-toolbar','.page-layout','.form-area','#identificacion','#motivo-seccion','#informacion'].map(s=>{const e=document.querySelector(s),r=e.getBoundingClientRect(),c=getComputedStyle(e);return[s,{x:r.x,y:r.y+scrollY,width:r.width,height:r.height,font:c.fontFamily,fontSize:c.fontSize}];})))});}
  await capture('paso1');await page.locator('#adult-next').click();await page.locator('#motivo').selectOption('consulta');await page.locator('#consulta-confirmada').check();await page.locator('#confirmar-orientacion').click();await page.locator('#tema').selectOption('general');await capture('paso2');await page.locator('#adult-next').click();await page.locator('#consulta-general-detalle').fill('Consulta sintética de cierre '+size);await page.locator('#adult-region').selectOption('13');await capture('paso3');await context.close();
 }
}finally{await fs.writeFile(path.join(output,'mockup.json'),JSON.stringify(evidence,null,2));await browser.close();}
