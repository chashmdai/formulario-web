import type { FormErrors, FormValues } from './nnaRules';
import { canonicalMobile, normalizeContact, validEmail } from './contacts';
import type { RelatedRole } from './PersonIdentity';
import type { IdentityView } from './api';
import { comunas, regiones } from './territorio';

export interface AdultPersonCard {id:string;role:RelatedRole;kind?:'persona'|'institucion';detalles:Record<string,string>}
export interface PersonReference {personaId:string;detalles:Record<string,string>}
export interface InstitutionReference {nombre:string;tipo:string;rol:string;region:string;comuna:string}
export interface AdultCapture {campos:Record<string,string>;personas:PersonReference[];instituciones:InstitutionReference[];adjuntos:string[]}
const narratives=new Set(['relato','entrevista-motivo','consulta-general-detalle','stats-descripcion']);
const labels:Record<string,string>={motivo:'Motivo principal',tema:'Tipo de consulta','adult-region':'Región',relato:'Relato',publico:'Público al que se dirige',actividad:'Actividad','entrevista-motivo':'Motivo de la entrevista','entrevista-rol':'Perfil de quien solicita','entrevista-rol-otro':'Otro perfil','stats-descripcion':'Antecedentes estadísticos solicitados','consulta-general-detalle':'Consulta general','nombre-organizacion':'Nombre de la organización','tipo-organizacion':'Tipo de organización','relacion-familiar':'Parentesco','relacion':'Tu relación con el NNA'};
export function adultMaxLength(id:string):number{return narratives.has(id)?5000:500;}
export function adultRequired(c:FormValues,complete=true):string[]{
  const required=['motivo'];
  if(c.motivo==='consulta')required.push('tema','consulta-confirmada',...(c.tema==='estadisticas'?['stats-confirm']:[]));
  if(!complete)return required;
  required.push('adult-region');
  if(c.motivo==='consulta') {
    if(c.tema==='general')required.push('consulta-general-detalle');
    if(c.tema==='entrevista')required.push('entrevista-motivo','entrevista-rol',...(c['entrevista-rol']==='otro'?['entrevista-rol-otro']:[]));
    if(c.tema==='estadisticas')required.push('stats-descripcion');
  }
  if(c.motivo==='vulneracion')required.push('riesgo','relacion','relato',...(c.relacion==='otro-familiar'?['relacion-familiar']:[]));
  if(c.motivo==='promocion')required.push('organizacion','actividad','publico','relato',...(c.organizacion==='si'?['tipo-organizacion','nombre-organizacion']:[]));
  return required;
}
export function adultValues(c:FormValues):Record<string,string>{
  const active=['motivo','adult-region','adult-commune','adult-address','email','telefono','relato'];
  if(c.motivo==='consulta'){
    active.push('tema','consulta-confirmada');
    if(c.tema==='general')active.push('consulta-general-detalle');
    if(c.tema==='estadisticas')active.push('stats-confirm','stats-descripcion','stats-periodo','stats-desglose');
    if(c.tema==='entrevista'){active.push('entrevista-motivo','entrevista-rol');if(c['entrevista-rol']==='otro')active.push('entrevista-rol-otro');if(['estudiante','prensa'].includes(c['entrevista-rol']))active.push('entrevista-entidad');}
  }
  if(c.motivo==='vulneracion'){active.push('riesgo','relacion','hechos-region','hechos-commune','lugar','momento','identifica-responsables');if(c.relacion==='otro-familiar')active.push('relacion-familiar');}
  if(c.motivo==='promocion'){active.push('organizacion','actividad','publico','fecha-actividad','promocion-region','promocion-commune');if(c.organizacion==='si')active.push('tipo-organizacion','nombre-organizacion');}
  return Object.fromEntries(active.filter(id=>c[id]).map(id=>[id,normalizeContact(id,c[id])]));
}
export function validateAdult(c:FormValues,complete=true):FormErrors{
  const errors:FormErrors={};
  for(const id of adultRequired(c,complete))if(!c[id]?.trim())errors[id]=`Completa ${labels[id]||'este campo'}.`;
  if(!['consulta','vulneracion','promocion'].includes(c.motivo||''))errors.motivo='Selecciona el motivo de contacto.';
  if(c.motivo==='consulta'){
    if(c['consulta-confirmada']!=='true')errors['consulta-confirmada']='Revisa y confirma la orientación antes de continuar.';
    if(!['general','entrevista','estadisticas'].includes(c.tema||''))errors.tema=c.tema==='empleo'?'Esta alternativa se gestiona en Empleos Públicos.':'Selecciona el tipo de consulta.';
    if(c.tema==='estadisticas'&&c['stats-confirm']!=='true')errors['stats-confirm']='Confirma que revisaste el material disponible.';
  }
  if(!complete)return errors;
  const clean=adultValues(c);
  for(const [id,value] of Object.entries(clean)){
    if(value.length>adultMaxLength(id))errors[id]=`Este campo admite hasta ${adultMaxLength(id)} caracteres.`;
    if(id==='email'&&value&&!validEmail(value))errors[id]='Revisa el formato del correo electrónico.';
    if(id==='telefono'&&value&&canonicalMobile(value)===null)errors[id]='Escribe los ocho dígitos después de +56 9.';
  }
  for(const prefix of ['adult','hechos','promocion']){
    if(prefix==='hechos'&&c.motivo!=='vulneracion'||prefix==='promocion'&&c.motivo!=='promocion')continue;
    const region=c[`${prefix}-region`],commune=c[`${prefix}-commune`];
    if(region&&!regiones[region])errors[`${prefix}-region`]='Selecciona una región válida.';
    if(commune&&!(comunas[region]||[]).includes(commune))errors[`${prefix}-commune`]='Selecciona una comuna de la región indicada.';
  }
  const choices:Record<string,string[]>={};
  if(c.motivo==='consulta'&&c.tema==='entrevista')choices['entrevista-rol']=['estudiante','independiente','prensa','otro'];
  if(c.motivo==='vulneracion')Object.assign(choices,{riesgo:['si','no'],relacion:['madre','padre','cuidador','otro-familiar','docente','salud','testigo','organizacion','otro'],momento:['Está ocurriendo actualmente','Ocurrió recientemente','Ocurrió tiempo atrás','No lo sé']});
  if(c.motivo==='vulneracion'&&c.relacion==='otro-familiar')choices['relacion-familiar']=['Abuela o abuelo','Tía o tío','Hermana o hermano','Prima o primo','Madrastra o padrastro','Madrina o padrino','Otro parentesco familiar'];
  if(c.motivo==='promocion')Object.assign(choices,{organizacion:['si','no'],actividad:['Charla','Taller','Capacitación','Otra actividad']});
  if(c.motivo==='promocion'&&c.organizacion==='si')choices['tipo-organizacion']=['ONG o fundación','Establecimiento educacional','Servicio u organismo público','Empresa u otra persona jurídica','Otra organización'];
  for(const [id,options] of Object.entries(choices))if(c[id]&&!options.includes(c[id]))errors[id]='Selecciona una opción válida.';
  if(c.motivo==='promocion'&&c['fecha-actividad']){const date=c['fecha-actividad'];const parsed=new Date(`${date}T12:00:00Z`);if(!/^\d{4}-\d{2}-\d{2}$/.test(date)||Number(date.slice(0,4))<1||!Number.isFinite(parsed.getTime())||parsed.toISOString().slice(0,10)!==date)errors['fecha-actividad']='Revisa la fecha de la actividad.';}
  return errors;
}
export function activeCards(c:FormValues,cards:AdultPersonCard[]):AdultPersonCard[]{return c.motivo==='vulneracion'?cards.filter(card=>card.role!=='PERSONA_SENALADA'||c['identifica-responsables']==='true'):[];}
export function adultCapture(c:FormValues,cards:AdultPersonCard[],attachments:string[]):AdultCapture{
  const people:PersonReference[]=[],institutions:InstitutionReference[]=[];
  for(const card of activeCards(c,cards)){
    if(card.role==='PERSONA_SENALADA'&&card.kind==='institucion'){const d=card.detalles;institutions.push({nombre:d.nombre||'',tipo:d.tipo||'',rol:d.rol||'',region:d.region||'',comuna:d.comuna||''});continue;}
    const allowed=card.role==='AFECTADO_NNA'?['datosDesconocidos','region','comuna']:card.role==='ADULTO_RELACIONADO'?['relacion']:['alias','rol','region','comuna'];
    people.push({personaId:card.id,detalles:Object.fromEntries(allowed.filter(k=>card.detalles[k]).map(k=>[k,card.detalles[k]]))});
  }
  return {campos:adultValues(c),personas:people,instituciones:institutions,adjuntos:[...attachments]};
}
export function validateCards(c:FormValues,cards:AdultPersonCard[],views:ReadonlyMap<string,IdentityView>):FormErrors{
  const errors:FormErrors={};if(c.motivo!=='vulneracion')return errors;const active=activeCards(c,cards);
  if(!active.some(card=>card.role==='AFECTADO_NNA'))errors['afectados-list']='Agrega al menos un NNA o indica que no conoces sus datos.';
  for(const card of active){
    const d=card.detalles,v=views.get(card.id);
    if(card.role==='PERSONA_SENALADA'&&card.kind==='institucion'){if(!d.nombre?.trim())errors[card.id]='Indica el nombre de la institución.';}
    else if(!v||v.estado!=='REVISADO')errors[card.id]='Revisa los datos de esta persona para continuar.';
    else if(card.role==='AFECTADO_NNA'&&!v.runInformado&&!Object.values(v.valoresDeclarados).some(x=>x?.trim())&&!d.region&&!d.comuna&&d.datosDesconocidos!=='true')errors[card.id]='Indica algún antecedente del NNA o marca que no conoces sus datos identificatorios.';
    else if(card.role==='PERSONA_SENALADA'&&!v.runInformado&&!['primerNombre','primerApellido'].some(k=>v.valoresDeclarados[k as 'primerNombre'|'primerApellido']?.trim())&&!d.alias?.trim()&&!d.rol?.trim())errors[card.id]='Indica al menos un dato que permita reconocer a la persona señalada.';
    if(d.region&&!regiones[d.region]||d.comuna&&!(comunas[d.region]||[]).includes(d.comuna))errors[card.id]='Revisa la región y comuna de esta persona o institución.';
  }
  return errors;
}
