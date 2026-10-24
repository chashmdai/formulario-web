import { useEffect, useImperativeHandle, useRef, useState } from 'react';
import type { ChangeEvent, ReactNode, Ref } from 'react';
import type { Session } from '../api';
import { ApiError } from '../api';
import { declarePerson, getPerson, identifyPerson, resetPerson } from './api';
import type { Declarations, IdentityField, IdentityView, Journey } from './api';
import { formatRunNumber, pastedRun, runNumber, validRun } from './run';

export interface IdentificationHandle { save: () => Promise<IdentityView | null> }
interface Props {
  journey: Journey; session: Session | null; nna?: boolean; children?: ReactNode;
  identityRef?: Ref<IdentificationHandle>; onResolved?: (view: IdentityView | null) => void;
}
const metadata: {key: IdentityField; suffix: string; label: string; type?: string; help?: string}[] = [
  {key: 'primerNombre', suffix: 'names', label: 'Primer nombre'},
  {key: 'segundoNombre', suffix: 'middle', label: 'Segundo nombre', help: 'Déjalo en blanco si no tienes un segundo nombre.'},
  {key: 'primerApellido', suffix: 'first', label: 'Primer apellido'},
  {key: 'segundoApellido', suffix: 'second', label: 'Segundo apellido'},
  {key: 'fechaNacimiento', suffix: 'birth', label: 'Fecha de nacimiento', type: 'date', help: 'Puedes completar este dato si lo conoces (opcional).'},
  {key: 'sexo', suffix: 'sex', label: 'Sexo registral', help: 'No equivale a identidad de género ni a la forma de trato.'},
  {key: 'edad', suffix: 'age', label: 'Edad', type: 'number', help: 'Déjalo en blanco si no la sabes.'},
];

