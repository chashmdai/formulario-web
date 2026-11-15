import {fileSize} from '../ui/AttachmentStatus';
import type {IdentityView,JourneyKind} from '../formulario/api';
import {adultSections,nnaSections} from '../formulario/reviewModel';
import type {SummarySection} from '../formulario/reviewModel';
import type {SubmissionState} from '../formulario/submission';
import {confirmedRegistration,evidenceFiles,receivedAt} from './evidence';
import type {EvidenceFile} from './evidence';
import type {SubmissionSummary} from './summaryApi';

/**
 * Modelo del comprobante legible, independiente del formato. Lo genera el navegador con la proyección
 * pública del servidor y las mismas reglas de la revisión previa. No es un documento firmado.
 */
export type ReducedReason='RETIRADO'|'NO_DISPONIBLE';
export interface FileIntegrity {label:string;id:string;size:string;sha256:string}
const bytes=new Intl.NumberFormat('es-CL');
const exactSize=(n:number)=>`${fileSize(n)} (${bytes.format(n)} bytes)`;
export interface ReceiptDocument {
  reference:string;registered:boolean;folio:string|null;reduced:ReducedReason|null;
  title:string;status:{label:string;detail:string};facts:[string,string][];notices:string[];
  sections:SummarySection[];evidence:EvidenceFile[];files:FileIntegrity[];generatedAt:Date;
}
export const NOT_SIGNED_NOTICE='Este PDF es una representación legible generada en tu navegador a partir de la información custodiada por el Formulario Público. No está firmado digitalmente. Las evidencias verificables son los comprobantes JSON firmados con Ed25519 que se reproducen sin cambios en el anexo técnico y que puedes descargar por separado.';
export const FOOTER_NOTICE='Representación legible · no firmada digitalmente · evidencias en el anexo técnico';
const kindLabels:Record<JourneyKind,string>={ADULTO_CU:'Persona adulta · ClaveÚnica',ADULTO_DECLARADO:'Persona adulta · Identificación declarada',NNA:'Niña, niño o adolescente'};
const chile=new Intl.DateTimeFormat('es-CL',{dateStyle:'long',timeStyle:'short',timeZone:'America/Santiago'});
export function chileTime(value:string|Date|null):string{
  if(!value)return 'No disponible';
  const date=value instanceof Date?value:new Date(value);
  return Number.isNaN(date.getTime())?String(value):`${chile.format(date)} (hora de Chile)`;
}
function presenter(personas:IdentityView[],id:string){const p=personas.find(view=>view.personaId===id);if(!p)throw new Error('Resumen sin presentador');return p;}
function contentSections(summary:SubmissionSummary):SummarySection[]{
  const c=summary.content;if(!c)return [];
  if(c.tipo==='NNA')return nnaSections({campos:c.campos,identity:presenter(c.personas,'presentador-nna'),attachments:c.adjuntos});
  return adultSections({campos:c.campos,personas:c.relaciones,instituciones:c.instituciones,presenter:presenter(c.personas,'presentador-adulto'),
    authenticatedIdentity:c.identidadAutenticada,people:new Map(c.personas.map(view=>[view.personaId,view])),files:c.adjuntos});
}

export function buildReceiptDocument(summary:SubmissionSummary|null,fallback:SubmissionState,now=new Date()):ReceiptDocument{
  const state=summary?.state??fallback;
  const registration=confirmedRegistration(state),registered=!!registration;
  const reduced:ReducedReason|null=!summary?'NO_DISPONIBLE':summary.content?null:'RETIRADO';
  const facts:[string,string][]=[
    ['Referencia de recepción',state.id],
    ['Estado',registered?'Registrada en SIGER':'Recibida · registro en SIGER pendiente'],
    ['Folio institucional',registration?.folio??'Aún no asignado'],
    ['Fecha de recepción',chileTime(receivedAt(state))],
  ];
  if(registration)facts.push(['Fecha de registro institucional',chileTime(registration.registradaEn)]);
  if(summary?.content)facts.push(['Tipo de comunicación',kindLabels[summary.content.tipo]]);
  facts.push(['Comprobante generado',chileTime(now)]);
  const notices=[registered
    ?'SIGER confirmó el registro institucional de tu comunicación con el folio indicado. Anota y guarda el folio: es importante para consultar el estado y dar seguimiento a tu gestión.'
    :'Tu comunicación fue recibida y se conserva en el Formulario Público. Su registro institucional en SIGER está pendiente, por lo que aún no tiene folio. La referencia de recepción identifica tu envío, pero no es un folio institucional. No necesitas volver a enviar tu comunicación.'];
  if(reduced==='RETIRADO')notices.push('Comprobante reducido: los antecedentes de esta comunicación ya fueron entregados a SIGER y el Formulario Público dejó de conservarlos. Este documento incluye solo la referencia, el estado, el folio, las huellas de los archivos y las evidencias técnicas.');
  if(reduced==='NO_DISPONIBLE')notices.push('Comprobante reducido: no fue posible recuperar el resumen de tu comunicación desde esta sesión. Este documento incluye la referencia, el estado y las evidencias técnicas disponibles; no reconstruye el contenido enviado.');
  notices.push(NOT_SIGNED_NOTICE);
  const files:FileIntegrity[]=summary?.content
    ?summary.content.adjuntos.map((file,i)=>({label:`Archivo ${i+1} · ${file.nombre}`,id:file.id,size:exactSize(file.tamanio),sha256:file.sha256}))
    :(summary?.retiredFiles??[]).map((file,i)=>({label:`Archivo ${i+1} (nombre no conservado)`,id:file.id,size:exactSize(file.tamanio),sha256:file.sha256}));
  return {reference:state.id,registered,folio:registration?.folio??null,reduced,
    title:registered?'Comprobante de comunicación registrada':'Comprobante de recepción de comunicación',
    status:registered?{label:'Registro institucional confirmado',detail:`Folio ${registration!.folio}`}:{label:'Registro institucional pendiente',detail:'Recibida y custodiada · sin folio aún'},
    facts,notices,sections:contentSections(summary??{state,content:null,retiredFiles:[]}),evidence:evidenceFiles(state),files,generatedAt:now};
}
export const pdfFilename=(id:string)=>`comprobante-comunicacion-${id}.pdf`;
