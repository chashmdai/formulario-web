import {useState} from 'react';
import {cleanup,render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach,describe,it,expect} from 'vitest';
import {MobileInput} from './MobileInput';
function Harness(){const [value,setValue]=useState('');return <><label htmlFor="phone">Celular</label><MobileInput id="phone" value={value} onValueChange={setValue}/><output data-testid="canonical">{value}</output></>;}
afterEach(cleanup);
describe('Edición del celular con prefijo fijo',()=>{
 it('permite escribir ocho dígitos, seleccionar, corregir y borrar un opcional',async()=>{
  const user=userEvent.setup();render(<Harness/>);const input=screen.getByLabelText('Celular') as HTMLInputElement;
  expect(input.type).toBe('tel');expect(input.inputMode).toBe('numeric');expect(screen.getByText('+56 9')).toBeTruthy();await user.type(input,'12345678');expect(input.value).toBe('12345678');expect(screen.getByTestId('canonical').textContent).toBe('+56912345678');input.setSelectionRange(2,4);await user.keyboard('90');expect(input.value).toBe('12905678');await user.keyboard('{Backspace}');expect(input.value).toBe('1295678');await user.clear(input);expect(input.value).toBe('');expect(screen.getByTestId('canonical').textContent).toBe('');
 });
 it.each(['12345678','912345678','+56 9 1234 5678','56912345678','0056912345678'])('pega %s sin duplicar prefijos',async raw=>{
  const user=userEvent.setup();render(<Harness/>);await user.click(screen.getByLabelText('Celular'));await user.paste(raw);expect((screen.getByLabelText('Celular') as HTMLInputElement).value).toBe('12345678');expect(screen.getByTestId('canonical').textContent).toBe('+56912345678');
 });
 it('reemplaza un número completo pegado y conserva la edición parcial',async()=>{
  const user=userEvent.setup();render(<Harness/>);const input=screen.getByLabelText('Celular') as HTMLInputElement;await user.type(input,'87654321');await user.paste('+56912345678');expect(input.value).toBe('12345678');input.setSelectionRange(2,4);await user.paste('00');expect(input.value).toBe('12005678');
 });
});
