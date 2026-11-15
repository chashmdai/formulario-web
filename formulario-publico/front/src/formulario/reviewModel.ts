import {fileSize} from '../ui/AttachmentStatus';
import type {IdentityView} from './api';
import {groupIndexes,groupFields} from './adultRules';
import type {InstitutionReference,PersonReference} from './adultRules';
import {regiones} from './territorio';

/**
 * Reglas de presentación compartidas por la revisión previa y el comprobante PDF.
 * Son funciones puras: no modifican la captura y solo usan la proyección pública de identidad
 * (valores declarados y si se informó un RUN). Nunca reciben antecedentes del Registro Civil.
 */
export interface SummaryRow {key:string;label:string;value:string;long:boolean}
export interface SummarySection {title:string;description?:string;rows:SummaryRow[];empty?:string;note?:string}
export interface FileSummary {nombre:string;tamanio:number}
export interface AuthenticatedIdentity {run:string;nombres:string;apellidos:string}
export interface AdultSummaryInput {
  campos:Record<string,string>;personas:PersonReference[];instituciones:InstitutionReference[];
  presenter:IdentityView;authenticatedIdentity:AuthenticatedIdentity|null;people:ReadonlyMap<string,IdentityView>;files:FileSummary[];
}
export interface NnaSummaryInput {campos:Record<string,string>;identity:IdentityView;attachments:FileSummary[]}

const labels:Record<string,string>={motivo:'Motivo principal',tema:'Tipo de consulta','consulta-confirmada':'Orientación revisada','stats-confirm':'Fuentes públicas revisadas','consulta-general-detalle':'Consulta general','entrevista-motivo':'Motivo de la entrevista','entrevista-rol':'Perfil de quien solicita','entrevista-rol-otro':'Otro perfil','entrevista-entidad':'Institución o medio','stats-descripcion':'Información solicitada','stats-periodo':'Periodo','stats-desglose':'Desglose','adult-region':'Región de residencia','adult-commune':'Comuna de residencia','adult-address':'Dirección informada',email:'Correo electrónico',telefono:'Celular',relato:'Relato',riesgo:'Riesgo inminente',relacion:'Relación','relacion-familiar':'Parentesco','hechos-region':'Región de los hechos','hechos-commune':'Comuna de los hechos',lugar:'Lugar de referencia','identifica-responsables':'Identifica personas o instituciones',organizacion:'Representa una organización','tipo-organizacion':'Tipo de organización','nombre-organizacion':'Nombre de la organización',actividad:'Actividad',publico:'Público destinatario','fecha-actividad':'Fecha tentativa','promocion-region':'Región de la actividad','promocion-commune':'Comuna de la actividad',primerNombre:'Primer nombre',segundoNombre:'Segundo nombre',primerApellido:'Primer apellido',segundoApellido:'Segundo apellido',fechaNacimiento:'Fecha de nacimiento',sexo:'Sexo registral',edad:'Edad aproximada',alias:'Alias o nombre conocido',rol:'Vínculo con la situación relatada',region:'Región',comuna:'Comuna',datosDesconocidos:'No conoce otros datos del NNA',nombre:'Nombre',tipo:'Tipo','adult-preferred':'Nombre preferido','adult-treatment':'Preferencia de tratamiento','adult-treatment-text':'Tratamiento indicado','afectados-modalidad':'Identificación de afectados',nacionalidad:'Nacionalidad',tipoDocumento:'Tipo de documento',numeroDocumento:'Número de documento',descripcion:'Antecedentes del grupo',cantidad:'Cantidad aproximada de NNA',edades:'Edades o tramo aproximado'};
const options:Record<string,string>={consulta:'Realizar una consulta',vulneracion:'Informar una vulneración de derechos',promocion:'Actividad de Promoción y Difusión',general:'Consulta general',entrevista:'Entrevista',estadisticas:'Estudios, publicaciones o estadísticas sobre niñez y adolescencia',si:'Sí',no:'No',true:'Sí',false:'No',estudiante:'Estudiante',independiente:'Persona independiente / a título personal',prensa:'Prensa o medio de comunicación',otro:'Otro',madre:'Madre',padre:'Padre',cuidador:'Cuidador o cuidadora','otro-familiar':'Otro familiar',docente:'Docente o profesional educativo',salud:'Profesional de salud',testigo:'Persona testigo',organizacion:'Representante de una organización',individual:'Individualmente',grupal:'Grupos de NNA',mixta:'Personas y grupos',nombre:'Usen mi nombre',ella:'Ella',el:'Él',elle:'Elle',otra:'De otra forma',noresponde:'Prefiero no responder',PASAPORTE:'Pasaporte',IDENTIDAD_EXTRANJERA:'Documento de identidad extranjero',OTRO:'Otro documento de identificación'};
const enumKeys=new Set(['motivo','tema','consulta-confirmada','stats-confirm','entrevista-rol','riesgo','relacion','identifica-responsables','organizacion','datosDesconocidos','adult-treatment','afectados-modalidad','tipoDocumento']);
function translated(key:string,value:string){if(key.endsWith('-region')||key==='region')return regiones[value]||value;return enumKeys.has(key)?options[value]||value:value;}
const contactKeys=new Set(['email','telefono']);
const locationKeys=new Set(['adult-region','adult-commune','adult-address','hechos-region','hechos-commune','lugar','promocion-region','promocion-commune']);
const storyKeys=new Set(['relato','consulta-general-detalle','entrevista-motivo','stats-descripcion']);
const preferenceKeys=new Set(['adult-preferred','adult-treatment','adult-treatment-text']);
const longText=(value:string)=>value.length>180||value.includes('\n');
const fileRows=(files:FileSummary[]):[string,string][]=>files.length?files.map((file,i)=>[`Archivo ${i+1}`,`${file.nombre} · ${fileSize(file.tamanio)}`]):[['Archivos','Sin archivos adjuntos']];