/** Estructura del mockup; solo muestra declaraciones propias o identidad CU de sesión. */
export function IdentificationControls({journey, session, nna = false, children, identityRef, onResolved}: Props) {
  const prefix = nna ? 'nna' : 'adult';
  const person = nna ? 'presentador-nna' : 'presentador-adulto';
  const cu = journey.tipo === 'ADULTO_CU';
  const identity = cu && session?.authenticated ? session.identity : null;
  const [numero, setNumero] = useState('');
  const [dv, setDv] = useState('');
  const [knowsRun, setKnowsRun] = useState(false);
  const [view, setView] = useState<IdentityView | null>(null);
  const [values, setValues] = useState<Declarations>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [phase, setPhase] = useState<'INICIAL' | 'CONSULTANDO' | 'REVISADO' | 'ERROR'>(cu || nna ? 'CONSULTANDO' : 'INICIAL');
  const [failure, setFailure] = useState('');
  const generation = useRef(0);
  const resetNeeded = useRef(false);
  const resetPending = useRef<Promise<void> | null>(null);
  const onResolvedRef = useRef(onResolved);
  useEffect(() => { onResolvedRef.current = onResolved; }, [onResolved]);
  useEffect(() => () => { generation.current++; }, []);
  const busy = phase === 'CONSULTANDO';

  function received(next: IdentityView) {
    if (cu && !next.autenticada) throw new ApiError(401);
    setView(next); setValues(next.valoresDeclarados); setErrors({}); setFailure('');
    setPhase(next.estado === 'CONSULTANDO' ? 'CONSULTANDO' : 'REVISADO');
    onResolvedRef.current?.(next);
  }
  function fail(error: unknown) {
    setPhase('ERROR');
    setFailure(error instanceof ApiError && error.status === 429 ? 'Espera unos minutos antes de volver a revisar el RUN.'
      : error instanceof ApiError && error.status === 401 ? 'La sesión expiró. Vuelve a iniciar sesión con ClaveÚnica.'
      : 'No pudimos guardar o revisar tus datos. Inténtalo nuevamente.');
  }
  async function ensureReset() {
    if (!resetNeeded.current) return;
    if (!resetPending.current) resetPending.current = resetPerson(journey.id, person);
    try { await resetPending.current; resetNeeded.current = false; }
    finally { resetPending.current = null; }
  }
  function invalidate() {
    if (view) resetNeeded.current = true;
    generation.current++; setView(null); setValues({}); setErrors({}); setFailure(''); setPhase('INICIAL');
    onResolvedRef.current?.(null);
  }
  async function review(withRun = !nna || knowsRun): Promise<IdentityView | null> {
    const token = ++generation.current;
    setFailure(''); setErrors({});
    if (!cu && withRun && !validRun(runNumber(numero), dv)) {
      setErrors({run: 'El número y el dígito verificador no coinciden.'});
      setPhase('INICIAL'); return null;
    }
    setPhase('CONSULTANDO');
    try {
      await ensureReset();
      const next = await identifyPerson(journey.id, person, 'PRESENTADOR', cu || !withRun ? undefined : runNumber(numero), cu || !withRun ? undefined : dv.toUpperCase());
      if (token !== generation.current) return null;
      received(next); return next;
    } catch (error) { if (token === generation.current) fail(error); return null; }
  }
  useEffect(() => {
    let active = true;
    const token = ++generation.current;
    // Restaurar la proyección primero: recargar una página con RUN no consulta otro identificador.
    void getPerson(journey.id, person).catch(error => {
      if (!(error instanceof ApiError) || error.status !== 404) throw error;
      return cu || nna ? identifyPerson(journey.id, person, 'PRESENTADOR') : null;
    }).then(next => {
      if (active && token === generation.current) {
        if (!next) return;
        if (cu && !next.autenticada) throw new ApiError(401);
        setView(next); setValues(next.valoresDeclarados); setPhase(next.estado === 'CONSULTANDO' ? 'CONSULTANDO' : 'REVISADO');
        if (nna && next.runInformado) setKnowsRun(true);
        onResolvedRef.current?.(next);
      }
    }).catch(error => { if (active && token === generation.current) fail(error); });
    return () => { active = false; generation.current++; };
  }, [journey.id, cu, nna, person]);
  async function refresh() {
    const token=++generation.current;setPhase('CONSULTANDO');
    try {const next=await getPerson(journey.id,person);if(token===generation.current)received(next);}
    catch(error){if(token===generation.current)fail(error);}
  }
  useEffect(() => {
    if (view?.estado !== 'CONSULTANDO' || phase === 'ERROR') return;
    let active=true;const token=generation.current;
    const timer=window.setTimeout(()=>{void getPerson(journey.id,person).then(next=>{
      if(active&&token===generation.current){setView(next);setValues(next.valoresDeclarados);setPhase(next.estado==='CONSULTANDO'?'CONSULTANDO':'REVISADO');onResolvedRef.current?.(next);}
    }).catch(error=>{if(active&&token===generation.current)fail(error);});},2000);
    return()=>{active=false;window.clearTimeout(timer);};
  },[view,phase,journey.id,person]);

  async function save(): Promise<IdentityView | null> {
    if (busy) return null;
    const current = view?.estado === 'REVISADO' ? view : await review();
    if (!current || current.estado !== 'REVISADO') return null;
    const input = view ? values : current.valoresDeclarados;
    const problems: Record<string, string> = {};
    for (const key of current.camposObligatorios) if (!(input[key] || '').trim()) problems[key] = `Completa ${metadata.find(m => m.key === key)?.label || 'este campo'}.`;
    for (const [key, value] of Object.entries(input)) {
      if (!value) continue;
      if (['primerNombre', 'segundoNombre', 'primerApellido', 'segundoApellido'].includes(key) && (!/^[\p{L}\p{M} .'-]+$/u.test(value) || !/\p{L}/u.test(value))) problems[key] = 'Escribe solo letras y separadores de nombres.';
      if (key === 'edad' && (!/^\d{1,2}$/.test(value) || Number(value) > 17)) problems[key] = 'Indica una edad entre 0 y 17 años, o deja el campo en blanco.';
    }
    setErrors(problems);
    if (Object.keys(problems).length) {
      const first = metadata.find(m => problems[m.key]);
      if (first) document.getElementById(`${prefix}-${first.suffix}`)?.focus();
      return null;
    }
    const token = ++generation.current;
    setPhase('CONSULTANDO');
    try {
      const allowed = Object.fromEntries(current.camposFaltantes.filter(k => input[k] !== undefined).map(k => [k, input[k]])) as Declarations;
      const next = await declarePerson(journey.id, person, allowed);
      if (token !== generation.current) return null;
      received(next); return next;
    } catch (error) { if (token === generation.current) fail(error); return null; }
  }
  useImperativeHandle(identityRef, () => ({save}));

  function changeRun(kind: 'numero' | 'dv', value: string) {
    if (busy || cu) return;
    invalidate();
    if (kind === 'numero') setNumero(formatRunNumber(value)); else setDv(value.replace(/[^0-9Kk]/g, '').slice(0, 1).toUpperCase());
  }
  function changeKnowsRun(next: boolean) {
    if (busy) return;
    invalidate(); setKnowsRun(next);
    if (!next) { setNumero(''); setDv(''); void review(false); }
  }
  const verifiedNames: Declarations = identity ? {
    primerNombre: identity.nombresComponentes?.[0], segundoNombre: identity.nombresComponentes?.slice(1).join(' '),
    primerApellido: identity.apellidosComponentes?.[0], segundoApellido: identity.apellidosComponentes?.slice(1).join(' '),
  } : {};
  const sourceFor = (key: IdentityField) => identity && verifiedNames[key] !== undefined ? 'ClaveÚnica' : 'Declarado';
  const fields = metadata.filter(m => (nna ? !['fechaNacimiento', 'sexo'].includes(m.key) : m.key !== 'edad')
    && ((identity && verifiedNames[m.key] !== undefined) || view?.camposFaltantes.includes(m.key)));
  const displayedNumber = identity ? formatRunNumber(identity.runNumero || identity.run.split('-')[0]) : numero;
  const displayedDv = identity ? identity.runDv || identity.run.split('-')[1] : dv;
  const preservedRun = !cu && !!view?.runInformado && !numero && !dv;
  const statusMarkup = <div className="manual-status" id={nna ? 'nna-lookup-status' : 'manual-status'} role="status" aria-live="polite" aria-atomic="true" hidden={!busy && !failure && (!view || (nna && !view.runInformado))}>
      {busy ? 'Estamos revisando tus datos…' : failure || view?.mensaje}
    </div>;
  const runControls = <div className="field">
    <label htmlFor={`${prefix}-run`}>RUN{nna ? ' (RUT)' : ''}{nna ? <span className="muted-label"> (opcional)</span> : <span className="identity-source" id="source-run">{cu ? 'ClaveÚnica' : 'Declarado'}</span>}</label>
    <div className="run-split">
      <div className="run-piece"><span className="run-piece-label" aria-hidden="true">Número</span>
        <input type="text" id={`${prefix}-run`} name={`${prefix}-run`} value={displayedNumber} onChange={e => changeRun('numero', e.target.value)} readOnly={cu} disabled={busy}
          onPaste={e=>{if(busy||cu)return;const parsed=pastedRun(e.clipboardData.getData('text'));if(parsed){e.preventDefault();invalidate();setNumero(formatRunNumber(parsed.numero));setDv(parsed.dv);}}}
          inputMode="numeric" maxLength={10} data-mask="run-body" data-run-body="true" placeholder="12.345.678" spellCheck={false}
          required={!cu && !nna} aria-invalid={!!errors.run} aria-describedby={`${prefix}-run-help ${prefix}-run-error`} />
      </div><span className="run-hyphen" aria-hidden="true">-</span>
      <div className="run-piece"><label className="run-piece-label" htmlFor={`${prefix}-run-dv`}>DV</label>
        <input type="text" id={`${prefix}-run-dv`} value={displayedDv} onChange={e => changeRun('dv', e.target.value)} readOnly={cu} disabled={busy}
          data-mask="run-dv" inputMode="text" maxLength={1} placeholder="K" autoComplete="off" autoCapitalize="characters" spellCheck={false} aria-label="Dígito verificador del RUN" aria-invalid={!!errors.run} />
      </div>
    </div>
    <p className="field-help" id={`${prefix}-run-help`}>{nna ? 'Es opcional. Escribe el número y el dígito verificador por separado, o continúa sin RUN.'
      : cu ? 'RUN de la identidad autenticada en tu sesión.' : 'Es obligatorio. Escribe el número y el dígito verificador en casillas separadas.'}</p>
    <p className="field-error" hidden={!errors.run} id={`${prefix}-run-error`}>{errors.run}</p>
  </div>;
  return <>
    {nna && <fieldset className="fieldset run-choice"><legend>¿Conoces tu RUN (RUT)?</legend><div className="radio-row">
      <label className="radio-pill"><input type="radio" name="nna-knows-run" checked={knowsRun} onChange={() => changeKnowsRun(true)} disabled={busy} value="yes" /> Sí, quiero ingresarlo</label>
      <label className="radio-pill"><input type="radio" name="nna-knows-run" checked={!knowsRun} onChange={() => changeKnowsRun(false)} disabled={busy} value="no" /> No sé mi RUN / continuar sin RUN</label>
    </div></fieldset>}
    {preservedRun && <div className="field-help"><p>Ya informaste un RUN en este recorrido. No es necesario volver a ingresarlo.</p><button type="button" className="btn btn-secondary" disabled={busy} onClick={invalidate}>Corregir el RUN informado</button></div>}
    <div className={nna ? undefined : 'identity-lookup-area'} id={nna ? 'nna-run-container' : 'adult-lookup-area'} hidden={(nna && !knowsRun) || preservedRun}>
      {runControls}
      {!cu && <div className="lookup-actions" id={nna ? undefined : 'manual-verify'}>
        <button className="btn btn-primary" id={nna ? 'nna-verify' : 'verify-manual'} type="button" disabled={busy} onClick={() => void review()}>{nna ? 'Revisar mi RUN' : 'Revisar RUN'}</button>
        {!nna && statusMarkup}
        {nna && <button className="btn btn-secondary" id="nna-skip-run" type="button" disabled={busy} onClick={() => changeKnowsRun(false)}>Continuar sin RUN</button>}
      </div>}
    </div>
    {(nna || cu || preservedRun) && statusMarkup}
    {phase === 'ERROR' && <button className="btn btn-secondary" type="button" onClick={() => void (view?.estado === 'CONSULTANDO' ? refresh() : review())}>Volver a intentar</button>}
    <div className={nna ? 'form-grid nna-basics' : 'identity-input-grid'} id={nna ? undefined : 'adult-personal-fields'} hidden={!fields.length && !children && !identity}>
      {fields.map(m => {
        const id = `${prefix}-${m.suffix}`, readOnly = !!identity && verifiedNames[m.key] !== undefined;
        const required = !readOnly && !!view?.camposObligatorios.includes(m.key);
        const input = {id, name: id, value: readOnly ? verifiedNames[m.key] || '' : values[m.key] || '', required, readOnly, disabled: busy,
          onChange: (e: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setValues(old => ({...old, [m.key]: e.target.value})),
          'aria-invalid': !!errors[m.key], 'aria-describedby': `${id}-help ${id}-error`};
        return <div className="field" data-identity-attr={m.suffix} key={m.key}>
          <label htmlFor={id}>{m.label}{(['segundoApellido', 'edad'].includes(m.key) || (nna && m.key === 'segundoNombre')) && <span className="muted-label"> {m.key === 'edad' ? '(opcional)' : '(si corresponde)'}</span>}
            {required && <span aria-hidden="true" className="required-mark"> *</span>}{!nna && <span className="identity-source" id={`source-${m.suffix}`}>{sourceFor(m.key)}</span>}</label>
          {m.key === 'sexo' ? <div className="select-wrap"><select {...input}><option value="">Sin dato informado</option><option value="F">Femenino</option><option value="M">Hombre</option><option value="I">Indeterminado</option><option value="U">No hay información</option></select></div>
            : <input {...input} type={m.type || 'text'} maxLength={m.key === 'edad' ? 2 : 500} {...(m.key === 'edad' ? {min: 0, max: 17, inputMode: 'numeric' as const} : {})} {...(['primerNombre','segundoNombre','primerApellido','segundoApellido'].includes(m.key) ? {'data-name-part': true} : {})} />}
          <p className="field-help" id={`${id}-help`}>{m.help}</p><p className="field-error" id={`${id}-error`} hidden={!errors[m.key]}>{errors[m.key]}</p>
        </div>;
      })}
      {identity && !identity.nombresComponentes && <div className="field"><label htmlFor="adult-cu-nombres">Nombres</label><input id="adult-cu-nombres" value={identity.nombres} readOnly /></div>}
      {identity && !identity.apellidosComponentes && <div className="field"><label htmlFor="adult-cu-apellidos">Apellidos</label><input id="adult-cu-apellidos" value={identity.apellidos} readOnly /></div>}
      {children}
    </div>
    {!nna && <p className="field-help" id="adult-source-help">{cu ? 'La identidad proviene de ClaveÚnica. Los antecedentes registrales complementarios se incorporan internamente.'
      : 'La comprobación del RUN y la obtención de antecedentes no autentican a quien presenta la Comunicación.'}</p>}
  </>;
}
