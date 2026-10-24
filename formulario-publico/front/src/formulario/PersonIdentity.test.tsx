import { createRef } from 'react';
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api';
import { declarePerson, getPerson, identifyPerson, resetPerson } from './api';
import type { IdentityView } from './api';
import { PersonIdentity } from './PersonIdentity';
import type { PersonIdentityHandle, RelatedRole } from './PersonIdentity';
vi.mock('./api',()=>({declarePerson:vi.fn(),getPerson:vi.fn(),identifyPerson:vi.fn(),resetPerson:vi.fn()}));
const view=():IdentityView=>({personaId:'afectado-1',estado:'REVISADO',autenticada:false,runInformado:false,camposFaltantes:['primerNombre','segundoNombre','primerApellido','segundoApellido','edad'],camposObligatorios:[],valoresDeclarados:{},mensaje:'Completa solo los datos que conozcas.'});
beforeEach(()=>{
  vi.resetAllMocks();vi.mocked(getPerson).mockRejectedValue(new ApiError(404));vi.mocked(identifyPerson).mockResolvedValue(view());vi.mocked(resetPerson).mockResolvedValue(undefined);
  vi.mocked(declarePerson).mockImplementation(async(_j,_p,values)=>({...view(),valoresDeclarados:values}));
});
function mount(role:RelatedRole='AFECTADO_NNA',person='afectado-1') {
  const identityRef=createRef<PersonIdentityHandle>();
  render(<><PersonIdentity journey="j-sintetico" person={person} role={role} identityRef={identityRef} /><button type="button" onClick={()=>{void identityRef.current?.save();}}>Guardar persona</button></>);
  return identityRef;
}
describe('Identificación persistente de terceros',()=>{
  it('sin RUN permite datos desconocidos sin exigir nombres ni inventar autenticación',async()=>{
    mount();const name=await screen.findByRole('textbox',{name:/^Primer nombre/});expect((name as HTMLInputElement).required).toBe(false);
    expect(identifyPerson).toHaveBeenCalledWith('j-sintetico','afectado-1','AFECTADO_NNA');
    await userEvent.click(screen.getByRole('button',{name:'Guardar persona'}));await waitFor(()=>expect(declarePerson).toHaveBeenCalledWith('j-sintetico','afectado-1',{}));
  });
  it('respuesta completa no revela valores recuperados ni exige reingresarlos',async()=>{
    vi.mocked(identifyPerson).mockResolvedValue({...view(),camposFaltantes:[]});mount();await waitFor(()=>expect((screen.getByRole('button',{name:'Revisar RUN'}) as HTMLButtonElement).disabled).toBe(false));
    expect(screen.queryByRole('textbox',{name:/Primer nombre/})).toBeNull();expect(screen.queryByRole('spinbutton')).toBeNull();await userEvent.click(screen.getByRole('button',{name:'Guardar persona'}));await waitFor(()=>expect(declarePerson).toHaveBeenCalledWith('j-sintetico','afectado-1',{}));
  });
  it('respuesta parcial pide solo lo faltante y conserva declaraciones propias',async()=>{
    vi.mocked(getPerson).mockResolvedValue({...view(),runInformado:true,camposFaltantes:['primerApellido'],valoresDeclarados:{primerApellido:'DECLARADO'}});mount();
    const surname=await screen.findByDisplayValue('DECLARADO');expect(screen.queryByRole('textbox',{name:/Primer nombre/})).toBeNull();expect(screen.queryByRole('textbox',{name:/^RUN/})).toBeNull();expect(identifyPerson).not.toHaveBeenCalled();
    await userEvent.clear(surname);await userEvent.type(surname,'CORREGIDO');await userEvent.click(screen.getByRole('button',{name:'Guardar persona'}));await waitFor(()=>expect(declarePerson).toHaveBeenCalledWith('j-sintetico','afectado-1',{primerApellido:'CORREGIDO'}));
  });
  it('separa número y DV al pegar, valida módulo 11 y reinicia antes de cambiar RUN',async()=>{
    mount();await screen.findByRole('textbox',{name:/Primer nombre/});const input=screen.getByRole('textbox',{name:/^RUN/});
    fireEvent.paste(input,{clipboardData:{getData:()=> '12.345.678-0'}});await userEvent.click(screen.getByRole('button',{name:'Revisar RUN'}));expect(screen.getByText('Revisa el número y dígito verificador del RUN.')).toBeTruthy();expect(resetPerson).not.toHaveBeenCalled();
    fireEvent.paste(input,{clipboardData:{getData:()=> '12.345.678-5'}});await userEvent.click(screen.getByRole('button',{name:'Revisar RUN'}));
    await waitFor(()=>expect(identifyPerson).toHaveBeenLastCalledWith('j-sintetico','afectado-1','AFECTADO_NNA','12345678','5'));expect(resetPerson).toHaveBeenCalledWith('j-sintetico','afectado-1');
  });
  it('edad opcional se valida sin aceptar valores fuera de NNA',async()=>{
    mount();const age=await screen.findByRole('spinbutton',{name:/Edad aproximada/});await userEvent.type(age,'18');await userEvent.click(screen.getByRole('button',{name:'Guardar persona'}));expect(declarePerson).not.toHaveBeenCalled();expect(screen.getByText(/Indica una edad entre 0 y 17/)).toBeTruthy();
    await userEvent.clear(age);await userEvent.type(age,'12');await userEvent.click(screen.getByRole('button',{name:'Guardar persona'}));await waitFor(()=>expect(declarePerson).toHaveBeenCalledWith('j-sintetico','afectado-1',{edad:'12'}));
  });
  it('adultos relacionados y señalados no solicitan edad, nacimiento ni sexo ajenos al mockup',async()=>{
    for(const [role,id] of [['ADULTO_RELACIONADO','relacionado-1'],['PERSONA_SENALADA','senalado-1']] as const){
      vi.mocked(identifyPerson).mockResolvedValue({...view(),personaId:id,camposFaltantes:['primerNombre','primerApellido','fechaNacimiento','sexo']});mount(role,id);await screen.findByRole('textbox',{name:/Primer nombre/});
      expect(screen.queryByRole('spinbutton')).toBeNull();expect(screen.queryByLabelText(/Fecha de nacimiento/)).toBeNull();expect(screen.queryByLabelText(/Sexo/)).toBeNull();await userEvent.click(screen.getByRole('button',{name:'Guardar persona'}));await waitFor(()=>expect(declarePerson).toHaveBeenCalledWith('j-sintetico',id,{}));cleanup();
    }
  });
  it('rechaza atribuir autenticación CU a una persona relacionada',async()=>{
    vi.mocked(getPerson).mockResolvedValue({...view(),autenticada:true});mount();expect(await screen.findByText(/No pudimos revisar o guardar/)).toBeTruthy();expect(screen.queryByRole('textbox',{name:/Primer nombre/})).toBeNull();expect(declarePerson).not.toHaveBeenCalled();
  });
  it('un fallo de revisión muestra error y conserva la tarjeta para reintentar',async()=>{
    vi.mocked(getPerson).mockRejectedValueOnce(new ApiError(503));mount();await screen.findByText(/No pudimos revisar o guardar/);
    await userEvent.click(screen.getByRole('button',{name:'Volver a intentar'}));await screen.findByRole('textbox',{name:/Primer nombre/});expect(identifyPerson).toHaveBeenCalledWith('j-sintetico','afectado-1','AFECTADO_NNA');
  });
  it('un fallo al guardar conserva la declaración escrita y permite reintentarla',async()=>{
    vi.mocked(declarePerson).mockRejectedValueOnce(new ApiError(503));mount();const name=await screen.findByRole('textbox',{name:/Primer nombre/});
    await userEvent.type(name,'DECLARADO');await userEvent.click(screen.getByRole('button',{name:'Guardar persona'}));await screen.findByText(/No pudimos revisar o guardar/);expect((name as HTMLInputElement).value).toBe('DECLARADO');
    await userEvent.click(screen.getByRole('button',{name:'Volver a intentar'}));await waitFor(()=>expect(declarePerson).toHaveBeenCalledTimes(2));expect(vi.mocked(declarePerson).mock.calls[1]).toEqual(['j-sintetico','afectado-1',{primerNombre:'DECLARADO'}]);
  });
});
