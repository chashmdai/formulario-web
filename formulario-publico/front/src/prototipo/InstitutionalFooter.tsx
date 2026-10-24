export function InstitutionalFooter({note}: {note?:string} = {}) { return <footer className="page-footer">
      <div className="shell footer-inner">
        <div>
          <strong>Defensoría de la Niñez · Formulario Público</strong>
          <p>
            {note || <>Versión de demostración para revisión. Las integraciones, el
            tratamiento de datos y las autorizaciones de publicación requieren
            validación institucional.</>}
          </p>
        </div>
        <div aria-hidden="true" className="footer-dots">
          <span></span><span></span><span></span><span></span><span></span
          ><span></span><span></span>
        </div>
      </div>
    </footer>; }
