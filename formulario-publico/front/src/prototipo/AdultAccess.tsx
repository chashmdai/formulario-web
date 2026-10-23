import type { RefObject } from 'react';
import { ClaveUnicaButton } from '../ClaveUnicaButton';
export function AdultAccess({dialogRef, onClose, onManual, manualUnavailable}: {dialogRef: RefObject<HTMLDialogElement | null>; onClose: () => void; onManual: () => void; manualUnavailable: boolean}) { return <dialog
      aria-describedby="adult-access-desc"
      aria-labelledby="adult-access-title"
      className="dialog adult-access-dialog"
      id="adult-access" ref={dialogRef}
    >
      <div className="dialog-head">
        <div>
          <span className="mini-tag">ACCESO AL FORMULARIO</span>
          <h2 id="adult-access-title">Ingresa como persona adulta</h2>
          <p id="adult-access-desc">
            Para continuar, elige cómo deseas identificarte.
          </p>
        </div>
        <button
          aria-label="Cerrar y volver al inicio"
          className="dialog-close"
          id="adult-access-close" onClick={onClose}
          type="button"
        >
          <svg className="icon"><use href="#i-x"></use></svg>
        </button>
      </div>
      <div className="access-body">
        <div className="access-primary">
          <span aria-hidden="true" className="access-illustration"
            ><svg className="icon"><use href="#i-lock"></use></svg
          ></span>
          <h3>Ingresa con tu ClaveÚnica</h3>
          <p>
            Este acceso permite identificarte con el mecanismo oficial del Estado de Chile. Continuarás al sitio oficial de ClaveÚnica.
          </p>
          <ClaveUnicaButton />
          <p className="access-footnote">
            El acceso oficial utiliza la sesión validada por el servidor.
          </p>
        </div>
        <div className="access-secondary">
          <span>¿No puedes utilizar ClaveÚnica?</span
          ><button className="manual-access" id="use-manual" onClick={onManual} type="button">
            No tengo ClaveÚnica
            <svg aria-hidden="true" className="icon small">
              <use href="#i-arrow"></use>
            </svg></button
          ><small
            >Necesitarás tu RUN y datos de identificación para intentar una
            comprobación registral.</small
          >
        </div>
      </div>
    {manualUnavailable && <p className="notice" role="status">Este acceso todavía no está disponible. Puedes volver al inicio.</p>}</dialog>; }
