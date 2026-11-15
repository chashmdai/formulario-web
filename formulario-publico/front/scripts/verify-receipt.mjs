import {chromium} from 'playwright';
import assert from 'node:assert/strict';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';

const origin=process.env.RECEIPT_BROWSER_URL||'http://127.0.0.1:5173';
assert(['127.0.0.1','localhost'].includes(new URL(origin).hostname),'Use only a local static preview');
const output=process.env.RECEIPT_BROWSER_OUTPUT||path.join(os.tmpdir(),'siger-receipt-visual');
await mkdir(output,{recursive:true});
const id='11111111-1111-4111-8111-111111111111',journey='22222222-2222-4222-8222-222222222222';
// Evidencias sintéticas decodificables; las firmas no son reales y el navegador no las verifica.
const b64url=value=>Buffer.from(JSON.stringify(value)).toString('base64url');
const proof={algoritmo:'Ed25519',keyId:'visual-synthetic',payload:b64url({version:'1.0',id,recibidaEn:'2026-11-14T15:30:00Z',tipoRecepcion:'CUSTODIA_PUBLICA',huellaContenido:'a'.repeat(64),huellaProcedencia:'b'.repeat(64),emisor:'FORMULARIO_PUBLICO_DDN',avisoVersion:'VISUAL_ONLY'}),firma:'not-a-real-signature'};
const registry={version:'1.0',emisor:'SIGER_DDN',hecho:'COMUNICACION_REGISTRADA',operacionPublicaId:id,comunicacionId:'33333333-3333-4333-8333-333333333333',numeroFolio:123,folio:'COM-000123',registradaEn:'2026-11-15T12:00:00Z',canalOrigen:'FORMULARIO_WEB_PUBLICO',sha256:'a'.repeat(64),huellaProcedencia:'b'.repeat(64)};
const pending={id,estado:'PENDIENTE_REGISTRO_SIGER',custodia:proof,registro:null};
const confirmed={...pending,estado:'REGISTRADA_SIGER',registro:{registro:registry,comprobante:{algoritmo:'Ed25519',keyId:'visual-institutional',payload:b64url(registry),firma:'not-a-real-signature'}}};
const story='Relato sintético para el comprobante visual.\n\n'+'Contexto de prueba extenso. '.repeat(260);
const summary=state=>({...state,archivosRetirados:[],contenido:{version:'1.0',tipo:'ADULTO_DECLARADO',recibidaEn:'2026-11-14T15:30:00Z',
 campos:{motivo:'consulta',tema:'general','consulta-confirmada':'true','consulta-general-detalle':story,'adult-region':'13',email:'sintetico@example.test'},relaciones:[],instituciones:[],
 personas:[{personaId:'presentador-adulto',estado:'REVISADO',autenticada:false,runInformado:false,camposFaltantes:['primerNombre','primerApellido'],camposObligatorios:['primerNombre','primerApellido'],valoresDeclarados:{primerNombre:'Persona',primerApellido:'Sintética'},mensaje:'Sintético'}],
 identidadAutenticada:null,adjuntos:[]}});
