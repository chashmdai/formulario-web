import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../formulario-publico/front/package.json',import.meta.url));
const {chromium}=require('playwright');
const root=fileURLToPath(new URL('../../',import.meta.url));
const output=process.env.PARITY_OUTPUT||path.join(root,'.local/evidencias/paridad-nna');
await fs.mkdir(output,{recursive:true});
const browser=await chromium.launch({headless:true,...(process.env.BROWSER_CHANNEL?{channel:process.env.BROWSER_CHANNEL}:{})});
const evidence=[];
try {
  for(const [size,width,height] of [['escritorio',1440,900],['movil',390,844]]) {
    for(const [kind,url] of [['mockup',process.env.MOCKUP_URL||'http://localhost:8181/'],['react',process.env.REACT_URL||'https://claveunica.formularioadulto.defensorianinez.gob.cl:18443/?vista=formulario-nna']]) {
      const context=await browser.newContext({viewport:{width,height},deviceScaleFactor:1,ignoreHTTPSErrors:false});
      const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
      // El software institucional del navegador mantiene peticiones abiertas; esperar UI, no inactividad de red.
      await page.goto(url,{waitUntil:'domcontentloaded'});
      if(kind==='mockup')await page.locator('#start-nna').click();
      await page.locator('#nna-names').waitFor({state:'visible'});await page.evaluate(()=>document.fonts.ready);
      async function capture(state) {
        await page.screenshot({path:path.join(output,`${kind}-${size}-${state}.png`),fullPage:true});
        const geometry=await page.evaluate(()=>Object.fromEntries(['.site-header','.hero','.form-toolbar','.page-layout','.side-column','.form-area','#nna-step-one','#nna-step-two','.nna-basics','#nna-story','.page-footer'].map(s=>{
          const e=document.querySelector(s);if(!e)return[s,null];const r=e.getBoundingClientRect(),c=getComputedStyle(e);return[s,{x:r.x,y:r.height?r.y+window.scrollY:r.y,width:r.width,height:r.height,font:c.fontFamily,fontSize:c.fontSize,display:c.display}];
        })));
        const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth);
        evidence.push({kind,size,state,geometry,overflow,errors:[...errors]});assert.equal(overflow,false,`${kind}/${size}/${state}: desborde horizontal`);
      }
      await capture('paso1');
      await page.locator('#nna-names').fill('NOMBRE');await page.locator('#nna-first').fill('APELLIDO');await page.locator('#nna-region').selectOption('13');await page.locator('#nna-primary-method').selectOption('unsafe');
      await page.locator('#nna-next').click();await page.locator('#nna-story').waitFor({state:'visible'});await page.locator('#nna-story').fill('Relato sintético de paridad.');await capture('paso2');
      await page.getByRole('button',{name:'Completo',exact:true}).click();await capture('completo');
      await page.locator('#theme-choice').selectOption('contrast');await capture('contraste');await page.locator('#theme-choice').selectOption('light');
      if(kind==='react') {
        await page.locator('#nna-files').setInputFiles({name:'respaldo.txt',mimeType:'text/plain',buffer:Buffer.from('Documento sintetico de prueba')});
        await page.locator('#nna-files-feedback').filter({hasText:'respaldo.txt'}).waitFor();
        await page.locator('#nna-review').click();await page.getByRole('dialog').waitFor({state:'visible'});await capture('revision');
        await page.getByRole('button',{name:'Enviar comunicación',exact:true}).click();await page.locator('#communication-receipt').waitFor();
        assert.match(await page.locator('#communication-receipt').innerText(),/registro institucional está pendiente/);await capture('custodia');
        await page.reload({waitUntil:'domcontentloaded'});await page.locator('#communication-receipt').waitFor();await capture('recarga');
      }
      assert.deepEqual(errors,[],`${kind}/${size}: error JavaScript`);await context.close();console.log(`${kind}/${size}: recorrido capturado y sin desborde`);
    }
  }
} finally {await fs.writeFile(path.join(output,'geometria.json'),JSON.stringify(evidence,null,2));await browser.close();}
