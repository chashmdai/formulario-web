import {PDFDocument,StandardFonts,rgb} from 'pdf-lib';
import type {Color,PDFFont,PDFPage} from 'pdf-lib';
import fontkit from '@pdf-lib/fontkit';
import type {SummaryRow} from '../formulario/reviewModel';
import {RegistrationNotConfirmedError,requireRegistrationEvidence} from './evidence';
import {FOOTER_NOTICE} from './receiptModel';
import type {ReceiptDocument} from './receiptModel';

/**
 * Generador del comprobante PDF A4. Maqueta, pagina y dibuja el modelo del comprobante con fuentes
 * incrustadas (OFL) y el logotipo vectorial institucional. No firma el documento ni lo presenta como firmado.
 */
export const FONT_KEYS=['roboto-400-latin','roboto-400-latin-ext','roboto-400-greek','roboto-400-cyrillic','roboto-400-vietnamese','roboto-400-symbols','roboto-400-math',
  'roboto-500-latin','roboto-500-latin-ext','cabin-600-latin','cabin-600-latin-ext','cabin-700-latin','cabin-700-latin-ext'] as const;
export type FontKey=typeof FONT_KEYS[number];
export interface PdfAssets {fonts:Record<FontKey,ArrayBuffer|Uint8Array>;logoSvg:string}
/** Evento de dibujo para pruebas: posición de la línea base y ancho efectivo, en puntos PDF. */
export interface PdfTraceEvent {page:number;text:string;size:number;family:FamilyName;x:number;y:number;width:number}
export const PAGE_SIZE={width:595.28,height:841.89};
type FamilyName='body'|'label'|'heading'|'title'|'mono';
interface Face {font:PDFFont;has:(codePoint:number)=>boolean}
type Family=Face[];

const PAGE:[number,number]=[595.28,841.89];
const MARGIN={left:54,right:54,top:46,bottom:66};
const WIDTH=PAGE[0]-MARGIN.left-MARGIN.right;
const hex=(value:string):Color=>rgb(parseInt(value.slice(1,3),16)/255,parseInt(value.slice(3,5),16)/255,parseInt(value.slice(5,7),16)/255);
const C={tinta:hex('#233a47'),texto:hex('#38515d'),suave:hex('#5b717c'),azul:hex('#115874'),azulOscuro:hex('#0d465f'),azulClaro:hex('#e8f5fb'),
  linea:hex('#dee7ec'),lineaFuerte:hex('#baccd5'),fondo:hex('#f6f9fb'),blanco:hex('#ffffff'),
  okFondo:hex('#e8f5ec'),okBorde:hex('#9fd0b0'),okTinta:hex('#1b5a35')};
const STRIPE=['#2fa9d7','#f05a30','#8bc53f','#ffe72c','#faaf4c','#e34d7f','#9e1f63'].map(hex);
export const SUBSTITUTION_NOTICE='Algunos caracteres no disponibles en las fuentes de este documento (por ejemplo, emojis) se muestran con su código Unicode, como [U+1F600]. El contenido enviado no se modificó.';

