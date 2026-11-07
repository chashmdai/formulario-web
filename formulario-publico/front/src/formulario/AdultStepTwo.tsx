import {ReadableSelect} from '../ui/ReadableSelect';
import type { AdultContext } from "./AdultFlow";

const Required = ({show}: {show: boolean}) => <span className="required-mark conditional-required" aria-hidden="true" hidden={!show}> *</span>;
const Arrow = () => <svg aria-hidden="true" className="icon small"><use href="#i-arrow" /></svg>;

/** Paso 2: motivo de contacto. Las opciones y su valor enviado no cambian. */
export function AdultStepTwo({ctx}: {ctx:AdultContext}) {
  const reason = ctx.value("motivo"), topic = ctx.value("tema"), acknowledged = ctx.value("consulta-confirmada") === "true";
  return <section aria-labelledby="titulo-motivo" className="paper" data-section-tone="reason" id="motivo-seccion" hidden={!ctx.full && ctx.step !== 2}>
    <div className="paper-top">
      <div className="paper-heading">
        <span className="paper-num">02</span>
        <div>
          <h2 className="paper-title" id="titulo-motivo" tabIndex={-1}>¿Cuál es el motivo de tu contacto?</h2>
          <p className="paper-caption">Así te mostraremos solo las preguntas que correspondan.</p>
        </div>
      </div>
    </div>
    <div className="paper-body">
      <div className={reason === "consulta" || !reason ? "choice-area single-choice" : "choice-area"}>
        <div className="field">
          <label htmlFor="motivo">Motivo principal<Required show={ctx.required("motivo")} /></label>
          <div className="select-wrap">
            <ReadableSelect id="motivo" name="motivo" {...ctx.bind("motivo")}>
              <option disabled value="">Selecciona el motivo de contacto</option>
              <option value="consulta">Realizar una consulta</option>
              <option value="vulneracion">Informar una vulneración de derechos</option>
              <option value="promocion">Solicitar una actividad de Promoción y Difusión</option>
            </ReadableSelect>
          </div>
        </div>
        <div aria-atomic="true" aria-live="polite" className="context-card">
          <span className="context-eyebrow"><svg className="icon small" aria-hidden="true"><use href="#i-info" /></svg>Sobre esta opción</span>
          <strong id="context-titulo">{ctx.reasonTitle}</strong>
          <p id="context-texto">{ctx.reasonText}</p>
        </div>
      </div>
      <div className="topic-selector conditional" hidden={!ctx.shown("tema-selector")} id="tema-selector">
        <div className="field">
          <label htmlFor="tema">¿Qué tipo de consulta deseas realizar?<Required show={ctx.required("tema")} /></label>
          <div className="select-wrap">
            <ReadableSelect id="tema" name="tema" {...ctx.bind("tema")}>
              <option value="">Selecciona qué necesitas</option>
              <option value="entrevista">Solicitar una entrevista</option>
              <option value="empleo">Oportunidades laborales</option>
              <option value="estadisticas">Estudios, publicaciones o estadísticas sobre niñez y adolescencia</option>
              <option value="general">Otra consulta u orientación general</option>
            </ReadableSelect>
          </div>
        </div>
      </div>
      <div className="consult-info guidance-alert conditional" hidden={!ctx.shown("consulta-guidance")} id="consulta-guidance" role="status">
        <div>
          <span className="mini-tag">Antes de consultar</span>
          <h3>Revisa primero los canales disponibles</h3>
          <p>Algunos temas tienen su propia plataforma oficial.</p>
          <span id="consulta-ack-status" className={acknowledged ? "ack-status ok" : "ack-status"}>{acknowledged ? "Orientación revisada" : "Pendiente de lectura"}</span>
        </div>
        <button className="btn btn-secondary" id="abrir-orientacion" type="button" onClick={() => ctx.action("abrir-orientacion")}>Ver orientación <Arrow /></button>
      </div>
      <div className="resource-panel conditional" hidden={!ctx.shown("consulta-redirect-panel")} id="consulta-redirect-panel">
        <div className="resource-top">
          <span className="mini-tag">Canal recomendado</span>
          <h3 id="resource-title">{topic === "empleo" ? "Las postulaciones laborales se realizan en Empleos Públicos" : "Revisa primero estas fuentes"}</h3>
        </div>
        <div hidden={!ctx.shown("resource-empleo")} id="resource-empleo">
          <a className="btn btn-primary external-btn" href="https://www.empleospublicos.cl/" rel="noopener noreferrer" target="_blank">Ir a Empleos Públicos ↗</a>
          <p className="field-help">Las ofertas y postulaciones se gestionan en el portal del Servicio Civil, no en este formulario.</p>
        </div>
        <div hidden={!ctx.shown("resource-estadisticas")} id="resource-estadisticas">
          <div className="resource-links">
            <a className="resource-link" href="https://observatorio.defensorianinez.cl/" rel="noopener noreferrer" target="_blank"><strong>Observatorio de Derechos ↗</strong><small>Indicadores, visualizaciones y publicaciones</small></a>
            <a className="resource-link" href="https://www.defensorianinez.cl/informe-anual/" rel="noopener noreferrer" target="_blank"><strong>Informe Anual ↗</strong><small>Balance institucional y situación de derechos</small></a>
            <a className="resource-link" href="https://www.defensorianinez.cl/home-adulto/estudios-y-estadisticas/" rel="noopener noreferrer" target="_blank"><strong>Estudios ↗</strong><small>Investigaciones, estudios y encuestas</small></a>
          </div>
          <label className="ack-checkbox">
            <input id="stats-confirm" type="checkbox" {...ctx.check("stats-confirm")} />
            <span>Ya revisé el material disponible y necesito información que no encontré.</span>
          </label>
        </div>
      </div>
    </div>
  </section>;
}
