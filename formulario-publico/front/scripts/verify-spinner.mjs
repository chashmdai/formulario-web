import {chromium} from 'playwright';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdir,writeFile} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
const origin=process.env.RECEIPT_BROWSER_URL||'http://127.0.0.1:5173';
assert(['127.0.0.1','localhost'].includes(new URL(origin).hostname));
const output=process.env.RECEIPT_BROWSER_OUTPUT||path.join(os.tmpdir(),'siger-spinner-visual');
await mkdir(output,{recursive:true});
const headed=process.env.RECEIPT_BROWSER_HEADED==='1';
const browser=await chromium.launch({headless:!headed,...(process.platform==='win32'?{channel:'msedge'}:{})});
const id='11111111-1111-4111-8111-111111111111',journey='22222222-2222-4222-8222-222222222222';
const pending={id,estado:'PENDIENTE_REGISTRO_SIGER',registro:null,custodia:{algoritmo:'Ed25519',keyId:'visual-only',payload:'synthetic',firma:'synthetic'}};
const results=[];
try{
 for(const mode of ['system','no-preference','reduce']){
  const context=await browser.newContext({viewport:{width:1100,height:900},...(mode==='system'?{}:{reducedMotion:mode})});
  const page=await context.newPage();let checks=0,mutations=0;
  try{
   await context.route('**/*',async route=>{
    const req=route.request(),url=new URL(req.url());if(url.origin!==new URL(origin).origin){await route.abort();return;}
    if(!url.pathname.startsWith('/api/')){await route.continue();return;}
    if(req.method()!=='GET'){mutations++;await route.abort();return;}
    let value;
    switch(url.pathname){
     case '/api/session':value={authenticated:false,identity:null,environment:'QA',loginAvailable:false};break;
     case '/api/formulario/recepcion/capacidades':value={adultos:true,nna:true,multimedia:false};break;
     case '/api/formulario/actual':value={id:journey,tipo:'ADULTO_DECLARADO',estado:'RECIBIDO',aceptaCanal:true,avisoVersion:'VISUAL_ONLY',venceEn:'2099-01-01T00:00:00Z'};break;
     case '/api/formulario/recorridos/'+journey+'/comunicacion':value=pending;break;
     case '/api/formulario/comunicaciones/'+id:checks++;value=pending;break;
     default:throw new Error('Unexpected fixture route '+url.pathname);
    }
    await route.fulfill({contentType:'application/json',body:JSON.stringify(value)});
   });
   await page.goto(origin+'/?vista=formulario-adulto',{waitUntil:'networkidle'});
   await page.getByRole('heading',{name:'Recibimos tu comunicación'}).waitFor();
   await page.evaluate(()=>document.fonts.ready);
   const reduced=await page.evaluate(()=>matchMedia('(prefers-reduced-motion: reduce)').matches);
   const spinner=page.locator('.receipt-waiting .ui-spinner');
   await page.locator('.receipt-waiting').scrollIntoViewIfNeeded();
   const styles=await spinner.evaluate(el=>{const s=getComputedStyle(el);return {animationName:s.animationName,animationDuration:s.animationDuration,timingFunction:s.animationTimingFunction,width:s.width,height:s.height,borderWidth:s.borderTopWidth,playState:s.animationPlayState,keyframes:el.getAnimations().flatMap(a=>a.effect.getKeyframes().map(f=>({offset:f.offset,transform:f.transform}))) };});
   assert(parseFloat(styles.width)>=20&&parseFloat(styles.borderWidth)>=3,'Spinner should remain legible over remote desktop');
   if(!reduced){assert(parseFloat(styles.animationDuration)>=1);assert(styles.keyframes.some(frame=>String(frame.transform).includes('360deg')));}
   const samples=[];
   for(let i=0;i<5;i++){
    const transform=await spinner.evaluate(el=>getComputedStyle(el).transform);
    const frame=await page.locator('.receipt-waiting').screenshot({animations:'allow'});
    await writeFile(path.join(output,'spinner-'+mode+'-frame-'+i+'.png'),frame);
    const pixels=createHash('sha256').update(frame).digest('hex');
    samples.push({transform,pixels});
    await page.waitForTimeout(180);
   }
   if(reduced){assert.equal(styles.animationName,'none');assert.equal(new Set(samples.map(s=>s.transform)).size,1);assert.equal(new Set(samples.map(s=>s.pixels)).size,1);}
   else{assert.notEqual(styles.animationName,'none');assert(new Set(samples.map(s=>s.transform)).size>1);assert(new Set(samples.map(s=>s.pixels)).size>1,'The painted spinner must visibly change');}
   await page.waitForTimeout(5000);
   assert(checks>=1);assert.equal(mutations,0);assert.equal(await page.getByRole('heading',{name:'Recibimos tu comunicación'}).count(),1);
   await page.screenshot({path:path.join(output,'spinner-'+mode+'.png')});
   results.push({mode,reduced,styles,samples,status:'PASS',getChecks:checks,mutations,pendingHeldOverSeconds:5});
  }finally{await context.close();}
 }
}finally{await browser.close();}
const report={headed,nativeWindowsEdge:process.platform==='win32',realClock:true,localFixturesOnly:true,results};
await writeFile(path.join(output,'spinner-report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify({headed,report:path.join(output,'spinner-report.json'),results:results.map(r=>({mode:r.mode,reduced:r.reduced,animation:r.styles.animationName,width:r.styles.width,distinctPaintedFrames:new Set(r.samples.map(s=>s.pixels)).size,getChecks:r.getChecks,status:r.status}))}));
