import {describe,it,expect} from 'vitest';
import cases from '../../../../contratos/fixtures/contactos-formulario-v1.json';
import {canonicalMobile,cleanEmail,validEmail} from './contacts';
import {adultValues,validateAdult} from './adultRules';
import {nnaValues,validateNna} from './nnaRules';
describe('Normalización compartida de contactos',()=>{
 it.each(cases.movilesValidos)('normaliza móvil %s', (raw,want)=>expect(canonicalMobile(raw)).toBe(want));
 it.each(cases.movilesInvalidos)('rechaza móvil %s',raw=>expect(canonicalMobile(raw)).toBeNull());
 it.each(cases.correosValidos)('acepta correo %s sin cambiar su parte local',raw=>{expect(validEmail(raw)).toBe(true);expect(cleanEmail(raw)).toBe(raw.trim());});
 it.each(cases.correosInvalidos)('rechaza correo incompleto o inválido %s',raw=>expect(validEmail(raw)).toBe(false));
 it('captura adulta normaliza y mantiene opcionales vacíos',()=>{
  const fields={motivo:'consulta',tema:'general','consulta-confirmada':'true','consulta-general-detalle':'Orientación','adult-region':'13',email:'  Usuario+Alias@sub.correo.algo  ',telefono:'+56 9 1234 5678'};
  expect(validateAdult(fields)).toEqual({});expect(adultValues(fields)).toMatchObject({email:'Usuario+Alias@sub.correo.algo',telefono:'+56912345678'});expect(adultValues({...fields,email:'',telefono:''})).not.toHaveProperty('telefono');expect(fields.telefono).toBe('+56 9 1234 5678');
 });
 it('NNA normaliza solo canales activos, exige el elegido y conserva contacto inseguro',()=>{
  const fields={'nna-region':'13','nna-primary-method':'celular','nna-primary-celular':'+56 9 1234 5678','nna-extra-contact':'true','nna-secondary-method':'email','nna-secondary-email':'  Alias+Uno@sub.correo.algo  '};
  expect(validateNna(fields,false)).toEqual({});expect(nnaValues(fields)).toMatchObject({'nna-primary-celular':'+56912345678','nna-secondary-email':'Alias+Uno@sub.correo.algo'});expect(validateNna({...fields,'nna-primary-celular':''},false)).toHaveProperty('nna-primary-celular');expect(nnaValues({...fields,'nna-primary-method':'unsafe','nna-extra-contact':'false'})).not.toHaveProperty('nna-primary-celular');
 });
});
