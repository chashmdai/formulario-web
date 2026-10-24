import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api';
import { getPerson, identifyPerson } from './api';
import { AdultPersonCard } from './AdultPersonCard';
import { adultCapture } from './adultRules';
import type { AdultPersonCard as Card } from './adultRules';
vi.mock('./api',()=>({getPerson:vi.fn(),identifyPerson:vi.fn(),resetPerson:vi.fn(),declarePerson:vi.fn()}));
beforeEach(()=>{vi.resetAllMocks();vi.mocked(getPerson).mockRejectedValue(new ApiError(404));vi.mocked(identifyPerson).mockImplementation(async(_journey,person)=>({personaId:person,estado:'REVISADO',autenticada:false,runInformado:false,camposFaltantes:['primerNombre','segundoNombre','primerApellido','segundoApellido','edad'],camposObligatorios:[],valoresDeclarados:{},mensaje:''}));});
function Harness({initial,onRemove=()=>{}}:{initial:Card;onRemove?:()=>void}) {
  const [card,setCard]=useState(initial);return <><AdultPersonCard card={card} journey="j-sintetico" onRemove={onRemove} onKind={kind=>setCard(old=>({...old,kind}))} onChange={detalles=>setCard(old=>({...old,detalles}))} /><output aria-label="Captura controlada">{JSON.stringify(adultCapture({motivo:'vulneracion','identifica-responsables':'true'},[card],[]))}</output></>;
}
describe('Tarjetas adultas del mockup',()=>{
  it('NNA admite datos desconocidos y comuna dependiente sin nombres obligatorios',async()=>{
    render(<Harness initial={{id:'afectado-1',role:'AFECTADO_NNA',detalles:{}}} />);expect((await screen.findByRole('textbox',{name:/Primer nombre/}) as HTMLInputElement).required).toBe(false);
    await userEvent.selectOptions(screen.getByRole('combobox',{name:/Región/}),'13');await userEvent.selectOptions(screen.getByRole('combobox',{name:/Comuna/}),'Santiago');
    await userEvent.selectOptions(screen.getByRole('combobox',{name:/Región/}),'15');expect((screen.getByRole('combobox',{name:/Comuna/}) as HTMLSelectElement).value).toBe('');
    await userEvent.click(screen.getByRole('checkbox',{name:/No conozco otros datos/}));expect(screen.getByLabelText('Captura controlada').textContent).toContain('"datosDesconocidos":"true"');
  });
  it('una institución no se envía como persona ni conserva alias inactivo',async()=>{
    render(<Harness initial={{id:'senalado-1',role:'PERSONA_SENALADA',detalles:{alias:'Apodo anterior'}}} />);await screen.findByRole('textbox',{name:/Primer nombre/});
    await userEvent.selectOptions(screen.getByRole('combobox',{name:/¿A quién puedes identificar/}),'institucion');expect(screen.queryByRole('textbox',{name:/Primer nombre/})).toBeNull();
    await userEvent.type(screen.getByRole('textbox',{name:/Nombre de la institución/}),'Entidad sintética');await userEvent.selectOptions(screen.getByRole('combobox',{name:/Tipo de institución/}),'Organismo público');
    const captured=JSON.parse(screen.getByLabelText('Captura controlada').textContent!);expect(captured.personas).toEqual([]);expect(captured.instituciones[0].nombre).toBe('Entidad sintética');expect(captured.instituciones[0]).not.toHaveProperty('alias');
  });
  it('adulto relacionado mantiene parentesco propio y emite retiro al contenedor',async()=>{
    const remove=vi.fn();render(<Harness onRemove={remove} initial={{id:'relacionado-1',role:'ADULTO_RELACIONADO',detalles:{}}} />);await screen.findByRole('textbox',{name:/Primer nombre/});
    await userEvent.selectOptions(screen.getByRole('combobox',{name:'Relación con la comunicación'}),'Testigo');expect(screen.getByLabelText('Captura controlada').textContent).toContain('"relacion":"Testigo"');
    expect(screen.queryByRole('spinbutton')).toBeNull();await userEvent.click(screen.getByRole('button',{name:'Quitar'}));expect(remove).toHaveBeenCalledTimes(1);
  });
});
