// @vitest-environment node
import {PDFDocument} from 'pdf-lib';
import {describe,expect,it} from 'vitest';
import {confirmedRegistration,evidenceJson,textSha256} from './evidence';
import {PAGE_SIZE,renderReceiptPdf,SUBSTITUTION_NOTICE,wrapText} from './pdf';
import type {PdfTraceEvent} from './pdf';
import {buildReceiptDocument,FOOTER_NOTICE,NOT_SIGNED_NOTICE} from './receiptModel';
import {parseSummary} from './summaryApi';
import {adultSummary,b64url,ID,nnaSummary,nodeAssets,pending,registered,registryContent} from './fixtures.test-utils';

const assets=nodeAssets(),now=new Date('2026-11-16T13:00:00Z');
async function render(summary:unknown,state=pending){
  const parsed=summary===null?null:parseSummary(summary,state.id);const model=buildReceiptDocument(parsed,state,now);
  const trace:PdfTraceEvent[]=[];const bytes=await renderReceiptPdf(model,assets,event=>trace.push(event));
  const pdf=await PDFDocument.load(bytes,{updateMetadata:false});const pages=pdf.getPageCount();
  // Las líneas ajustadas se unen con un espacio para reconstruir frases; el compacto ignora el ajuste.
  const text=trace.map(e=>e.text).join(' ').replace(/\s+/g,' '),compact=trace.map(e=>e.text.replace(/\s+/g,'')).join('');
  const values=trace.filter(e=>e.family==='body'&&e.size===10).map(e=>e.text.replace(/\s+/g,'')).join('');
  // Todo texto dibujado debe quedar dentro del área imprimible de su página: nada se recorta fuera del papel.
  for(const e of trace){expect(e.x).toBeGreaterThanOrEqual(36);expect(e.x+e.width).toBeLessThanOrEqual(PAGE_SIZE.width-36);expect(e.y).toBeGreaterThanOrEqual(20);expect(e.y).toBeLessThanOrEqual(PAGE_SIZE.height-20);}
  return {model,bytes,pdf,pages,trace,text,compact,values};
}
const story=['Primer párrafo del relato sintético con tildes: niñez, ¿qué pasó? ¡Ayuda!','',...Array.from({length:40},(_,i)=>`Párrafo ${i+1}. `+'Texto extenso de contexto que se repite para ocupar varias páginas sin perder contenido. '.repeat(1))].join('\n')+'\n'+'x'.repeat(260)+' '+'ñ'.repeat(1200);
const adultFields={motivo:'vulneracion',riesgo:'no',relacion:'testigo',relato:story,'adult-region':'13','hechos-region':'13','hechos-commune':'Santiago',email:'sintetico@example.test',telefono:'+56987654321','identifica-responsables':'true'};

describe('Ajuste de líneas del PDF',()=>{
  const measure=(value:string)=>value.length*5;
  it('conserva todos los caracteres visibles, los párrafos y divide palabras extensas',()=>{
    const text='Hola mundo\n\n  sangría '+'z'.repeat(45)+' fin';const lines=wrapText(text,100,measure);
    expect(lines.join('').replace(/\s+/g,'')).toBe(text.replace(/\s+/g,''));expect(lines[1]).toBe('');expect(lines[2].startsWith('  ')).toBe(true);
    expect(lines.every(line=>measure(line)<=100)).toBe(true);
  });
});

