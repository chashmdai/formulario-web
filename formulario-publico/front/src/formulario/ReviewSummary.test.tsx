import {render,screen,within,cleanup} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {ReviewAdult} from './ReviewAdult';
import type {AdultReview} from './ReviewAdult';
import {ReviewNna} from './ReviewNna';
import type {IdentityView} from './api';
const identity:IdentityView={personaId:'presentador-adulto',estado:'REVISADO',autenticada:false,runInformado:false,camposFaltantes:[],camposObligatorios:[],valoresDeclarados:{primerNombre:'Persona sintética',primerApellido:'Ejemplo',nacionalidad:'Perú',tipoDocumento:'PASAPORTE',numeroDocumento:'DOC-SINTETICO'},mensaje:'Declaración'};
beforeEach(()=>{HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');};});
afterEach(cleanup);
describe('Resumen organizado sin modificar la captura',()=>{
 it('separa identificación, contacto, ubicación, comunicación, relato y adjuntos y preserva el texto íntegro',()=>{
  const story='Primera línea de un relato sintético.\n\n'+('Texto extenso con contexto. '.repeat(35))+'<script>texto literal</script>';
  const review:AdultReview={presenter:identity,people:new Map(),files:[{id:'archivo',nombre:'respaldo-sintetico.pdf',tamanio:1024,sha256:'a'.repeat(64),estado:'VALIDADO'}],capture:{campos:{motivo:'vulneracion',email:'sintetico@example.test','hechos-region':'13','hechos-commune':'Santiago',relato:story,'adult-preferred':'Nombre preferido sintético','grupo-nna-1-nombre':'Grupo sintético','grupo-nna-1-descripcion':'Antecedentes colectivos','campo-adicional':'Valor que no se debe perder'},personas:[],instituciones:[{nombre:'Institución sintética',tipo:'Educación',rol:'Antecedente sintético',region:'13',comuna:'Santiago'}],adjuntos:['archivo']}};
  const original=JSON.stringify(review.capture);
  render(<ReviewAdult review={review} session={null} busy={false} locked={false} error="" onBack={vi.fn()} onSend={vi.fn()} onCheck={vi.fn()}/>);
  for(const title of ['Identificación','Contacto','Ubicación','Comunicación','Relato','Archivos adjuntos'])expect(screen.getByRole('region',{name:title})).toBeTruthy();
  expect(within(screen.getByRole('region',{name:'Identificación'})).getByText('Nombre preferido sintético')).toBeTruthy();
  expect(within(screen.getByRole('region',{name:'Contacto'})).getByText('sintetico@example.test')).toBeTruthy();
  expect(within(screen.getByRole('region',{name:'Comunicación'})).getByText('Valor que no se debe perder')).toBeTruthy();
  const text=screen.getByRole('region',{name:'Relato'}).querySelector('dd')!;
  expect(text.textContent).toBe(story);expect(text.closest('.summary-row-long')).toBeTruthy();expect(document.querySelector('script')).toBeNull();
  expect(screen.getByText('Grupo sintético')).toBeTruthy();expect(screen.getByText('Institución sintética')).toBeTruthy();expect(screen.getByText(/respaldo-sintetico.pdf/)).toBeTruthy();
  expect(JSON.stringify(review.capture)).toBe(original);
 });
 it('conserva contacto seguro NNA, ubicación opcional, preferencias y relato en secciones independientes',()=>{
  const campos={'nna-region':'13','nna-preferred':'Preferido sintético','nna-treatment':'elle','nna-primary-method':'unsafe','nna-contact-instructions':'No llamar','nna-story':'Relato sintético\nSegunda línea.'};
  render(<ReviewNna review={{campos,identity:{...identity,personaId:'presentador-nna',valoresDeclarados:{primerNombre:'NNA sintético'}},attachments:[]}} busy={false} locked={false} error="" onBack={vi.fn()} onSend={vi.fn()} onCheck={vi.fn()}/>);
  expect(within(screen.getByRole('region',{name:'Identificación'})).getByText('Preferido sintético')).toBeTruthy();
  expect(within(screen.getByRole('region',{name:'Contacto'})).getByText('No tengo una forma segura de contacto')).toBeTruthy();
  expect(screen.getByRole('region',{name:'Relato'}).querySelector('dd')?.textContent).toBe(campos['nna-story']);
  expect(within(screen.getByRole('region',{name:'Archivos adjuntos'})).getByText('Sin archivos adjuntos')).toBeTruthy();
 });
});
