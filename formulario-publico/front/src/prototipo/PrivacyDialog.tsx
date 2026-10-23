import { useEffect, useRef, useState } from 'react';

/** Aviso institucional del prototipo. Elección del canal en memoria; no acredita identidad. */
export function PrivacyDialog({onClose, onAccept}: {onClose: () => void; onAccept: () => void}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const homeButton = useRef<HTMLButtonElement>(null);
  const fieldset = useRef<HTMLFieldSetElement>(null);
  const focusChoice = useRef(false);
  const [decision, setDecision] = useState<'yes' | 'no' | null>(null);
  const [alternatives, setAlternatives] = useState(false);
  useEffect(() => {
    const current = dialog.current;
    current?.showModal();
    return () => current?.close();
  }, []);
  useEffect(() => {
    if (alternatives) homeButton.current?.focus({preventScroll: true});
    else if (focusChoice.current) {
      fieldset.current?.focus({preventScroll: true});
      fieldset.current?.scrollIntoView?.({behavior: 'auto', block: 'nearest'});
      focusChoice.current = false;
    }
  }, [alternatives]);
  function continueChoice() {
    if (decision === 'no') setAlternatives(true);
    else if (decision === 'yes') onAccept();
  }
  function rechoose() { focusChoice.current = true; setDecision(null); setAlternatives(false); }
  return (
    <dialog
      aria-describedby="privacy-intro"
      aria-labelledby="privacy-title"
      className="dialog privacy-dialog"
      id="privacy-access" ref={dialog} onCancel={onClose}
    >
      <div className="dialog-head">
        <div>
          <span className="mini-tag">PROTECCIÓN DE DATOS PERSONALES</span>
          <h2 id="privacy-title">
            Antes de continuar, conoce cómo cuidaremos tus datos
          </h2>
          <p id="privacy-intro">
            Este aviso corresponde al formulario digital para personas adultas.
          </p>
        </div>
        <button
          aria-label="Cerrar y volver al inicio"
          className="dialog-close"
          id="privacy-close" onClick={onClose}
          type="button"
        >
          <svg className="icon"><use href="#i-x"></use></svg>
        </button>
      </div>
      <div id="privacy-choice-panel" hidden={alternatives}>
        <div className="privacy-content">
          <div className="privacy-intro-card">
            <svg aria-hidden="true" className="icon">
              <use href="#i-lock"></use>
            </svg>
            <div>
              <strong
                >Tu información se utilizará para gestionar tu
                comunicación</strong
              >
              <p>
                La Defensoría de la Niñez es responsable de los datos que recibe
                en el ejercicio de sus funciones legales.
              </p>
            </div>
          </div>
          <h3>¿Para qué se utilizan?</h3>
          <p>
            Si decides utilizar este formulario, la Defensoría podrá recopilar y
            tratar los datos que proporciones, incluidos antecedentes de niños,
            niñas o adolescentes cuando sean necesarios, para{' '}
            <strong
              >recibir, registrar, evaluar, clasificar, derivar, gestionar,
              hacer seguimiento y responder</strong
            >{' '}
            tu comunicación en el marco de sus competencias legales.
          </p>
          <h3>¿Cómo se resguardan?</h3>
          <p>
            Solo se solicitarán los datos necesarios para atender lo comunicado.
            Cuando corresponda, la institución podrá verificar antecedentes
            mediante los servicios de interoperabilidad habilitados y
            comunicarlos a organismos competentes para una finalidad legal
            específica, bajo las reglas aplicables de confidencialidad y
            seguridad. La información no debe usarse para fines ajenos a la
            gestión informada.
          </p>
          <p>
            Podrás solicitar información sobre el tratamiento y ejercer los
            derechos que te reconozca la legislación vigente a través de los
            canales oficiales de la Defensoría. La conservación de datos deberá
            ajustarse a los plazos y obligaciones legales aplicables.
          </p>
          <p className="privacy-legal">
            Fundamento: Ley N.° 21.067 y Ley N.° 19.628. La Ley N.° 21.719
            refuerza el régimen de protección de datos desde el{' '}
            <strong>1 de diciembre de 2026</strong>; en particular, regula el
            tratamiento que realizan los organismos públicos en sus funciones
            legales.{' '}
            <a
              href="https://www.bcn.cl/leychile/navegar?idNorma=1209272"
              rel="noopener noreferrer"
              target="_blank"
              >Consultar la Ley 21.719 <span aria-hidden="true">↗</span></a
            >
          </p>
          <fieldset className="privacy-fieldset" id="privacy-fieldset" ref={fieldset} tabIndex={-1}>
            <legend>
              ¿Quieres continuar mediante este formulario digital?
            </legend>
            <label className="privacy-choice"
              ><input name="privacy-decision" type="radio" value="yes" checked={decision === "yes"} onChange={() => setDecision("yes")} /><span
                ><strong>Acepto</strong
                ><small
                  >He leído este aviso y elijo continuar por este medio.</small
                ></span
              ></label
            >
            <label className="privacy-choice"
              ><input name="privacy-decision" type="radio" value="no" checked={decision === "no"} onChange={() => setDecision("no")} /><span
                ><strong>No acepto</strong
                ><small
                  >Prefiero conocer otros canales de atención de la
                  Defensoría.</small
                ></span
              ></label
            >
          </fieldset>
          <p className="field-help privacy-explanation">
            Esta elección corresponde al uso del canal digital.{' '}
            <strong>No reemplaza las bases legales</strong> para el tratamiento
            de datos de un organismo público, ni implica una autorización para
            otros fines.
          </p>
        </div>
        <div className="dialog-foot privacy-footer">
          <button className="btn btn-secondary" id="privacy-back" onClick={onClose} type="button">
            Volver</button
          ><button
            className="btn btn-primary"
            disabled={!decision}
            id="privacy-continue" onClick={continueChoice}
            type="button"
          >
            Continuar
            <svg aria-hidden="true" className="icon small">
              <use href="#i-arrow"></use>
            </svg>
          </button>
        </div>
      </div>
      <div className="privacy-alternatives" hidden={!alternatives} id="privacy-alternatives">
        <div className="privacy-content">
          <div className="privacy-alternative-title">
            <svg aria-hidden="true" className="icon">
              <use href="#i-message"></use>
            </svg>
            <h3>Otras formas de comunicarte</h3>
          </div>
          <p>
            No tienes que utilizar este formulario para comunicarte con la
            Defensoría. Puedes recurrir a sus canales oficiales:
          </p>
          <div className="privacy-contact-grid">
            <a className="privacy-contact-item" href="tel:+56224979602"
              ><strong>Atención de usuarios</strong><span>+56 2 2497 9602</span
              ><small>Llamar por teléfono</small></a
            ><a className="privacy-contact-item" href="tel:+56224979600"
              ><strong>Mesa central</strong><span>+56 2 2497 9600</span
              ><small>Llamar por teléfono</small></a
            ><a
              className="privacy-contact-item"
              href="https://www.defensorianinez.cl/"
              rel="noopener noreferrer"
              target="_blank"
              ><strong>Atención presencial</strong
              ><span>Teatinos 340, Santiago</span
              ><small>Revisar oficinas y horarios oficiales ↗</small></a
            >
          </div>
          <p className="field-help">
            Los datos de contacto y horarios deben verificarse antes de publicar
            esta interfaz de manera definitiva.
          </p>
        </div>
        <div className="dialog-foot privacy-footer">
          <button className="btn btn-secondary" id="privacy-rechoose" onClick={rechoose} type="button">
            Cambiar mi respuesta</button
          ><button className="btn btn-primary" id="privacy-home" ref={homeButton} onClick={onClose} type="button">
            Volver al inicio
          </button>
        </div>
      </div>
    </dialog>
  );
}
