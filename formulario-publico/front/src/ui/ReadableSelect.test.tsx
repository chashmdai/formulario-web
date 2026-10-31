import {render,screen,cleanup} from '@testing-library/react';
import {afterEach,it,expect} from 'vitest';
import {ReadableSelect} from './ReadableSelect';
afterEach(cleanup);
it('conserva el control nativo y muestra una selección larga completa',()=>{
 const label='Magallanes y de la Antártica Chilena';
 const view=render(<><label htmlFor="region-readable">Región</label><ReadableSelect id="region-readable" value="12" onChange={()=>{}}><option value="">Selecciona una región</option><option value="12">{label}</option></ReadableSelect></>);
 expect(screen.getByRole('combobox',{name:'Región'}).tagName).toBe('SELECT');expect(view.container.querySelector('.readable-selection')?.textContent).toBe(label);
});
it('no duplica selecciones cortas o el placeholder',()=>{
 const view=render(<ReadableSelect value="" onChange={()=>{}}><option value="">Selecciona una región de residencia</option><option value="13">Santiago</option></ReadableSelect>);
 expect(view.container.querySelector('.readable-selection')).toBeNull();
 view.rerender(<ReadableSelect value="13" onChange={()=>{}}><option value="13">Santiago</option></ReadableSelect>);expect(view.container.querySelector('.readable-selection')).toBeNull();
});
