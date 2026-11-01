import {chromium} from 'playwright';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';

const origin=process.env.RECEIPT_BROWSER_URL||'http://127.0.0.1:5173';
assert(['127.0.0.1','localhost'].includes(new URL(origin).hostname),'Use only a local static preview');
const output=process.env.RECEIPT_BROWSER_OUTPUT||path.join(os.tmpdir(),'siger-receipt-visual');
await mkdir(output,{recursive:true});
const id='11111111-1111-4111-8111-111111111111',journey='22222222-2222-4222-8222-222222222222';
const proof={algoritmo:'Ed25519',keyId:'visual-synthetic',payload:'not-a-real-receipt',firma:'not-a-real-signature'};
const pending={id,estado:'PENDIENTE_REGISTRO_SIGER',custodia:proof,registro:null};
const confirmed={...pending,estado:'REGISTRADA_SIGER',registro:{registro:{folio:'COM-000123'},comprobante:proof}};
const browser=await chromium.launch({headless:true,...(process.platform==='win32'?{channel:'msedge'}:{})});
const results=[];
try{
 for(const variant of [
  {name:'desktop-light',width:1440,height:1000,theme:'light'},
  {name:'mobile-light',width:390,height:844,theme:'light'},
  {name:'mobile-dark-reduced',width:390,height:844,theme:'dark',reduced:true},
  {name:'desktop-contrast',width:1280,height:900,theme:'contrast'},
 ]){
  const context=await browser.newContext({viewport:{width:variant.width,height:variant.height},reducedMotion:variant.reduced?'reduce':'no-preference'});
  try{
   const page=await context.newPage();let checks=0,mutations=0;
   await page.clock.install();
   await page.addInitScript(theme=>localStorage.setItem('siger:theme',theme),variant.theme);
   await context.route('**/*',async route=>{
    const request=route.request(),url=new URL(request.url());
    if(url.origin!==new URL(origin).origin){await route.abort();return;}
    if(!url.pathname.startsWith('/api/')){await route.continue();return;}
    if(request.method()!=='GET'){mutations++;await route.abort();return;}
    let data,status=200;
    switch(url.pathname){
     case '/api/session':data={authenticated:false,identity:null,environment:'QA',loginAvailable:false};break;
     case '/api/formulario/recepcion/capacidades':data={adultos:true,nna:true,multimedia:false};break;
     case '/api/formulario/actual':data={id:journey,tipo:'ADULTO_DECLARADO',avisoVersion:'VISUAL_ONLY',aceptaCanal:true,estado:'RECIBIDO',venceEn:'2099-01-01T00:00:00Z'};break;
     case '/api/formulario/recorridos/'+journey+'/comunicacion':data=pending;break;
     case '/api/formulario/comunicaciones/'+id:checks++;data=checks===1?{}:confirmed;status=checks===1?503:200;break;
     default:throw new Error('Unexpected local mock route: '+url.pathname);
    }
    await route.fulfill({status,contentType:'application/json',body:JSON.stringify(data)});
   });
   await page.goto(origin+'/?vista=formulario-adulto',{waitUntil:'networkidle'});
   const receipt=page.locator('#communication-receipt');
   await page.getByRole('heading',{name:'Recibimos tu comunicación'}).waitFor();
   assert.equal(await page.getByRole('button',{name:'Descargar comprobante institucional'}).count(),0);
   assert.equal(await page.locator('.receipt-waiting .ui-spinner').count(),1);
   if(variant.reduced)assert.equal(await page.locator('.receipt-waiting .ui-spinner').evaluate(el=>getComputedStyle(el).animationName),'none');
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'No horizontal overflow');
   await receipt.screenshot({path:path.join(output,variant.name+'-pending.png')});
   await page.clock.runFor(4000);
   await page.getByRole('alert').filter({hasText:'No pudimos actualizar el estado'}).waitFor();
   assert.equal(await page.getByRole('button',{name:'Descargar comprobante de recepción'}).isEnabled(),true);
   await receipt.screenshot({path:path.join(output,variant.name+'-retry.png')});
   await page.clock.runFor(4000);
   await page.getByRole('heading',{name:'Comunicación registrada'}).waitFor();
   assert.equal(await page.getByText('COM-000123',{exact:true}).count(),1);
   assert.equal(await page.getByRole('button',{name:'Descargar comprobante institucional'}).isEnabled(),true);
   assert.equal(await page.locator('.receipt-waiting').count(),0);
   assert.equal(await page.getByRole('alert').count(),0);
   await receipt.screenshot({path:path.join(output,variant.name+'-confirmed.png')});
   await page.clock.runFor(20000);assert.equal(checks,2);assert.equal(mutations,0);
   results.push({variant:variant.name,status:'PASS',stateGets:checks,mutations,screenshots:3});
  }finally{await context.close();}
 }
}finally{await browser.close();}
await writeFile(path.join(output,'report.json'),JSON.stringify({origin,controlledMocksOnly:true,noCitizenDataCreated:true,results},null,2));
console.log(JSON.stringify({output,results}));
