export function AudienceSelection({onNna, onAdult}: {onNna: () => void; onAdult: () => void}) { return <main
      aria-labelledby="landing-title"
      className="shell landing"
      id="landing"
      tabIndex={-1}
    >
      <div className="landing-hero">
        <div className="landing-copy">
          <p className="eyebrow">
            <span aria-hidden="true" className="accent-line"></span
            ><span>DEFENSORÍA DE LA NIÑEZ · FORMULARIO DE CONTACTO</span>
          </p>
          <h1 id="landing-title" tabIndex={-1}>
            Estamos aquí para <span>escucharte.</span>
          </h1>
          <p>
            Queremos conocer tu consulta, solicitud o situación. Para comenzar,
            dinos quién nos escribe.
          </p>
        </div>
        <div aria-hidden="true" className="landing-decoration">
          <div className="landing-orbit orbit-one"></div>
          <div className="landing-orbit orbit-two"></div>
          <span className="landing-mark"
            ><svg className="icon"><use href="#i-heart"></use></svg
          ></span>
          <span className="voice-tag">Tu voz importa.</span><span className="voice-dot"/><span className="voice-dot two"/>
        </div>
      </div>
      <h2 className="selection-title">¿Quién está completando el formulario?</h2>
      <div className="audience-grid">
        <button
          aria-describedby="start-nna-desc"
          className="audience-card audience-nna"
          id="start-nna" onClick={onNna}
          type="button"
        >
          <span aria-hidden="true" className="audience-icon"
            ><svg className="icon"><use href="#i-heart"></use></svg
          ></span>
          <span className="audience-meta">Para ti</span>
          <strong>Soy niño, niña o adolescente</strong>
          <span className="audience-description" id="start-nna-desc"
            >Puedes contarnos lo que te pasa o pedir orientación. No necesitas
            ClaveÚnica ni conocer tu RUN.</span
          >
          <span className="audience-action"
            >Quiero contarles algo
            <svg aria-hidden="true" className="icon small">
              <use href="#i-arrow"></use></svg
          ></span>
        </button>
        <button
          aria-describedby="start-adult-desc"
          className="audience-card audience-adult"
          id="start-adult" onClick={onAdult}
          type="button"
        >
          <span aria-hidden="true" className="audience-icon"
            ><svg className="icon"><use href="#i-message"></use></svg
          ></span>
          <span className="audience-meta">Para personas mayores de edad</span>
          <strong>Soy una persona adulta</strong>
          <span className="audience-description" id="start-adult-desc"
            >Puedes hacer una consulta, informar una vulneración de derechos o
            solicitar una actividad.</span
          >
          <span className="audience-action"
            >Comenzar
            <svg aria-hidden="true" className="icon small">
              <use href="#i-arrow"></use></svg
          ></span>
        </button>
      </div>
      <div className="landing-trust" role="note">
        <svg aria-hidden="true" className="icon small">
          <use href="#i-lock"></use></svg
        ><span
          >Tu privacidad importa. Completa solo los datos necesarios y revisa tus respuestas antes de enviarlas.</span
        >
      </div>
      <ol className="landing-steps" aria-label="Cómo funciona"><li><b aria-hidden="true">1</b>Elige quién nos escribe</li><li><b aria-hidden="true">2</b>Cuéntanos qué necesitas</li><li><b aria-hidden="true">3</b>Revisa y envía tu comunicación</li></ol>
    </main>; }