/** Normaliza para presentación y sustituye visiblemente lo que ninguna fuente puede dibujar: nada se omite en silencio. */
function prepare(text:string,family:Family,flag?:{substituted:boolean}):string{
  let out='';
  for(const ch of text.normalize('NFC').replace(/\r\n?/g,'\n').replace(/\t/g,'    ')){
    const cp=ch.codePointAt(0)!;
    if(ch==='\n'||family.some(face=>face.has(cp))&&!(cp<0x20||cp>=0x7f&&cp<0xa0)){out+=ch;continue;}
    out+=`[U+${cp.toString(16).toUpperCase().padStart(4,'0')}]`;if(flag)flag.substituted=true;
  }
  return out;
}
function runs(text:string,family:Family){
  const out:{text:string;face:Face}[]=[];
  for(const ch of text){const face=family.find(f=>f.has(ch.codePointAt(0)!))??family[0];const last=out.at(-1);if(last&&last.face===face)last.text+=ch;else out.push({text:ch,face});}
  return out;
}
const measure=(text:string,family:Family,size:number)=>runs(text,family).reduce((sum,run)=>sum+run.face.font.widthOfTextAtSize(run.text,size),0);
/** Ajuste de líneas: respeta saltos de párrafo, divide palabras más largas que el ancho y no pierde caracteres. */
export function wrapText(text:string,width:number,measureText:(value:string)=>number):string[]{
  const lines:string[]=[];
  for(const paragraph of text.split('\n')){
    let line='',current=0,start=true;
    const push=()=>{lines.push(line.replace(/\s+$/,''));line='';current=0;};
    for(const token of paragraph.match(/\s+|\S+/g)??[]){
      const w=measureText(token),space=/^\s/.test(token);
      if(current+w<=width||line===''&&space&&start){line+=token;current+=w;start=false;continue;}
      start=false;
      if(space){push();continue;}
      if(line)push();
      if(w<=width){line=token;current=w;continue;}
      for(const ch of token){const cw=measureText(ch);if(current+cw>width&&line)push();line+=ch;current+=cw;}
    }
    if(line||!lines.length||paragraph.trim()==='')push();
  }
  return lines;
}
function logoPaths(svg:string){
  const styles=Object.fromEntries([...svg.matchAll(/\.(st\d+)\s*\{\s*fill:\s*(#[0-9a-fA-F]{6})/g)].map(m=>[m[1],m[2]]));
  return [...svg.matchAll(/<path\s+class="(st\d+)"\s+d="([^"]+)"/g)].map(m=>({d:m[2],color:hex(styles[m[1]]??'#58595b')}));
}
const rounded=(w:number,h:number,r:number)=>`M ${r} 0 H ${w-r} Q ${w} 0 ${w} ${r} V ${h-r} Q ${w} ${h} ${w-r} ${h} H ${r} Q 0 ${h} 0 ${h-r} V ${r} Q 0 0 ${r} 0 Z`;

class Writer {
  pages:PDFPage[]=[];page!:PDFPage;pageNumber=0;y=0;
  private readonly families:Record<FamilyName,Family>;private readonly logo:{d:string;color:Color}[];
  constructor(private readonly doc:PDFDocument,families:Record<FamilyName,Family>,logoSvg:string,private readonly model:ReceiptDocument,private readonly trace?:(event:PdfTraceEvent)=>void){
    this.families=families;this.logo=logoPaths(logoSvg);
  }
  family(name:FamilyName){return this.families[name];}
  wrap(text:string,family:FamilyName,size:number,width:number){const f=this.families[family];return wrapText(prepare(text,f),width,value=>measure(value,f,size));}
  width(text:string,family:FamilyName,size:number){const f=this.families[family];return measure(prepare(text,f),f,size);}
  draw(text:string,x:number,baseline:number,family:FamilyName,size:number,color:Color){
    const f=this.families[family];let cursor=x;const prepared=prepare(text,f);
    for(const run of runs(prepared,f)){this.page.drawText(run.text,{x:cursor,y:baseline,size,font:run.face.font,color});cursor+=run.face.font.widthOfTextAtSize(run.text,size);}
    this.trace?.({page:this.pageNumber,text:prepared,size,family,x,y:baseline,width:cursor-x});
  }
  /** Dibuja una línea en la posición actual y avanza el cursor. */
  line(text:string,x:number,family:FamilyName,size:number,color:Color,leading:number){this.draw(text,x,this.y-leading+(leading-size)/2+size*0.22,family,size,color);this.y-=leading;}
  drawLogo(x:number,top:number,height:number){const scale=height/449;for(const p of this.logo)this.page.drawSvgPath(p.d,{x,y:top,scale,color:p.color});return 1024*scale;}
  stripe(height:number){const w=PAGE[0]/STRIPE.length;STRIPE.forEach((color,i)=>this.page.drawRectangle({x:i*w,y:PAGE[1]-height,width:w+0.5,height,color}));}
  rule(y:number,color=C.linea,thickness=0.8,x=MARGIN.left,width=WIDTH){this.page.drawLine({start:{x,y},end:{x:x+width,y},thickness,color});}
  box(x:number,top:number,w:number,h:number,fill:Color,border?:Color,r=6){this.page.drawSvgPath(rounded(w,h,r),{x,y:top,color:fill,...(border?{borderColor:border,borderWidth:0.8}:{})});}
  addPage(first=false){
    this.page=this.doc.addPage(PAGE);this.pages.push(this.page);this.pageNumber=this.pages.length;
    if(first){this.stripe(5);this.y=PAGE[1]-MARGIN.top;return;}
    this.stripe(3);const top=PAGE[1]-24;this.drawLogo(MARGIN.left,top,22);
    const title=this.model.title,ref='Referencia '+this.model.reference;
    this.draw(title,PAGE[0]-MARGIN.right-this.width(title,'label',8.5),top-9,'label',8.5,C.azulOscuro);
    this.draw(ref,PAGE[0]-MARGIN.right-this.width(ref,'body',7.5),top-20,'body',7.5,C.suave);
    this.rule(top-30);this.y=top-44;
  }
  /** Garantiza espacio vertical; si no cabe, continúa en una página nueva. */
  ensure(height:number){if(this.y-height<MARGIN.bottom){this.addPage();return true;}return false;}
  paragraph(text:string,family:FamilyName,size:number,color:Color,leading:number,x=MARGIN.left,width=WIDTH){for(const l of this.wrap(text,family,size,width)){this.ensure(leading);this.line(l,x,family,size,color,leading);}}
  footers(){
    const total=this.pages.length;
    this.pages.forEach((page,i)=>{
      this.page=page;this.pageNumber=i+1;const y=MARGIN.bottom-26;this.rule(y+12,C.linea,0.6);
      this.draw(FOOTER_NOTICE,MARGIN.left,y,'body',7.2,C.suave);
      this.draw('Referencia '+this.model.reference,MARGIN.left,y-10,'body',7.2,C.suave);
      const label=`Página ${i+1} de ${total}`;this.draw(label,PAGE[0]-MARGIN.right-this.width(label,'label',7.8),y,'label',7.8,C.azulOscuro);
    });
  }
}

function cover(w:Writer,m:ReceiptDocument){
  w.addPage(true);const top=w.y;const logoWidth=w.drawLogo(MARGIN.left,top,46);
  const right=PAGE[0]-MARGIN.right,line1='Formulario Público',line2='Plataforma Digital de Comunicaciones';
  w.page.drawLine({start:{x:MARGIN.left+logoWidth+14,y:top-4},end:{x:MARGIN.left+logoWidth+14,y:top-42},thickness:0.8,color:C.linea});
  w.draw(line1,right-w.width(line1,'heading',11),top-18,'heading',11,C.azulOscuro);
  w.draw(line2,right-w.width(line2,'body',8.5),top-32,'body',8.5,C.suave);
  w.y=top-62;w.rule(w.y);w.y-=24;
  for(const l of w.wrap(m.title,'title',20,WIDTH))w.line(l,MARGIN.left,'title',20,C.tinta,25);
  w.y-=2;w.paragraph(m.reduced?'Comprobante reducido de tu comunicación a la Defensoría de la Niñez.':'Resumen de lo que enviaste a la Defensoría de la Niñez.','body',10.5,C.texto,15);w.y-=12;
  // Estado de la comunicación: siempre con texto, nunca solo con color.
  const fill=C.okFondo,border=C.okBorde,ink=C.okTinta,h=46;
  w.box(MARGIN.left,w.y,WIDTH,h,fill,border);w.page.drawCircle({x:MARGIN.left+20,y:w.y-h/2,size:5,color:ink});
  w.draw(m.status.label,MARGIN.left+36,w.y-19,'heading',12,ink);w.draw(m.status.detail,MARGIN.left+36,w.y-34,'body',9.5,ink);w.y-=h+14;
  // Datos principales en dos columnas.
  const colW=(WIDTH-36)/2,cells=m.facts.map(([label,value])=>({label:w.wrap(label.toUpperCase(),'label',7.4,colW),value:w.wrap(value,'body',10,colW)}));
  const rows:typeof cells[]=[];for(let i=0;i<cells.length;i+=2)rows.push(cells.slice(i,i+2));
  const rowH=(row:typeof cells)=>Math.max(...row.map(c=>c.label.length*10+c.value.length*13.5))+12;
  const total=rows.reduce((sum,row)=>sum+rowH(row),0)+16;
  w.ensure(total);w.box(MARGIN.left,w.y,WIDTH,total,C.fondo,C.linea);let y=w.y-12;
  rows.forEach((row,ri)=>{
    row.forEach((cell,ci)=>{let cy=y;const x=MARGIN.left+12+ci*(colW+12);
      for(const l of cell.label){w.draw(l,x,cy-8,'label',7.4,C.suave);cy-=10;}
      for(const l of cell.value){w.draw(l,x,cy-10.5,cell===row[0]&&ri===0?'heading':'body',10,C.tinta);cy-=13.5;}});
    y-=rowH(row);if(ri<rows.length-1)w.rule(y+5,C.linea,0.5,MARGIN.left+12,WIDTH-24);
  });
  w.y-=total+14;
  for(const notice of m.notices){
    const lines=w.wrap(notice,'body',9,WIDTH-30),h2=lines.length*12.6+14;w.ensure(h2);
    w.box(MARGIN.left,w.y,WIDTH,h2,C.azulClaro,undefined,4);w.page.drawRectangle({x:MARGIN.left,y:w.y-h2,width:3,height:h2,color:C.azul});
    let ny=w.y-7;for(const l of lines){w.draw(l,MARGIN.left+16,ny-9.6,'body',9,C.tinta);ny-=12.6;}w.y-=h2+8;
  }
  w.y-=8;
}
function sectionTitle(w:Writer,title:string,reserve:number){
  w.ensure(32+reserve);w.y-=6;w.draw(title,MARGIN.left,w.y-12,'heading',13,C.azulOscuro);w.y-=20;
  w.rule(w.y,C.linea,0.8);w.page.drawRectangle({x:MARGIN.left,y:w.y-0.6,width:34,height:2.2,color:C.azul});w.y-=8;
}
const LABEL_W=152,GAP=14,VALUE_W=WIDTH-LABEL_W-GAP,HASH=/^[a-f0-9]{64}$/;
function row(w:Writer,r:SummaryRow,valueFamily:'body'|'mono'='body',valueSize=10){
  const leading=valueSize*1.4;
  if(!r.long){
    const label=w.wrap(r.label,'label',8.6,LABEL_W),value=w.wrap(r.value,valueFamily,valueSize,VALUE_W);
    const h=Math.max(label.length*11.5,value.length*leading)+10;
    if(h<=PAGE[1]-MARGIN.top-MARGIN.bottom-80){
      w.ensure(h);const top=w.y;
      label.forEach((l,i)=>w.draw(l,MARGIN.left,top-5-9-i*11.5,'label',8.6,C.suave));
      value.forEach((l,i)=>w.draw(l,MARGIN.left+LABEL_W+GAP,top-5-leading+(leading-valueSize)/2+valueSize*0.22-i*leading,valueFamily,valueSize,C.tinta));
      w.y-=h;w.rule(w.y,C.linea,0.5);return;
    }
  }
  // Texto extenso: etiqueta arriba y valor a todo el ancho, línea a línea y entre páginas.
  const value=w.wrap(r.value,valueFamily,valueSize,WIDTH);
  w.ensure(16+Math.min(value.length,3)*leading);w.y-=4;w.line(r.label,MARGIN.left,'label',8.6,C.suave,13);
  for(const l of value){if(w.ensure(leading))w.line(r.label+' (continuación)',MARGIN.left,'label',8.6,C.suave,13);w.line(l,MARGIN.left,valueFamily,valueSize,C.tinta,leading);}
  w.y-=5;w.rule(w.y,C.linea,0.5);
}
function sections(w:Writer,m:ReceiptDocument){
  for(const s of m.sections){
    sectionTitle(w,s.title,30);
    if(s.description)w.paragraph(s.description,'body',9,C.suave,12.5);
    if(!s.rows.length&&s.empty)w.paragraph(s.empty,'body',9.5,C.suave,14);
    for(const r of s.rows)row(w,r);
    if(s.note){w.y-=3;w.paragraph(s.note,'body',8.5,C.suave,12);}
    w.y-=10;
  }
}
function annex(w:Writer,m:ReceiptDocument){
  w.addPage();w.line('Anexo técnico',MARGIN.left,'title',17,C.tinta,23);
  w.line('Evidencias originales de custodia y registro institucional',MARGIN.left,'heading',11,C.azulOscuro,17);w.y-=6;
  w.paragraph('Las evidencias se reproducen exactamente como fueron emitidas, en el mismo formato que su descarga JSON. Su integridad se verifica con la firma Ed25519 del campo «firma» sobre el contenido «payload» (base64url), usando la clave pública del emisor indicado en «keyId». Este PDF no contiene firmas digitales ni sustituye esas evidencias.','body',9,C.texto,12.8);w.y-=4;
  w.paragraph('El comprobante de custodia lo emitió el Formulario Público al recibir tu comunicación; no es un folio. El registro institucional lo emitió SIGER al registrar la comunicación e incluye el folio.','body',9,C.texto,12.8);
  w.y-=6;
  for(const e of m.evidence){
    sectionTitle(w,e.title,40);
    for(const [label,value] of e.fields)if(HASH.test(value))row(w,{key:label,label,value,long:false},'mono',8.2);else row(w,{key:label,label,value,long:false},'body',9);
    row(w,{key:'archivo',label:'Archivo JSON descargable',value:e.filename,long:false},'body',9);
    row(w,{key:'sha',label:'SHA-256 del archivo JSON',value:e.sha256,long:false},'mono',8.2);
    w.ensure(30);w.y-=6;w.line('Contenido JSON original (las líneas largas se dividen solo para su presentación)',MARGIN.left,'label',8.4,C.suave,14);
    const lines=e.json.split('\n').flatMap(l=>w.wrap(l,'mono',7.4,WIDTH-16));
    for(const l of lines){w.ensure(10.2);w.page.drawRectangle({x:MARGIN.left,y:w.y-10.2,width:WIDTH,height:10.2,color:C.fondo});w.line(l,MARGIN.left+8,'mono',7.4,C.tinta,10.2);}
    w.y-=12;
  }
  sectionTitle(w,'Huellas de los archivos adjuntos',30);
  if(m.files.length)for(const f of m.files){row(w,{key:f.label,label:f.label,value:f.size,long:false},'body',9);row(w,{key:f.id,label:'Identificador',value:f.id,long:false},'mono',8.2);row(w,{key:f.sha256,label:'SHA-256',value:f.sha256,long:false},'mono',8.2);}
  else w.paragraph(m.reduced==='NO_DISPONIBLE'?'No disponible en este comprobante reducido.':'Sin archivos adjuntos.','body',9.5,C.suave,14);
}

export async function renderReceiptPdf(model:ReceiptDocument,assets:PdfAssets,trace?:(event:PdfTraceEvent)=>void):Promise<Uint8Array>{
  // Defensa adicional ante invocaciones directas: el folio debe coincidir con un registro coherente anexado.
  if(model.registered!==true)throw new RegistrationNotConfirmedError();
  requireRegistrationEvidence(model.reference,model.folio,model.evidence);
  const doc=await PDFDocument.create();doc.registerFontkit(fontkit);
  const fonts={} as Record<FontKey,Face>;
  for(const key of FONT_KEYS){const font=await doc.embedFont(assets.fonts[key],{subset:true});const set=new Set(font.getCharacterSet());fonts[key]={font,has:cp=>set.has(cp)};}
  const courier=await doc.embedFont(StandardFonts.Courier);
  const fallback:Family=['roboto-400-latin','roboto-400-latin-ext','roboto-400-greek','roboto-400-cyrillic','roboto-400-vietnamese','roboto-400-symbols','roboto-400-math'].map(k=>fonts[k as FontKey]);
  const families:Record<FamilyName,Family>={body:fallback,label:[fonts['roboto-500-latin'],fonts['roboto-500-latin-ext'],...fallback],
    heading:[fonts['cabin-600-latin'],fonts['cabin-600-latin-ext'],...fallback],title:[fonts['cabin-700-latin'],fonts['cabin-700-latin-ext'],...fallback],
    mono:[{font:courier,has:cp=>cp>=0x20&&cp<0x7f},...fallback]};
  const flag={substituted:false};
  const texts=[...allTexts(model)];for(const t of texts)prepare(t,fallback,flag);
  const document:ReceiptDocument=flag.substituted?{...model,notices:[...model.notices,SUBSTITUTION_NOTICE]}:model;
  doc.setTitle(`${model.title} · ${model.reference}`,{showInWindowTitleBar:true});doc.setAuthor('Defensoría de la Niñez');
  doc.setSubject('Representación legible de una comunicación recibida por el Formulario Público. No firmada digitalmente.');
  doc.setCreator('Formulario Público · Defensoría de la Niñez');doc.setProducer('Formulario Público · Defensoría de la Niñez');
  doc.setKeywords(['comprobante','Defensoría de la Niñez',model.reference,model.folio]);doc.setLanguage('es-CL');
  doc.setCreationDate(model.generatedAt);doc.setModificationDate(model.generatedAt);
  const w=new Writer(doc,families,assets.logoSvg,document,trace);
  cover(w,document);sections(w,document);annex(w,document);w.footers();
  return doc.save({useObjectStreams:true});
}
function* allTexts(model:ReceiptDocument){
  yield model.title;yield model.status.label;yield model.status.detail;for(const [a,b] of model.facts){yield a;yield b;}yield* model.notices;
  for(const s of model.sections){yield s.title;if(s.description)yield s.description;if(s.note)yield s.note;for(const r of s.rows){yield r.label;yield r.value;}}
  for(const e of model.evidence){yield e.json;for(const [a,b] of e.fields){yield a;yield b;}}for(const f of model.files)yield f.label;
}