describe('Comprobante PDF de una comunicación adulta',()=>{
  it('genera un PDF A4 real, paginado, con estado pendiente y sin inventar folio',async()=>{
    const r=await render(adultSummary(pending,adultFields));
    expect(new TextDecoder().decode(r.bytes.slice(0,5))).toBe('%PDF-');
    const [w,h]=[r.pdf.getPage(0).getWidth(),r.pdf.getPage(0).getHeight()];expect(Math.round(w)).toBe(595);expect(Math.round(h)).toBe(842);
    expect(r.pages).toBeGreaterThanOrEqual(3);expect(Math.max(...r.trace.map(e=>e.page))).toBe(r.pages);
    for(let page=1;page<=r.pages;page++){
      const texts=r.trace.filter(e=>e.page===page).map(e=>e.text);
      expect(texts).toContain(`Página ${page} de ${r.pages}`);expect(texts).toContain(FOOTER_NOTICE);expect(texts).toContain('Referencia '+ID);
    }
    expect(r.text).toContain('Registro institucional pendiente');expect(r.text).toContain('Aún no asignado');expect(r.text).toContain(ID);
    expect(r.text).not.toMatch(/COM-\d/);expect(r.model.folio).toBeNull();
    expect(r.text).toContain('14 de noviembre de 2026');expect(r.text).toContain('(hora de Chile)');
    expect(r.pdf.getTitle()).toContain(ID);expect(r.pdf.getAuthor()).toBe('Defensoría de la Niñez');expect(r.pdf.getSubject()).toMatch(/No firmada digitalmente/);
  });
  it('incluye íntegramente el relato extenso, continúa entre páginas y respeta las secciones de la revisión',async()=>{
    const r=await render(adultSummary(pending,adultFields));
    expect(r.values).toContain(story.replace(/\s+/g,''));expect(r.text).toContain('Relato (continuación)');
    for(const title of ['Identificación','Contacto','Ubicación','Comunicación','Relato','NNA 1','Persona señalada 1','Institución 1','Archivos adjuntos'])expect(r.trace.some(e=>e.family==='heading'&&e.text===title)).toBe(true);
    for(const value of ['APELLIDO DECLARADO','sintetico@example.test','+56987654321','Informar una vulneración de derechos','Persona testigo','Santiago','SEÑALADO DECLARADO','Apodo sintético','Institución sintética','respaldo-sintético.pdf · 2 KB'])expect(r.text).toContain(value);
    expect(r.text).toContain('Identificación declarada. Los antecedentes recuperados del Registro Civil se mantienen protegidos y no se muestran aquí.');
    const relatoPage=r.trace.find(e=>e.text.startsWith('Primer párrafo'))!.page,lastPage=r.trace.filter(e=>e.text.includes('ñññ')).at(-1)!.page;expect(lastPage).toBeGreaterThan(relatoPage);
  });
  it('declara que no está firmado y anexa las evidencias originales con la huella del JSON descargable',async()=>{
    const r=await render(adultSummary(pending,adultFields));
    expect(r.text).toContain(NOT_SIGNED_NOTICE);expect(r.text).not.toMatch(/firmado digitalmente por|firma electrónica avanzada/i);
    const annex=r.trace.find(e=>e.text==='Anexo técnico')!,files=r.trace.find(e=>e.family==='heading'&&e.text==='Archivos adjuntos')!;expect(annex.page).toBeGreaterThan(files.page);
    const json=evidenceJson(pending.custodia);expect(r.compact).toContain(json.replace(/\s+/g,''));expect(r.text).toContain(textSha256(json));
    expect(r.text).toContain('comprobante-custodia-'+ID+'.json');expect(r.text).toContain('FORMULARIO_PUBLICO_DDN');expect(r.text).toContain('a'.repeat(64));
    expect(r.text).toContain('El registro institucional aún no existe');expect(r.text).toContain('c'.repeat(64));
  });
  it('incorpora el folio real solo desde el registro confirmado y coherente',async()=>{
    const r=await render(adultSummary(registered,adultFields),registered);
    expect(r.model.folio).toBe('COM-000123');expect(r.text).toContain('Registro institucional confirmado');expect(r.text).toContain('Folio COM-000123');
    expect(r.text).toContain('15 de noviembre de 2026');expect(r.text).not.toContain('Aún no asignado');expect(r.pdf.getKeywords()).toContain('COM-000123');
    expect(r.compact).toContain(evidenceJson(registered.registro).replace(/\s+/g,''));expect(r.text).toContain('comprobante-institucional-'+ID+'.json');
    const forged={...registered,registro:{...registered.registro!,registro:{folio:'COM-999999'}}};
    expect(()=>confirmedRegistration(forged)).toThrow();
    const otherOperation={...registered,registro:{...registered.registro!,comprobante:{...registered.registro!.comprobante,payload:b64url({...registryContent,operacionPublicaId:'99999999-9999-4999-8999-999999999999'})}}};
    expect(()=>confirmedRegistration(otherOperation)).toThrow();
  });
  it('muestra la identidad de ClaveÚnica igual que la revisión y nunca un antecedente registral',async()=>{
    const summary=adultSummary(pending,adultFields,{tipo:'ADULTO_CU',identidadAutenticada:{run:'12345678-5',nombres:'NOMBRE CU',apellidos:'APELLIDO CU'}});
    (summary.contenido.personas[0] as {autenticada:boolean}).autenticada=true;
    const r=await render(summary);
    expect(r.text).toContain('12345678-5');expect(r.text).toContain('NOMBRE CU');expect(r.text).toContain('Identidad autenticada con ClaveÚnica.');expect(r.text).toContain('Persona adulta · ClaveÚnica');
    expect(r.text).not.toContain('APELLIDO DECLARADO');
  });
  it('mantiene dentro de la página un nombre de archivo de longitud máxima sin espacios',async()=>{
    const longName='a'.repeat(176)+'.pdf',summary=adultSummary(pending,adultFields);summary.contenido.adjuntos[0].nombre=longName;
    const r=await render(summary);expect(r.compact).toContain(longName);expect(r.text).toContain('2 KB (2.048 bytes)');
  });
  it('representa visiblemente los caracteres sin glifo y dibuja otros alfabetos sin pérdidas',async()=>{
    const r=await render(adultSummary(pending,{...adultFields,relato:'Relato con emoji 😀, escritura 漢 y griego Ωμέγα; cirílico Жизнь.'}));
    expect(r.text).toContain('[U+1F600]');expect(r.text).toContain('[U+6F22]');expect(r.text).toContain('Ωμέγα');expect(r.text).toContain('Жизнь');
    expect(r.text).toContain(SUBSTITUTION_NOTICE);
  });
});