function adultSection(title:string,values:Record<string,string|undefined>,description?:string):SummarySection{
  const rows=Object.entries(values).filter((entry):entry is [string,string]=>!!entry[1])
    .map(([key,value])=>({key,label:labels[key]||key,value:translated(key,value),long:storyKeys.has(key)||longText(value)}));
  return {title,rows,empty:'Sin datos en esta sección.',...(description?{description}:{})};
}
/** Descripción de la identificación del presentador adulto; nunca afirma una verificación inexistente. */
export function adultIdentityDescription(presenter:IdentityView){
  return presenter.autenticada?'Identidad autenticada con ClaveÚnica.':presenter.camposFaltantes.includes('tipoDocumento')?'Los antecedentes de tu documento son declarados por ti; no equivalen a una identidad autenticada.':'Identificación declarada. Los antecedentes recuperados del Registro Civil se mantienen protegidos y no se muestran aquí.';
}
export function adultSections(input:AdultSummaryInput):SummarySection[]{
  const counts=new Map<string,number>();
  const entries=Object.entries(input.campos).filter(([key])=>!key.startsWith('grupo-nna-'));
  const pick=(keys:Set<string>)=>Object.fromEntries(entries.filter(([key])=>keys.has(key)));
  const communication=Object.fromEntries(entries.filter(([key])=>![contactKeys,locationKeys,storyKeys,preferenceKeys].some(keys=>keys.has(key))));
  const identity:Record<string,string>=input.presenter.autenticada&&input.authenticatedIdentity
    ?{RUN:input.authenticatedIdentity.run,Nombres:input.authenticatedIdentity.nombres,Apellidos:input.authenticatedIdentity.apellidos}:input.presenter.valoresDeclarados as Record<string,string>;
  return [
    adultSection('Identificación',{...identity,...pick(preferenceKeys)},adultIdentityDescription(input.presenter)),
    adultSection('Contacto',pick(contactKeys)),
    adultSection('Ubicación',pick(locationKeys)),
    adultSection('Comunicación',communication),
    adultSection('Relato',pick(storyKeys)),
    ...groupIndexes(input.campos).map(index=>adultSection('Grupo de NNA '+index,Object.fromEntries(groupFields.map(field=>[field,input.campos['grupo-nna-'+index+'-'+field]||''])))),
    ...input.personas.map(p=>{
      const kind=p.personaId.split('-')[0],number=(counts.get(kind)||0)+1;counts.set(kind,number);const v=input.people.get(p.personaId);
      return adultSection(`${kind==='afectado'?'NNA':kind==='relacionado'?'Persona adulta relacionada':'Persona señalada'} ${number}`,{...v?.valoresDeclarados,RUN:v?.runInformado?'Informado':'No informado',...p.detalles});
    }),
    ...input.instituciones.map((entity,i)=>adultSection(`Institución ${i+1}`,{...entity})),
    adultSection('Archivos adjuntos',Object.fromEntries(fileRows(input.files))),
  ];
}

