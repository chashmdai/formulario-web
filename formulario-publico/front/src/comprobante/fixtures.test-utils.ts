import {readFileSync} from 'node:fs';
import path from 'node:path';
import {FONT_KEYS} from './pdf';
import type {FontKey,PdfAssets} from './pdf';
import type {SubmissionState} from '../formulario/submission';
import type {IdentityField,IdentityView} from '../formulario/api';

/** Datos exclusivamente sintéticos para pruebas del comprobante; sin claves ni proveedores reales. */
const root=path.resolve(__dirname,'../..');
export function nodeAssets():PdfAssets{
  const file=(key:FontKey)=>{const [family,weight,...subset]=key.split('-');return path.join(root,'node_modules/@fontsource',family,'files',`${family}-${subset.join('-')}-${weight}-normal.woff`);};
  return {fonts:Object.fromEntries(FONT_KEYS.map(key=>[key,readFileSync(file(key))])) as unknown as Record<FontKey,Uint8Array>,logoSvg:readFileSync(path.join(root,'src/prototipo/assets/logo.svg'),'utf8')};
}
export const b64url=(value:unknown)=>Buffer.from(JSON.stringify(value)).toString('base64url');
export const ID='11111111-1111-4111-8111-111111111111';
export const custody={algoritmo:'Ed25519',keyId:'prueba-publica',payload:b64url({version:'1.0',id:ID,recibidaEn:'2026-11-14T15:30:00Z',tipoRecepcion:'CUSTODIA_PUBLICA',huellaContenido:'a'.repeat(64),huellaProcedencia:'b'.repeat(64),emisor:'FORMULARIO_PUBLICO_DDN',avisoVersion:'ADULTO_LAB_1'}),firma:'f'.repeat(86)};
export const registryContent={version:'1.0',emisor:'SIGER_DDN',hecho:'COMUNICACION_REGISTRADA',operacionPublicaId:ID,comunicacionId:'22222222-2222-4222-8222-222222222222',numeroFolio:123,folio:'COM-000123',registradaEn:'2026-11-15T12:00:00Z',canalOrigen:'FORMULARIO_WEB_PUBLICO',sha256:'a'.repeat(64),huellaProcedencia:'b'.repeat(64)};
export const pending:SubmissionState={id:ID,estado:'PENDIENTE_REGISTRO_SIGER',custodia:custody,registro:null};
export const registered:SubmissionState={...pending,estado:'REGISTRADA_SIGER',registro:{...{registro:registryContent},comprobante:{algoritmo:'Ed25519',keyId:'prueba-institucional',payload:b64url(registryContent),firma:'e'.repeat(86)}} as SubmissionState['registro']};
export const view=(personaId:string,valoresDeclarados:Record<string,string>,extra:Partial<IdentityView>={}):IdentityView=>({personaId,estado:'REVISADO',autenticada:false,runInformado:false,camposFaltantes:Object.keys(valoresDeclarados) as IdentityField[],camposObligatorios:[],valoresDeclarados,mensaje:'Sintético',...extra});
export function adultSummary(state:SubmissionState,campos:Record<string,string>,extra:Record<string,unknown>={}){
  return {id:state.id,estado:state.estado,custodia:state.custodia,registro:state.registro,archivosRetirados:[],contenido:{version:'1.0',tipo:'ADULTO_DECLARADO',recibidaEn:'2026-11-14T15:30:00Z',campos,
    relaciones:[{personaId:'afectado-1',detalles:{region:'13'}},{personaId:'senalado-1',detalles:{alias:'Apodo sintético',rol:'Vínculo relatado'}}],
    instituciones:[{nombre:'Institución sintética',tipo:'Educación',rol:'Antecedente sintético',region:'13',comuna:'Santiago'}],
    personas:[view('presentador-adulto',{primerApellido:'APELLIDO DECLARADO'},{runInformado:true}),view('afectado-1',{edad:'12'},{runInformado:true}),view('senalado-1',{primerNombre:'SEÑALADO DECLARADO'})],
    identidadAutenticada:null,adjuntos:[{id:'33333333-3333-4333-8333-333333333333',nombre:'respaldo-sintético.pdf',tamanio:2048,sha256:'c'.repeat(64),tipo:'application/pdf'}],...extra}};
}
export function nnaSummary(state:SubmissionState,campos:Record<string,string>){
  return {id:state.id,estado:state.estado,custodia:state.custodia,registro:state.registro,archivosRetirados:[],contenido:{version:'1.0',tipo:'NNA',recibidaEn:'2026-11-14T15:30:00Z',campos,relaciones:[],instituciones:[],
    personas:[view('presentador-nna',{primerNombre:'NNA SINTÉTICO',edad:'13'})],identidadAutenticada:null,adjuntos:[]}};
}