// Misma política que Spring aplica a la aplicación servida desde el JAR.
const CSP="default-src 'self'; script-src 'self'; style-src 'self'; font-src 'self'; img-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self' https://accounts.claveunica.gob.cl";
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
   const page=await context.newPage();let checks=0,mutations=0,summaries=0;const violations=[];
   page.on('console',message=>{if(/Content Security Policy|Refused to/i.test(message.text()))violations.push(message.text());});
   await page.addInitScript(theme=>localStorage.setItem('siger:theme',theme),variant.theme);
   await context.route('**/*',async route=>{
    const request=route.request(),url=new URL(request.url());
    if(url.origin!==new URL(origin).origin){await route.abort();return;}
    if(request.resourceType()==='document'){const original=await route.fetch();await route.fulfill({response:original,headers:{...original.headers(),'content-security-policy':CSP}});return;}
    if(!url.pathname.startsWith('/api/')){await route.continue();return;}
    if(request.method()!=='GET'){mutations++;await route.abort();return;}
    let data,status=200;
    switch(url.pathname){
     case '/api/session':data={authenticated:false,identity:null,environment:'QA',loginAvailable:false};break;
     case '/api/formulario/recepcion/capacidades':data={adultos:true,nna:true,multimedia:false};break;
     case '/api/formulario/actual':data={id:journey,tipo:'ADULTO_DECLARADO',avisoVersion:'VISUAL_ONLY',aceptaCanal:true,estado:'RECIBIDO',venceEn:'2099-01-01T00:00:00Z'};break;
     case '/api/formulario/recorridos/'+journey+'/comunicacion':data=pending;break;
     case '/api/formulario/comunicaciones/'+id+'/resumen':summaries++;data=summary(checks>=2?confirmed:pending);break;
     case '/api/formulario/comunicaciones/'+id:checks++;data=checks===1?{}:confirmed;status=checks===1?503:200;break;
     default:throw new Error('Unexpected local mock route: '+url.pathname);
    }
    await route.fulfill({status,contentType:'application/json',body:JSON.stringify(data)});
   });
   await page.goto(origin+'/?vista=formulario-adulto',{waitUntil:'networkidle'});
   const receipt=page.locator('#communication-receipt');
   await page.getByRole('heading',{name:'Recibimos tu comunicación'}).waitFor();
   assert.equal(await page.evaluate(()=>fetch('data:text/plain,csp').then(()=>'permitido',()=>'bloqueado')),'bloqueado','La CSP de producción debe estar activa');violations.length=0;
   assert.equal(await page.getByRole('button',{name:'Descargar comprobante PDF'}).isEnabled(),true);
   await page.locator('.receipt-evidence summary').click();assert.equal(await page.locator('.receipt-evidence').getAttribute('open'),'');
   assert.equal(await page.getByRole('button',{name:'Descargar registro institucional (JSON)'}).count(),0);
   assert.equal(await page.locator('.receipt-waiting .ui-spinner').count(),1);
   const rotation=await page.locator('.receipt-waiting .ui-spinner').evaluate(async el=>{
    const first=getComputedStyle(el).transform;
    await new Promise(resolve=>setTimeout(resolve,240));
    return {first,second:getComputedStyle(el).transform,animation:getComputedStyle(el).animationName};
   });
   if(variant.reduced){assert.equal(rotation.animation,'none');assert.equal(rotation.first,rotation.second);}
   else{assert.notEqual(rotation.first,rotation.second,'Spinner must actually rotate in a real browser');}
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'No horizontal overflow');
   await receipt.screenshot({path:path.join(output,variant.name+'-pending.png')});
   await page.getByRole('alert').filter({hasText:'No pudimos actualizar el estado'}).waitFor();
   assert.equal(await page.getByRole('button',{name:'Descargar evidencia de custodia (JSON)'}).isEnabled(),true);
   await receipt.screenshot({path:path.join(output,variant.name+'-retry.png')});
   await page.getByRole('heading',{name:'Comunicación registrada'}).waitFor();
   assert.equal(await page.getByText('COM-000123',{exact:true}).count(),1);
   assert.equal(await page.getByRole('button',{name:'Descargar registro institucional (JSON)'}).isEnabled(),true);
   assert.equal(await page.locator('.receipt-waiting').count(),0);
   assert.equal(await page.getByRole('alert').count(),0);
   await receipt.screenshot({path:path.join(output,variant.name+'-confirmed.png')});
   assert.equal(await page.getByRole('button',{name:'Actualizar estado',exact:true}).count(),0);
   assert.equal(await page.getByRole('heading',{name:'Guarda tu comprobante'}).count(),1);
   const [download]=await Promise.all([page.waitForEvent('download'),page.getByRole('button',{name:'Descargar comprobante PDF'}).click()]);
   assert.equal(download.suggestedFilename(),'comprobante-comunicacion-'+id+'.pdf');
   const pdfPath=path.join(output,variant.name+'-comprobante.pdf');await download.saveAs(pdfPath);const bytes=await readFile(pdfPath);
   assert.equal(bytes.subarray(0,5).toString('latin1'),'%PDF-');assert(bytes.length>20000,'PDF con fuentes incrustadas');assert.equal(summaries,1);
   assert.deepEqual(violations,[],'Sin violaciones de CSP al generar el PDF');assert.equal(await page.getByRole('alert').count(),0);
   await page.waitForTimeout(4500);assert.equal(checks,2);assert.equal(mutations,0);
   results.push({variant:variant.name,status:'PASS',stateGets:checks,mutations,summaries,pdfBytes:(await readFile(path.join(output,variant.name+'-comprobante.pdf'))).length,cspViolations:violations.length,screenshots:3,realSpinnerRotation:!variant.reduced,reducedMotion:!!variant.reduced});
  }finally{await context.close();}
 }
}finally{await browser.close();}
await writeFile(path.join(output,'report.json'),JSON.stringify({origin,controlledMocksOnly:true,noCitizenDataCreated:true,results},null,2));
console.log(JSON.stringify({output,results}));