const names:Record<string,string>={primerNombre:'Primer nombre',segundoNombre:'Segundo nombre',primerApellido:'Primer apellido',segundoApellido:'Segundo apellido',edad:'Edad'};
function contact(c:Record<string,string>,prefix:string):string{
  switch(c[`${prefix}-method`]){
    case 'celular':return `Celular: ${c[`${prefix}-celular`]}`;
    case 'email':return `Correo electrónico: ${c[`${prefix}-email`]}`;
    case 'social':return `${c[`${prefix}-network`]==='otra'?c[`${prefix}-other-network`]:c[`${prefix}-network`]}: ${c[`${prefix}-handle`]}`;
    case 'unsafe':return 'No tengo una forma segura de contacto';
    default:return '';
  }
}
function nnaSection(title:string,rows:[string,string|undefined][],note?:string):SummarySection{
  return {title,rows:rows.filter((row):row is [string,string]=>!!row[1]).map(([label,value])=>({key:label,label,value,long:label==='Relato'||longText(value)})),...(note?{note}:{})};
}
export const NNA_IDENTITY_NOTE='Los antecedentes recuperados del Registro Civil se mantienen protegidos y no se muestran aquí.';
export function nnaSections(input:NnaSummaryInput):SummarySection[]{
  const c=input.campos;
  const treatment=({nombre:'Usar mi nombre',ella:'Ella',el:'Él',elle:'Elle',noresponde:'Prefiero no responder'} as Record<string,string>)[c['nna-treatment']]||c['nna-treatment'];
  return [
    nnaSection('Identificación',[
      ...Object.entries(input.identity.valoresDeclarados).map(([k,v]):[string,string]=>[names[k]||k,v||'']),
      ['RUN',input.identity.runInformado?'Informado por ti':'No informado'],
      ['Nombre preferido',c['nna-preferred']],['Cómo referirnos a ti',c['nna-treatment']==='otra'?c['nna-treatment-text']:treatment],
    ],NNA_IDENTITY_NOTE),
    nnaSection('Contacto',[
      ['Forma principal',contact(c,'nna-primary')],['Otra forma de contacto',contact(c,'nna-secondary')],
      ['Seguridad del contacto',({yes:'Sí',unsure:'No estoy seguro o segura',no:'Necesito acordar otra forma'} as Record<string,string>)[c['nna-safe']]],['Precauciones',c['nna-contact-instructions']],
    ]),
    nnaSection('Ubicación',[['Región',regiones[c['nna-region']]],['Comuna',c['nna-commune']]]),
    nnaSection('Comunicación',[['Tipo de ingreso','Comunicación de niño, niña o adolescente']]),
    nnaSection('Relato',[['Relato',c['nna-story']]]),
    nnaSection('Archivos adjuntos',fileRows(input.attachments)),
  ];
}
