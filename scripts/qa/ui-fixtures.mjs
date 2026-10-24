import assert from 'node:assert/strict';
const empty=(id,role,cu=false)=>({personaId:id,estado:'REVISADO',autenticada:cu,runInformado:cu,camposFaltantes:cu?['fechaNacimiento','sexo']:role==='PRESENTADOR'?['primerNombre','segundoNombre','primerApellido','segundoApellido',...(id==='presentador-nna'?['edad']:['fechaNacimiento','sexo'])]:['primerNombre','segundoNombre','primerApellido','segundoApellido',...(role==='AFECTADO_NNA'?['edad']:[])],camposObligatorios:role==='PRESENTADOR'&&!cu?['primerNombre','primerApellido']:[],valoresDeclarados:{},mensaje:'Completa los datos solicitados. La revisión del RUN no acredita tu identidad.'});
export async function fixture(context,kind,{closed=false}={}){
 const people=new Map(),calls=[],flags={rejectFiles:false,failSend:false,registered:false,lookupDelay:0,lookup:'empty'};
 let journey=kind?{id:'11111111-1111-4111-8111-111111111111',tipo:kind,avisoVersion:kind==='NNA'?'NNA_1':'ADULTO_1',aceptaCanal:kind!=='NNA',estado:'BORRADOR',venceEn:'2026-10-25T00:00:00Z'}:null,receipt=null,files=[];
 const proof={algoritmo:'Ed25519',keyId:'QA_VISUAL',payload:'controlado',firma:'controlada'};
 await context.route('**/api/**',async route=>{
  const r=route.request(),url=new URL(r.url()),p=url.pathname,m=r.method();calls.push({p,m});let data,status=200;
  try{
   const body=m==='POST'&&!p.endsWith('/adjuntos')?r.postDataJSON():null;
   if(p==='/api/session')data={authenticated:kind==='ADULTO_CU',identity:kind==='ADULTO_CU'?{run:'12345678-5',nombres:'NOMBRE DE PRUEBA',apellidos:'APELLIDO DE PRUEBA',nombresComponentes:['NOMBRE','DE PRUEBA'],apellidosComponentes:['APELLIDO','DE PRUEBA']}:null,environment:'SANDBOX',loginAvailable:true};
   else if(p==='/api/csrf')data={token:'QA_CONTROLADO',headerName:'X-CSRF-TOKEN',parameterName:'_csrf'};
   else if(p==='/api/formulario/capacidades')data={disponible:true,avisoAdulto:'ADULTO_1',avisoNna:'NNA_1'};
   else if(p.endsWith('/recepcion/capacidades'))data={adultos:!closed,nna:!closed};
   else if(p.endsWith('/actual')){data=journey||{};if(!journey)status=404;}
   else if(p==='/api/formulario/recorridos'&&m==='POST')data=journey={id:'11111111-1111-4111-8111-111111111111',tipo:body.tipo,avisoVersion:body.avisoVersion,aceptaCanal:body.aceptaCanalDigital,estado:'BORRADOR',venceEn:'2026-10-25T00:00:00Z'};
   else if(p.endsWith('/adjuntos')){
    if(m==='POST'&&flags.rejectFiles){status=400;data={};}else{if(m==='POST')files=[{id:'22222222-2222-4222-8222-222222222222',nombre:'antecedentes.txt',tamanio:2456,sha256:'a'.repeat(64),tipo:'text/plain',estado:'VALIDADO'}];if(m==='DELETE')files=[];data=files;}
   }else if(p.endsWith('/comunicaciones')&&m==='POST'){
    if(flags.failSend){status=503;data={};flags.failSend=false;}else{receipt={id:r.headers()['idempotency-key'],estado:'PENDIENTE_REGISTRO_SIGER',custodia:proof,registro:null};journey.estado='RECIBIDO';data=receipt;}
   }else if(p.endsWith('/comunicacion')||p.includes('/comunicaciones/')){
    data=receipt||{};if(!receipt)status=404;if(receipt&&flags.registered)data={...receipt,estado:'REGISTRADA_SIGER',registro:{registro:{folio:'COM-2026-000123'},comprobante:proof}};
   }else{
    const person=/\/personas\/([^/]+)(?:\/(identificacion|declaracion))?$/.exec(p);assert(person,'API ajena al doble');const id=person[1];
    if(m==='GET'){data=people.get(id)||{};if(!people.has(id))status=404;}
    else if(m==='DELETE'){people.delete(id);status=204;}
    else if(person[2]==='identificacion'){
     if(flags.lookupDelay)await new Promise(resolve=>setTimeout(resolve,flags.lookupDelay));
     data=empty(id,body.rol,kind==='ADULTO_CU'&&body.rol==='PRESENTADOR');data.runInformado=data.autenticada||!!body.runNumero;
     if(body.runNumero&&flags.lookup==='complete'){data.camposFaltantes=[];data.camposObligatorios=[];}
     if(body.runNumero&&flags.lookup==='partial'){data.camposFaltantes=['primerApellido'];data.camposObligatorios=['primerApellido'];}
     people.set(id,data);
    }else{data={...people.get(id),valoresDeclarados:body.atributos};people.set(id,data);}
   }
   await route.fulfill({status,...(status===204?{}:{contentType:'application/json',body:JSON.stringify(data)})});
  }catch{await route.abort();throw new Error('Ruta QA sin doble controlado: '+p);}
 });
 await context.route('**/oauth2/**',route=>route.fulfill({status:503,body:'Acceso controlado en auditoría visual'}));
 return {flags,calls};
}