describe('Comprobante PDF NNA y comprobantes reducidos',()=>{
  it('aplica las reglas de la revisión NNA',async()=>{
    const r=await render(nnaSummary(pending,{'nna-region':'13','nna-primary-method':'unsafe','nna-preferred':'Preferido sintético','nna-treatment':'elle','nna-story':'Relato NNA sintético\nSegunda línea.'}));
    for(const value of ['NNA SINTÉTICO','Preferido sintético','Elle','No tengo una forma segura de contacto','Comunicación de niño, niña o adolescente','Relato NNA sintético','Segunda línea.','Sin archivos adjuntos.','Niña, niño o adolescente'])expect(r.text).toContain(value);
    expect(r.text).toContain('Los antecedentes recuperados del Registro Civil se mantienen protegidos y no se muestran aquí.');
  });
  it('tras la retirada genera un comprobante reducido sin reconstruir contenido',async()=>{
    const retired={id:ID,estado:'REGISTRADA_SIGER',custodia:registered.custodia,registro:registered.registro,contenido:null,archivosRetirados:[{id:'33333333-3333-4333-8333-333333333333',tamanio:2048,sha256:'c'.repeat(64)}]};
    const r=await render(retired,registered);
    expect(r.model.reduced).toBe('RETIRADO');expect(r.text).toContain('Comprobante reducido');expect(r.text).toContain('COM-000123');expect(r.text).toContain('(nombre no conservado)');
    expect(r.trace.some(e=>e.family==='heading'&&e.text==='Relato')).toBe(false);expect(r.text).toContain('c'.repeat(64));
    expect(r.text).toContain('33333333-3333-4333-8333-333333333333');expect(r.text).toContain('2 KB (2.048 bytes)');
  });
  it('sin resumen recuperable genera un comprobante reducido con las evidencias en memoria',async()=>{
    const r=await render(null);expect(r.model.reduced).toBe('NO_DISPONIBLE');expect(r.model.sections).toEqual([]);
    expect(r.text).toContain('no fue posible recuperar el resumen');expect(r.text).toContain('No disponible en este comprobante reducido.');expect(r.text).toContain(textSha256(evidenceJson(pending.custodia)));
  });
});

/** Solo inspección visual local: `RECEIPT_PDF_OUTPUT=/ruta npm test -- src/comprobante`. Datos sintéticos. */
describe.runIf(!!process.env.RECEIPT_PDF_OUTPUT)('Muestras visuales del comprobante',()=>{
  it('exporta comprobantes sintéticos',async()=>{
    const {mkdirSync,writeFileSync}=await import('node:fs');const dir=process.env.RECEIPT_PDF_OUTPUT!;mkdirSync(dir,{recursive:true});
    const samples:[string,unknown,typeof pending][]=[['adulto-pendiente',adultSummary(pending,adultFields),pending],['adulto-registrado',adultSummary(registered,adultFields),registered],
      ['nna-pendiente',nnaSummary(pending,{'nna-region':'13','nna-primary-method':'unsafe','nna-treatment':'elle','nna-story':'Relato NNA sintético.\nSegunda línea.'}),pending],
      ['reducido-retirado',{id:ID,estado:'REGISTRADA_SIGER',custodia:registered.custodia,registro:registered.registro,contenido:null,archivosRetirados:[{id:'33333333-3333-4333-8333-333333333333',tamanio:2048,sha256:'c'.repeat(64)}]},registered]];
    for(const [name,summary,state] of samples)writeFileSync(`${dir}/${name}.pdf`,(await render(summary,state)).bytes);
  });
});
