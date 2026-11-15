import {describe,expect,it} from 'vitest';
import {ApiError} from '../api';
import {parseSummary} from './summaryApi';
import {adultSummary,ID,nnaSummary,pending,registered} from './fixtures.test-utils';

const fields={motivo:'consulta',tema:'general','consulta-confirmada':'true','consulta-general-detalle':'Consulta sintética','adult-region':'13'};
const rejects=(value:unknown,id=ID)=>{try{parseSummary(value,id);}catch(error){return error instanceof ApiError&&error.status===502;}return false;};
describe('Validación del resumen recibido del servidor',()=>{
  it('acepta la proyección pública y conserva las evidencias tal como llegan',()=>{
    const parsed=parseSummary(adultSummary(registered,fields),ID);
    expect(parsed.state).toEqual(registered);expect(parsed.content?.campos).toEqual(fields);expect(parsed.content?.personas).toHaveLength(3);
    expect(parseSummary(nnaSummary(pending,{'nna-story':'Relato'}),ID).content?.tipo).toBe('NNA');
  });
  it('rechaza atributos ampliados accidentalmente o antecedentes registrales',()=>{
    const extraTop={...adultSummary(pending,fields),body:'documento completo'};expect(rejects(extraTop)).toBe(true);
    const withAttributes=adultSummary(pending,fields);(withAttributes.contenido.personas[0] as unknown as Record<string,unknown>).atributos={nombres:{valor:'NOMBRE REGISTRAL',fuente:'SRCEI'}};expect(rejects(withAttributes)).toBe(true);
    const discrepancies=adultSummary(pending,fields);(discrepancies.contenido as Record<string,unknown>).discrepancias={};expect(rejects(discrepancies)).toBe(true);
    const undeclared=adultSummary(pending,fields);undeclared.contenido.personas[0].valoresDeclarados={fechaNacimiento:'1980-01-01'};undeclared.contenido.personas[0].camposFaltantes=[];expect(rejects(undeclared)).toBe(true);
    const cuOnDeclared=adultSummary(pending,fields,{identidadAutenticada:{run:'12345678-5',nombres:'X',apellidos:'Y'}});expect(rejects(cuOnDeclared)).toBe(true);
  });
  it('rechaza referencias, estados o folios incoherentes y contenido junto a un recibo retirado',()=>{
    expect(rejects(adultSummary(pending,fields),'99999999-9999-4999-8999-999999999999')).toBe(true);
    expect(rejects({...adultSummary(pending,fields),registro:registered.registro})).toBe(true);
    expect(rejects({...adultSummary(registered,fields),archivosRetirados:[{id:'x',tamanio:1,sha256:'c'.repeat(64)}]})).toBe(true);
    expect(rejects({id:ID,estado:'PENDIENTE_REGISTRO_SIGER',custodia:pending.custodia,registro:null,contenido:null,archivosRetirados:[{id:'x',tamanio:1,sha256:'c'.repeat(64)}]})).toBe(true);
    expect(rejects({id:ID,estado:'PENDIENTE_REGISTRO_SIGER',custodia:pending.custodia,registro:null,contenido:null,archivosRetirados:[]})).toBe(true);
    expect(rejects({id:ID,estado:'PENDIENTE_REGISTRO_SIGER',custodia:pending.custodia,registro:null})).toBe(true);
    const badHash=adultSummary(pending,fields);badHash.contenido.adjuntos[0].sha256='no-hex';expect(rejects(badHash)).toBe(true);
  });
});
