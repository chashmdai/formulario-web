import { describe, expect, it } from 'vitest';
import { adultCapture, adultMaxLength, adultValues, validateAdult, validateCards } from './adultRules';
import type { AdultPersonCard } from './adultRules';
import type { IdentityView } from './api';
const general=()=>({motivo:'consulta',tema:'general','consulta-confirmada':'true','consulta-general-detalle':'Consulta sintética','adult-region':'13'});
const promotion=()=>({motivo:'promocion',organizacion:'no',actividad:'Taller',publico:'Adolescentes',relato:'Relato sintético','adult-region':'13'});
const violation=()=>({motivo:'vulneracion',riesgo:'no',relacion:'testigo',relato:'Relato sintético','adult-region':'13'});
const view=(id:string):IdentityView=>({personaId:id,estado:'REVISADO',autenticada:false,runInformado:false,camposFaltantes:['primerNombre','primerApellido'],camposObligatorios:[],valoresDeclarados:{},mensaje:''});
describe('Reglas de captura adulta trasladadas del mockup',()=>{
  it('distingue paso de motivo y captura completa, y bloquea la ruta de empleo',()=>{
    expect(validateAdult({motivo:'vulneracion'},false)).toEqual({});expect(validateAdult({motivo:'vulneracion'})).toHaveProperty('relato');
    expect(validateAdult({...general(),tema:'empleo'})).toHaveProperty('tema');expect(validateAdult({...general(),'consulta-confirmada':'false'})).toHaveProperty('consulta-confirmada');
  });
  it('consulta general admite 5000 caracteres y solo sus campos activos',()=>{
    const c={...general(),'consulta-general-detalle':'ñ'.repeat(5000),'stats-descripcion':'Dato oculto',riesgo:'si',publico:'Oculto'};
    expect(validateAdult(c)).toEqual({});expect(adultValues(c)).toEqual({...general(),'consulta-general-detalle':'ñ'.repeat(5000)});
    expect(validateAdult({...c,'consulta-general-detalle':'x'.repeat(5001)})).toHaveProperty('consulta-general-detalle');expect(adultMaxLength('email')).toBe(500);
  });
  it('entrevista exige otro perfil cuando corresponde y omite la entidad de un independiente',()=>{
    const c={...general(),tema:'entrevista','entrevista-motivo':'Motivo','entrevista-rol':'otro'};expect(validateAdult(c)).toHaveProperty('entrevista-rol-otro');
    expect(validateAdult({...c,'entrevista-rol-otro':'Perfil sintético'})).toEqual({});
    expect(adultValues({...c,'entrevista-rol':'independiente','entrevista-entidad':'Oculta','entrevista-rol-otro':'Oculto'})).not.toHaveProperty('entrevista-entidad');
    expect(adultValues({...c,'entrevista-rol':'prensa','entrevista-entidad':'Medio sintético'})).toHaveProperty('entrevista-entidad','Medio sintético');
  });
  it('estadísticas conserva periodo/desglose y exige revisión de fuentes',()=>{
    const c={...general(),tema:'estadisticas','stats-descripcion':'Datos no publicados','stats-periodo':'2025','stats-desglose':'Región'};expect(validateAdult(c)).toHaveProperty('stats-confirm');
    expect(validateAdult({...c,'stats-confirm':'true'})).toEqual({});expect(adultValues({...c,'stats-confirm':'true'})).not.toHaveProperty('consulta-general-detalle');
  });
  it('promoción valida organización, actividad, ubicación y fecha sin campos inactivos',()=>{
    expect(validateAdult(promotion())).toEqual({});expect(validateAdult({...promotion(),organizacion:'si'})).toHaveProperty('nombre-organizacion');
    expect(validateAdult({...promotion(),organizacion:'si','tipo-organizacion':'ONG o fundación','nombre-organizacion':'Entidad','promocion-region':'13','promocion-commune':'Santiago','fecha-actividad':'2027-02-28'})).toEqual({});
    for(const date of ['2027-02-30','0000-01-01'])expect(validateAdult({...promotion(),'fecha-actividad':date})).toHaveProperty('fecha-actividad');
    expect(validateAdult({...promotion(),'promocion-commune':'Santiago'})).toHaveProperty('promocion-commune');expect(adultValues({...promotion(),'nombre-organizacion':'Oculta'})).not.toHaveProperty('nombre-organizacion');
  });
  it('vulneración exige riesgo y parentesco condicional; valida ubicación y contacto',()=>{
    expect(validateAdult(violation())).toEqual({});expect(validateAdult({...violation(),riesgo:''})).toHaveProperty('riesgo');expect(validateAdult({...violation(),relacion:'otro-familiar'})).toHaveProperty('relacion-familiar');
    expect(validateAdult({...violation(),relacion:'otro-familiar','relacion-familiar':'Abuela o abuelo'})).toEqual({});expect(validateAdult({...violation(),email:'invalido',telefono:'123','hechos-region':'13','hechos-commune':'Comuna inventada'})).toHaveProperty('email');
    expect(validateAdult({...violation(),email:'sintetico@example.test',telefono:'+56 9 1234 5678'})).toEqual({});
  });
  it('no incorpora personas de otra ruta ni instituciones desmarcadas',()=>{
    const cards:AdultPersonCard[]=[{id:'afectado-1',role:'AFECTADO_NNA',detalles:{datosDesconocidos:'true'}},{id:'senalado-1',role:'PERSONA_SENALADA',kind:'institucion',detalles:{nombre:'Entidad',tipo:'Organismo público',region:'13',comuna:'Santiago'}}];
    expect(adultCapture(general(),cards,[]).personas).toEqual([]);expect(adultCapture(violation(),cards,[]).instituciones).toEqual([]);
    const captured=adultCapture({...violation(),'identifica-responsables':'true'},cards,['archivo']);expect(captured.instituciones).toEqual([{nombre:'Entidad',tipo:'Organismo público',rol:'',region:'13',comuna:'Santiago'}]);expect(captured.adjuntos).toEqual(['archivo']);
  });
  it('personas referenciadas incluyen solo metadatos declarados permitidos, sin autoafirmar fuentes',()=>{
    const cards:AdultPersonCard[]=[{id:'afectado-1',role:'AFECTADO_NNA',detalles:{datosDesconocidos:'true',fuente:'CLAVE_UNICA_USERINFO',primerNombre:'No se envía desde aquí'}},{id:'relacionado-1',role:'ADULTO_RELACIONADO',detalles:{relacion:'Testigo',alias:'Campo de otra tarjeta'}}];
    expect(adultCapture(violation(),cards,[]).personas).toEqual([{personaId:'afectado-1',detalles:{datosDesconocidos:'true'}},{personaId:'relacionado-1',detalles:{relacion:'Testigo'}}]);
  });
  it('el NNA requiere algún antecedente o datos desconocidos y la persona señalada admite alias',()=>{
    const affected:AdultPersonCard={id:'afectado-1',role:'AFECTADO_NNA',detalles:{}};const signaled:AdultPersonCard={id:'senalado-1',role:'PERSONA_SENALADA',detalles:{}};
    const views=new Map([['afectado-1',view('afectado-1')],['senalado-1',view('senalado-1')]]);const c={...violation(),'identifica-responsables':'true'};
    expect(validateCards(c,[affected,signaled],views)).toHaveProperty('afectado-1');expect(validateCards(c,[affected,signaled],views)).toHaveProperty('senalado-1');
    affected.detalles.datosDesconocidos='true';signaled.detalles.alias='Apodo';expect(validateCards(c,[affected,signaled],views)).toEqual({});
    expect(validateCards(c,[affected],new Map())).toHaveProperty('afectado-1');expect(validateCards(general(),[affected],new Map())).toEqual({});
  });
});
