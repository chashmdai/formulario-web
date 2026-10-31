import {useState} from 'react';
import {cleanup,render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach,describe,expect,it,vi} from 'vitest';
import {NationalitySelect} from './NationalitySelect';
import {canonicalNationality,nationalityPrefix,otherNationality,validNationality} from '../formulario/nationality';
function mount(value='',disabled=false){
 const change=vi.fn();
 function Form(){const [current,setCurrent]=useState(value);return <><label htmlFor="nationality">Nacionalidad</label><NationalitySelect id="nationality" value={current} required disabled={disabled} onValueChange={v=>{change(v);setCurrent(v);}}/><output data-testid="stored">{current}</output></>;}
 render(<Form/>);return change;
}
afterEach(cleanup);
describe('Nacionalidad seleccionada de un catálogo compartido',()=>{
 it('ofrece 249 países y una excepción; buscar no guarda texto libre',async()=>{
  const change=mount();expect(screen.getAllByRole('option')).toHaveLength(251);
  await userEvent.type(screen.getByRole('searchbox',{name:'Buscar país'}),'peru');
  expect(screen.getByRole('option',{name:'Perú'})).toBeTruthy();expect(change).not.toHaveBeenCalled();expect(screen.getByTestId('stored').textContent).toBe('');
  await userEvent.selectOptions(screen.getByRole('combobox',{name:'Nacionalidad'}),'Perú');
  expect(change).toHaveBeenLastCalledWith('Perú');expect(screen.getByRole('searchbox').getAttribute('value')).toBe('');
 });
 it('una búsqueda sin resultados no inventa ni reemplaza el país ya elegido',async()=>{
  const change=mount('Perú');await userEvent.type(screen.getByRole('searchbox'),'no-existe-123');
  expect(screen.getByText(/No encontramos coincidencias/).textContent).toContain('No encontramos coincidencias');
  expect((screen.getByRole('combobox') as HTMLSelectElement).value).toBe('Perú');expect(change).not.toHaveBeenCalled();
 });
 it('solo habilita el detalle al elegir la opción excepcional y lo elimina al elegir un país',async()=>{
  const change=mount();expect(screen.queryByRole('textbox')).toBeNull();
  await userEvent.selectOptions(screen.getByRole('combobox'),otherNationality);
  expect(validNationality(screen.getByTestId('stored').textContent||'')).toBe(false);
  const input=screen.getByRole('textbox',{name:/Indica tu nacionalidad/});expect((input as HTMLInputElement).required).toBe(true);expect((input as HTMLInputElement).maxLength).toBe(120);
  await userEvent.type(input,'Apátrida');expect(change).toHaveBeenLastCalledWith(nationalityPrefix+'Apátrida');expect(validNationality(nationalityPrefix+'Apátrida')).toBe(true);
  await userEvent.selectOptions(screen.getByRole('combobox'),'Canadá');expect(screen.queryByRole('textbox')).toBeNull();expect(screen.getByTestId('stored').textContent).toBe('Canadá');
 });
 it('admite borrar y corregir la selección sin autocompletar una nacionalidad',async()=>{
  const change=mount('Perú');await userEvent.selectOptions(screen.getByRole('combobox'),'');expect(change).toHaveBeenLastCalledWith('');
  await userEvent.type(screen.getByRole('searchbox'),'estados uni');await userEvent.selectOptions(screen.getByRole('combobox'),'Estados Unidos');expect(change).toHaveBeenLastCalledWith('Estados Unidos');
 });
 it('restaura antecedentes anteriores como excepción sin sobrescribirlos automáticamente',()=>{
  const change=mount('Venezolana');expect((screen.getByRole('combobox') as HTMLSelectElement).value).toBe(otherNationality);
  expect((screen.getByRole('textbox') as HTMLInputElement).value).toBe('Venezolana');expect(change).not.toHaveBeenCalled();
  expect(canonicalNationality('Venezolana')).toBe(nationalityPrefix+'Venezolana');
  expect(canonicalNationality('Perú')).toBe('Perú');
 });
 it('mantiene búsqueda y selección deshabilitadas durante operaciones en curso',()=>{
  mount('',true);expect((screen.getByRole('searchbox') as HTMLInputElement).disabled).toBe(true);expect((screen.getByRole('combobox') as HTMLSelectElement).disabled).toBe(true);
 });
 it('rechaza valores libres, excepciones vacías, demasiado largas o con controles',()=>{
  for(const value of ['SINTÉTICA','PE',nationalityPrefix,nationalityPrefix+' ',nationalityPrefix+'x'.repeat(121),nationalityPrefix+'Apátrida\n'])expect(validNationality(value)).toBe(false);
 });
});
