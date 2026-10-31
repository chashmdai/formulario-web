import {render,fireEvent,cleanup} from '@testing-library/react';
import {afterEach,it,expect} from 'vitest';
import {useUnsavedChanges} from './useUnsavedChanges';
afterEach(cleanup);
function Harness({dirty=true,received=false}:{dirty?:boolean;received?:boolean}){const allow=useUnsavedChanges(dirty&&!received);return <button onClick={allow}>Confirmar salida</button>;}
function unload(){const event=new Event('beforeunload',{cancelable:true});window.dispatchEvent(event);return event;}
it('advierte cierre/recarga solo cuando hay información sin enviar',()=>{
 const view=render(<Harness dirty={false}/>);expect(unload().defaultPrevented).toBe(false);
 view.rerender(<Harness/>);expect(unload().defaultPrevented).toBe(true);
 view.rerender(<Harness received/>);expect(unload().defaultPrevented).toBe(false);
});
it('respeta una salida ya confirmada y elimina el listener al desmontar',()=>{
 const view=render(<Harness/>);fireEvent.click(view.getByRole('button',{name:'Confirmar salida'}));expect(unload().defaultPrevented).toBe(false);
 view.unmount();expect(unload().defaultPrevented).toBe(false);
});
