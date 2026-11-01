import {chromium} from 'playwright';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
const origin=process.env.RECEIPT_BROWSER_URL||'http://127.0.0.1:5173';
assert(['127.0.0.1','localhost'].includes(new URL(origin).hostname));
const output=process.env.RECEIPT_BROWSER_OUTPUT||path.join(os.tmpdir(),'siger-review-visual');
await mkdir(output,{recursive:true});
const browser=await chromium.launch({headless:true,...(process.platform==='win32'?{channel:'msedge'}:{})});
const results=[];
try{
 for(const item of [{kind:'adult',width:1440,height:1000,theme:'light'},{kind:'adult',width:390,height:844,theme:'light'},{kind:'nna',width:1440,height:1000,theme:'light'},{kind:'nna',width:390,height:844,theme:'light'},{kind:'adult',width:390,height:844,theme:'dark'},{kind:'nna',width:1280,height:900,theme:'contrast'}]){
  const context=await browser.newContext({viewport:{width:item.width,height:item.height}});
  try{
   const page=await context.newPage();let apiCalls=0;
   await context.route('**/*',async route=>{
    const url=new URL(route.request().url());
    if(url.origin!==new URL(origin).origin||url.pathname.startsWith('/api/')){if(url.pathname.startsWith('/api/'))apiCalls++;await route.abort();return;}
    await route.continue();
   });
   await page.goto(origin+'/scripts/review-fixture.html');
   await page.waitForFunction(()=>typeof window.renderReview==='function');
   await page.evaluate(({kind,theme})=>window.renderReview(kind,theme),item);
   const dialog=page.getByRole('dialog',{name:'Revisión de tu comunicación'});await dialog.waitFor();
   await dialog.evaluate(el=>Promise.all(el.getAnimations().map(animation=>animation.finished.catch(()=>{}))));
   for(const name of ['Identificación','Contacto','Ubicación','Comunicación','Relato','Archivos adjuntos'])assert.equal(await dialog.getByRole('region',{name,exact:true}).count(),1);
   const box=await dialog.boundingBox();assert(box.x>=0&&box.y>=0&&box.x+box.width<=item.width+1&&box.y+box.height<=item.height+1);
   assert(await dialog.locator('.dialog-body').evaluate(el=>el.scrollHeight>el.clientHeight&&el.scrollWidth<=el.clientWidth+1));
   await page.evaluate(()=>document.fonts.ready);
   const geometry=await dialog.evaluate(el=>{
    const h=el.querySelector('h2').getBoundingClientRect(),c=el.querySelector('.dialog-close').getBoundingClientRect(),d=el.getBoundingClientRect();
    return {headingTop:h.top,headingBottom:h.bottom,closeTop:c.top,dialogTop:d.top,scrollTop:el.scrollTop};
   });
   assert(geometry.headingTop>=geometry.dialogTop&&geometry.closeTop>=geometry.dialogTop,JSON.stringify({item,geometry}));
   const prefix=item.kind+'-'+item.width+'-'+item.theme;
   await page.screenshot({path:path.join(output,prefix+'-top.png')});
   await dialog.getByRole('region',{name:'Relato',exact:true}).evaluate(el=>el.scrollIntoView({block:'start'}));
   assert((await dialog.getByRole('region',{name:'Relato',exact:true}).innerText()).includes('Último párrafo'));
   await page.screenshot({path:path.join(output,prefix+'-story.png')});
   await dialog.getByRole('region',{name:'Archivos adjuntos',exact:true}).scrollIntoViewIfNeeded();
   assert(await dialog.getByRole('button',{name:'Enviar comunicación'}).isVisible());
   for(let i=0;i<8;i++){await page.keyboard.press('Tab');assert(await page.evaluate(()=>!!document.activeElement.closest('dialog')));}
   assert.equal(apiCalls,0);
   results.push({...item,status:'PASS',apiCalls,screenshots:2});
  }finally{await context.close();}
 }
}finally{await browser.close();}
await writeFile(path.join(output,'review-report.json'),JSON.stringify({localFixturesOnly:true,results},null,2));console.log(JSON.stringify({output,results}));
