// Verificación visual aislada: todas las API se interceptan; nunca usar contra datos ciudadanos.
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {fixture} from './ui-fixtures.mjs';
import {inspectAccessibility} from './ui-accesibilidad.mjs';
const require=createRequire(new URL('../../formulario-publico/front/package.json',import.meta.url)),{chromium}=require('playwright');
const root=fileURLToPath(new URL('../../',import.meta.url)),origin=process.env.UI_ORIGIN||'http://127.0.0.1:15173';
assert(/^http:\/\/(127\.0\.0\.1|localhost):[0-9]+$/.test(origin),'Solo preview loopback aislado');
const out=path.join(root,'.local/evidencias/validacion-institucional-20261031');await fs.mkdir(out,{recursive:true});
const browser=await chromium.launch({headless:true,channel:'msedge'}),evidence=[];
async function shot(page,name){
 await page.evaluate(async()=>{await document.fonts.ready;await Promise.all(document.getAnimations().filter(a=>a.effect?.getComputedTiming().iterations!==Infinity).map(a=>a.finished.catch(()=>{})));});
 const accessibility=await inspectAccessibility(page);assert.equal(accessibility.documentOverflow,false,name+': desborde horizontal');
 assert.deepEqual(accessibility.contrast.failures,[],name+': contraste');assert(accessibility.dialogFooterVisible,name+': acciones fuera de pantalla');
 const layout=await page.evaluate(()=>({width:innerWidth,height:innerHeight,scale:document.getElementById('font-status')?.textContent,theme:document.documentElement.dataset.theme,missingLabels:[...document.querySelectorAll('input:not([type=hidden]),select,textarea')].filter(e=>e.getClientRects().length&&!e.labels?.length&&!e.getAttribute('aria-label')&&!e.getAttribute('aria-labelledby')).map(e=>e.id)}));
 assert.deepEqual(layout.missingLabels,[],name+': etiquetas');await page.screenshot({path:path.join(out,name+'.png'),fullPage:true});await page.screenshot({path:path.join(out,name+'-pantalla.png'),fullPage:false});evidence.push({name,...layout,accessibility});
}
async function reason(page,why,topic,full){
 if(!full)await page.locator('#adult-next').click();await page.locator('#motivo').selectOption(why);
 if(why==='consulta'){await page.locator('#consulta-confirmada').check();await page.locator('#confirmar-orientacion').click();await page.locator('#tema').selectOption(topic);if(topic==='estadisticas')await page.locator('#stats-confirm').check();}
 if(!full&&topic!=='empleo')await page.locator('#adult-next').click();
 if(topic!=='empleo')await page.locator('#informacion').waitFor();
}
async function identify(page,foreign,cu){
 if(cu){await page.locator('#adult-run:not(:disabled)').waitFor();assert(await page.locator('#adult-run').getAttribute('readonly')!==null);return;}
 if(foreign){
  await page.getByRole('radio',{name:/extranjera sin RUN/}).check();await page.locator('#adult-nationality').selectOption('Perú');await page.locator('#adult-document-kind').selectOption('PASAPORTE');await page.locator('#adult-document-number').fill('AB/123-xyz.45');
 }else{await page.locator('#adult-run').fill('12345678');await page.locator('#adult-run-dv').fill('5');}
 await page.locator('#adult-names').fill('DECLARADO');await page.locator('#adult-first').fill('APELLIDO');
}
try{
 for(const [size,width,height] of [['escritorio',1440,1000],['movil',390,844]]){
  for(const theme of ['light','dark','contrast']){
   for(const scenario of ['grupo-extranjero','estadisticas-cu','promocion','empleo','nna']){
    const full=theme!=='light',nna=scenario==='nna',cu=scenario==='estadisticas-cu',foreign=scenario==='grupo-extranjero';
    const context=await browser.newContext({viewport:{width,height},locale:'es-CL',reducedMotion:theme==='contrast'?'reduce':'no-preference'});
    const mock=await fixture(context,nna?'NNA':cu?'ADULTO_CU':'ADULTO_DECLARADO');
    await context.route('**/*',route=>new URL(route.request().url()).origin===origin?route.fallback():route.abort());
    const page=await context.newPage(),errors=[];page.on('pageerror',()=>errors.push('JavaScript'));
    await page.goto(origin+(nna?'/?vista=formulario-nna':'/?vista=formulario-adulto&modo='+(cu?'cu':'manual')),{waitUntil:'domcontentloaded'});
    await page.locator('#theme-choice').selectOption(theme);if(full)await page.getByRole('button',{name:'Completo',exact:true}).click();
    if(theme==='contrast')for(let n=0;n<3;n++)await page.locator('#font-increase').click();
    if(nna){
     await page.locator('#nna-names').fill('NOMBRE');await page.locator('#nna-first').fill('APELLIDO');await page.locator('#nna-region').selectOption('12');await page.locator('#nna-primary-method').selectOption('email');
     await page.locator('#nna-primary-email').fill('incompleto@');await page.locator('#nna-primary-email').blur();assert(await page.getByRole('alert').filter({hasText:'formato del correo'}).count()>0);
     await page.locator('#nna-primary-email').fill('x@x.x');await page.locator('#nna-primary-email').blur();if(!full)await page.locator('#nna-next').click();
     await page.locator('#nna-story').fill('Relato sintético. No se envía al backend real.');await shot(page,[scenario,size,theme,'formulario'].join('-'));
     await page.locator('#nna-review').click();await page.getByRole('dialog').waitFor();await shot(page,[scenario,size,theme,'revision'].join('-'));
    }else{
     await identify(page,foreign,cu);
     if(foreign){await page.locator('#adult-preferred').fill('PREFERIDO');await page.locator('#adult-treatment').selectOption('elle');}
     if(scenario==='empleo'){
      await reason(page,'consulta','empleo',full);assert.equal(await page.locator('#resource-empleo a').getAttribute('href'),'https://www.empleospublicos.cl/');
      assert(await page.locator('#revisar').isHidden());assert(await page.locator('#adult-next').isDisabled());
      await shot(page,[scenario,size,theme,'derivacion'].join('-'));
     }else{
      await reason(page,foreign?'vulneracion':cu?'consulta':'promocion',cu?'estadisticas':'',full);
      if(cu){
       assert.equal(await page.locator('#resource-estadisticas a').count(),3);
       assert.equal(await page.locator('#resource-estadisticas a').filter({hasText:'Estudios'}).getAttribute('href'),'https://www.defensorianinez.cl/home-adulto/estudios-y-estadisticas/');
       assert(await page.locator('#relato').isHidden());assert(await page.getByRole('textbox',{name:'¿Qué información necesitas?'}).isVisible());await page.locator('#stats-descripcion').fill('Información sintética de estudios y estadísticas');
      }
      if(foreign){
       await page.locator('#riesgo-si').check();assert(await page.locator('#urgencia').isVisible());await page.locator('#relacion').selectOption('docente');
       await page.locator('#hechos-region').selectOption('12');assert.equal(await page.locator('#hechos-commune').getAttribute('required'),null);assert.equal(await page.locator('#momento').count(),0);
       await page.getByRole('radio',{name:'Como grupo',exact:true}).check();await page.locator('#grupo-nna-1-nombre').fill('Curso sintético');await page.locator('#grupo-nna-1-descripcion').fill('Antecedentes colectivos de prueba, sin nombres de integrantes');await page.locator('#grupo-nna-1-cantidad').fill('25');
       await page.locator('#relato').fill('Relato colectivo sintético');
      }
      if(scenario==='promocion'){
       await page.locator('input[name=organizacion][value=no]').check();await page.locator('#actividad').selectOption('Taller');await page.locator('#publico').fill('Comunidad educativa de prueba');await page.locator('#relato').fill('Descripción sintética de actividad');
      }
      await page.locator('#adult-region').selectOption('12');
      if(scenario==='promocion'){
       assert.equal(await page.locator('#promocion-region').inputValue(),'12');assert(await page.locator('#promocion-commune').getAttribute('required')!==null);
       await page.locator('#promocion-region').selectOption('13');await page.locator('#promocion-commune').selectOption('Santiago');await page.locator('#adult-region').selectOption('05');assert.equal(await page.locator('#promocion-region').inputValue(),'13');
       await page.locator('#fecha-actividad').fill('2020-01-01');await page.locator('#revisar').click();await page.locator('#adult-errors').filter({hasText:'anterior a la fecha de solicitud'}).waitFor();await shot(page,[scenario,size,theme,'fecha-invalida'].join('-'));
       await page.locator('#fecha-actividad').fill(await page.locator('#fecha-actividad').getAttribute('min'));
      }
      await page.locator('#email').fill('x@x.x');await page.locator('#telefono').fill('12345678');assert.equal(await page.locator('#telefono').getAttribute('maxlength'),'8');
      await shot(page,[scenario,size,theme,'formulario'].join('-'));
      if(foreign&&size==='escritorio'&&theme==='light'){
       let warned=false;page.once('dialog',async dialog=>{assert.equal(dialog.type(),'beforeunload');warned=true;await dialog.dismiss();});
       await page.reload({waitUntil:'domcontentloaded',timeout:5000}).catch(()=>{});
       assert(warned,'Falta advertencia nativa de recarga');evidence.at(-1).nativeBeforeUnloadChecked=true;assert.equal(await page.locator('#grupo-nna-1-nombre').inputValue(),'Curso sintético');
      }

      await page.locator('#revisar').click();await page.locator('#vista-previa').waitFor();await shot(page,[scenario,size,theme,'revision'].join('-'));
      await page.getByRole('button',{name:'Enviar comunicación',exact:true}).focus();await page.keyboard.press('Tab');assert(await page.evaluate(()=>!!document.activeElement?.closest('dialog[open]')),'Foco fuera del diálogo');
      if(foreign){assert(await page.locator('#vista-previa').getByRole('heading',{name:'Grupo de NNA 1'}).isVisible());assert(await page.locator('#vista-previa').getByRole('heading',{name:'Identificación declarada'}).isVisible());}
     }
    }
    assert.deepEqual(errors,[]);assert.equal(mock.calls.filter(c=>c.p.includes('/comunicaciones')&&c.m==='POST').length,0);
    await context.close();
   }
  }
 }
}finally{await fs.writeFile(path.join(out,'resultados.json'),JSON.stringify(evidence,null,2));await browser.close();}
console.log('Validación visual controlada completada: '+evidence.length+' capturas; cero Comunicaciones reales y cero consultas gubernamentales.');
