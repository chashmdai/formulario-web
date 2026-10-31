import {useState} from 'react';
import {render,screen,cleanup} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {it,expect,afterEach} from 'vitest';
import {EmailInput} from './EmailInput';
afterEach(cleanup);
function Harness(){const [value,setValue]=useState('');return <><label htmlFor="contact">Correo</label><EmailInput id="contact" value={value} onValueChange={setValue}/><button>Continuar</button><output>{value}</output></>;}
it('advierte un formato incompleto al salir, permite corregir y acepta x@x.x',async()=>{
 const user=userEvent.setup();render(<Harness/>);const input=screen.getByRole('textbox',{name:'Correo'});
 await user.type(input,'incompleto@');await user.tab();expect(screen.getByRole('alert').textContent).toContain('formato');
 await user.clear(input);await user.type(input,'x@x.x');await user.tab();expect(screen.queryByRole('alert')).toBeNull();
});
it('recorta bordes conservando mayúsculas y alias de la parte local',async()=>{
 const user=userEvent.setup();render(<Harness/>);const input=screen.getByRole('textbox',{name:'Correo'}) as HTMLInputElement;
 await user.click(input);await user.paste('  Usuario+Alias@sub.correo.algo  ');await user.tab();expect(input.value).toBe('Usuario+Alias@sub.correo.algo');expect(screen.queryByRole('alert')).toBeNull();
});
