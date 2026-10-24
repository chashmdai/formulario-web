import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const require=createRequire(new URL('../../formulario-publico/front/package.json',import.meta.url));
const {chromium}=require('playwright');
const root=fileURLToPath(new URL('../../',import.meta.url));
const output=path.join(root,'.local/evidencias/adultos-navegador');
const phase=process.env.ADULT_PHASE||'preflight';
const origin=process.env.REACT_URL||'https://claveunica.formularioadulto.defensorianinez.gob.cl';
await fs.mkdir(output,{recursive:true});
const browser=await chromium.launch({headless:true,channel:process.env.BROWSER_CHANNEL||'msedge'});
const evidence=[];
try{
 for(const [size,width,height] of [['escritorio',1440,900],['movil',390,844]]){
  const reasons=phase==='recepcion'?['consulta','promocion','vulneracion']:['consulta'];
  for(const reason of reasons){
   const context=await browser.newContext({viewport:{width,height},deviceScaleFactor:1,ignoreHTTPSErrors:false});
   const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
   const item={phase,size,reason,states:[],errors,checks:[]};evidence.push(item);
   async function capture(state){
    await page.evaluate(()=>document.fonts.ready);
    const geometry=await page.evaluate(()=>Object.fromEntries(['.site-header','.hero','.form-toolbar','.page-layout','.side-column','.form-area','#identificacion','#motivo-seccion','#informacion','.page-footer'].map(s=>{const e=document.querySelector(s);if(!e)return[s,null];const r=e.getBoundingClientRect(),c=getComputedStyle(e);return[s,{x:r.x,y:r.y+scrollY,width:r.width,height:r.height,font:c.fontFamily,fontSize:c.fontSize}];})));
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,`${size}/${state}: desborde`);
    await page.screenshot({path:path.join(output,`${phase}-${size}-${reason}-${state}.png`),fullPage:true});item.states.push({state,geometry});
   }
   async function next(){await page.locator('#adult-next').click();}
   async function choose(value){await page.locator('#motivo').selectOption(value);if(value==='consulta'&&await page.getByRole('dialog').isVisible()){await page.getByRole('checkbox',{name:/Leí esta orientación/}).check();await page.getByRole('button',{name:'Entendido, continuar'}).click();}}
   async function review(){await page.locator('#revisar').click();await page.getByRole('dialog').waitFor({state:'visible'});}
   await page.goto(origin+'/?vista=formulario-adulto&modo=manual',{waitUntil:'domcontentloaded'});
   const privacy=page.getByRole('dialog');await privacy.waitFor();await privacy.getByRole('radio',{name:/^Acepto/}).check();await privacy.getByRole('button',{name:'Continuar',exact:true}).click();
   await page.locator('#adult-run').waitFor();
   const j=await (await context.request.get(origin+'/api/formulario/actual')).json();item.journey=j.id;
   await page.locator('#adult-run').fill('12345678');await page.locator('#adult-run-dv').fill('0');await page.locator('#verify-manual').click();await page.getByText('El número y el dígito verificador no coinciden.',{exact:true}).waitFor();item.checks.push('modulo11');
   await page.locator('#adult-run-dv').fill('5');await page.locator('#verify-manual').click();await page.locator('#adult-names').waitFor();await page.locator('#adult-names').fill('NOMBRE');await page.locator('#adult-first').fill('APELLIDO');
   assert.equal(await page.locator('#adult-names').getAttribute('readonly'),null);await capture('paso1');await next();await page.locator('#motivo').waitFor();
   if(phase==='preflight'){
    await choose('consulta');await page.locator('#tema').selectOption('empleo');await next();assert.equal(await page.locator('#informacion').isVisible(),false);item.checks.push('empleo-canal-oficial');
    await page.locator('#tema').selectOption('estadisticas');await next();assert.equal(await page.locator('#informacion').isVisible(),false);await page.locator('#stats-confirm').check();await next();await page.locator('#stats-descripcion').fill('Datos sintéticos no publicados');await page.locator('#adult-region').selectOption('13');await review();await page.getByRole('button',{name:'Enviar comunicación',exact:true}).click();await page.getByRole('alert').filter({hasText:'La recepción está temporalmente cerrada'}).waitFor();await page.getByRole('button',{name:'Volver al formulario'}).click();item.checks.push('estadisticas-confirmacion-recepcion-cerrada');
    await page.locator('#adult-prev').click();await page.locator('#tema').selectOption('entrevista');await next();await page.locator('#entrevista-rol').selectOption('otro');await page.locator('#entrevista-motivo').fill('Entrevista sintética');await page.locator('#revisar').click();await page.locator('#adult-errors').waitFor();await page.locator('#entrevista-rol-otro').fill('Perfil sintético');await review();await page.getByRole('button',{name:'Volver al formulario'}).click();item.checks.push('entrevista-otro-obligatorio');
    await page.locator('#adult-prev').click();
   }
   await choose(reason);if(reason==='consulta')await page.locator('#tema').selectOption('general');await capture('paso2');await next();await page.locator('#informacion').waitFor();
   await page.locator('#adult-region').selectOption('13');await page.locator('#adult-commune').selectOption('Santiago');
   await page.locator('#email').fill('laboratorio@example.invalid');await page.locator('#telefono').fill('+56 9 1234 5678');
   if(reason==='consulta')await page.locator('#consulta-general-detalle').fill('Consulta sintética de cierre '+size);
   if(reason==='promocion'){
    await page.locator('input[name="organizacion"][value="si"]').check();await page.locator('#tipo-organizacion').selectOption('ONG o fundación');await page.locator('#nombre-organizacion').fill('Entidad sintética');await page.locator('#actividad').selectOption('Taller');await page.locator('#publico').fill('Adolescentes');await page.locator('#fecha-actividad').fill('2026-11-02');await page.locator('#promocion-region').selectOption('13');await page.locator('#promocion-commune').selectOption('Santiago');await page.locator('#relato').fill('Actividad sintética de cierre '+size);
   }
   if(reason==='vulneracion'){
    await page.locator('#riesgo-si').check();await page.locator('#urgencia').waitFor();await page.locator('#riesgo-no').check();await page.locator('#relacion').selectOption('otro-familiar');await page.locator('#relacion-familiar').selectOption('Tía o tío');await page.locator('#hechos-region').selectOption('13');await page.locator('#hechos-commune').selectOption('Santiago');await page.locator('#lugar').fill('Lugar sintético');await page.locator('#momento').selectOption('Ocurrió recientemente');await page.locator('#relato').fill('Relato sintético de cierre '+size);
    await page.locator('#af-unknown-1').check();await page.locator('#agregar-afectado').click();await page.locator('#afectado-2').getByRole('textbox',{name:/Primer nombre/}).fill('NOMBRE');await page.locator('#afectado-2').getByRole('button',{name:'Quitar',exact:true}).click();await page.locator('#afectado-2').waitFor({state:'detached'});
    await page.locator('#add-related-adult').click();await page.locator('#ra-relationship-1').selectOption('Testigo');await page.locator('#identifica-responsables').check();await page.locator('#sb-kind-1').selectOption('institucion');await page.locator('#sb-entity-1').fill('Entidad sintética');await page.locator('#sb-etype-1').selectOption('Organismo público');await page.locator('#agregar-responsable').click();await page.locator('#sb-alias-2').fill('Alias sintético');await page.locator('#sb-rol-2').fill('Vínculo sintético');
   }
   if(phase==='preflight'){
    await page.locator('#adjuntos').setInputFiles({name:'incorrecto.pdf',mimeType:'application/pdf',buffer:Buffer.from('No es PDF')});await page.locator('#archivos-elegidos').filter({hasText:'No pudimos recibir estos archivos'}).waitFor();await page.locator('#revisar').click();await page.locator('#adult-errors').waitFor();await page.getByRole('button',{name:'Quitar archivos seleccionados'}).click();item.checks.push('archivo-invalido-bloquea-revision');
   }
   await page.locator('#adjuntos').setInputFiles({name:'respaldo.txt',mimeType:'text/plain',buffer:Buffer.from('Respaldo sintetico de adultos')});await page.locator('#archivos-elegidos').filter({hasText:'respaldo.txt'}).waitFor();
   await capture('paso3');await page.getByRole('button',{name:'Completo',exact:true}).click();await capture('completo');await page.locator('#theme-choice').selectOption('contrast');await capture('contraste');await page.locator('#theme-choice').selectOption('light');
   await review();await capture('revision');
   if(phase==='recepcion'){
    const request=page.waitForRequest(r=>r.url().endsWith('/comunicaciones')&&r.method()==='POST');await page.getByRole('button',{name:'Enviar comunicación',exact:true}).click();const sent=await request;await page.locator('#communication-receipt').waitFor();assert.match(await page.locator('#communication-receipt').innerText(),/registro institucional está pendiente/);await capture('custodia');
    item.key=sent.headers()['idempotency-key'];item.body=sent.postDataJSON();const csrf=await (await context.request.get(origin+'/api/csrf')).json();
    const headers={[csrf.headerName]:csrf.token,'Idempotency-Key':item.key};const repeat=await context.request.post(sent.url(),{headers,data:item.body});assert.equal(repeat.status(),200);assert.equal((await repeat.json()).id,item.key);
    const conflict=await context.request.post(sent.url(),{headers,data:{...item.body,campos:{...item.body.campos,'adult-address':'cambio'}}});assert.equal(conflict.status(),409);item.checks.push('idempotencia-identica-y-conflicto');
    const files=await (await context.request.get(origin+`/api/formulario/recorridos/${j.id}/adjuntos`)).json();assert.equal(files.length,1);item.attachment=files[0].id;
    const alien=await browser.newContext({ignoreHTTPSErrors:false});const denied=await alien.request.get(origin+`/api/formulario/recorridos/${j.id}/adjuntos/${files[0].id}`);assert.equal(denied.status(),404);await alien.close();item.checks.push('adjunto-privado');
    await page.reload({waitUntil:'domcontentloaded'});await page.locator('#communication-receipt').waitFor();await capture('recarga');
   }
   assert.deepEqual(errors,[]);console.log(`${phase}/${size}/${reason}: correcto`);await context.close();
  }
 }
}catch(e){console.log('Verificación detenida: '+String(e.message).split('\n')[0]);process.exitCode=1;}
finally{await fs.writeFile(path.join(output,`${phase}.json`),JSON.stringify(evidence,null,2));await browser.close();}
