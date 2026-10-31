import {ReadableSelect} from '../ui/ReadableSelect';
import type { AdultContext } from "./AdultFlow";

/** Estructura y textos trasladados del mockup institucional. */
export function AdultStepTwo({ctx}: {ctx:AdultContext}) {return (
<section aria-labelledby="titulo-motivo" className="paper" data-section-tone="reason" id="motivo-seccion" hidden={(!ctx.full && ctx.step !== 2)}>
{" "}<div className="paper-top">
{" "}<div className="paper-heading">
{" "}<span className="paper-num">{"02"}</span>
{" "}<div>
{" "}<h2 className="paper-title" id="titulo-motivo" tabIndex={-1}>{"\n                      ¿Cuál es el motivo de tu contacto?\n                    "}</h2>
{" "}<p className="paper-caption">{"\n                      Esto nos ayuda a mostrarte preguntas relacionadas con tu\n                      comunicación.\n                    "}</p>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}<div className="paper-body">
{" "}<div className={ctx.value("motivo") === "consulta" || !ctx.value("motivo") ? "choice-area single-choice" : "choice-area"}>
{" "}<div className="field">
{" "}<label htmlFor="motivo">{"Motivo principal"}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("motivo")}>{" *"}</span>
</label>
{" "}<div className="select-wrap">
{" "}<ReadableSelect id="motivo" name="motivo" {...ctx.bind("motivo")}>
{" "}<option disabled={true} value="">{"\n                          Selecciona el motivo de contacto\n                        "}</option>
{" "}<option value="consulta">{"Realizar una consulta"}</option>
{" "}<option value="vulneracion">{"\n                          Informar una vulneración de derechos\n                        "}</option>
{" "}<option value="promocion">{"\n                          Solicitar una actividad de Promoción y Difusión\n                        "}</option>
{" "}</ReadableSelect>
{" "}</div>
{" "}<p className="field-help">{"\n                      Selecciona la alternativa que se acerque más a lo que\n                      necesitas.\n                    "}</p>
{" "}</div>
{" "}<div aria-atomic="true" aria-live="polite" className="context-card">
{" "}<span className="context-eyebrow">
<svg className="icon small">
<use href="#i-info">
</use>
</svg>{"\n                      Sobre esta opción"}</span>
<strong id="context-titulo" className="">{ctx.reasonTitle}</strong>
{" "}<p id="context-texto" className="">{ctx.reasonText}</p>
{" "}</div>
{" "}</div>
{" "}<div className="topic-selector" hidden={!ctx.shown("tema-selector")} id="tema-selector">
{" "}<div className="field">
{" "}<label htmlFor="tema">{"¿Qué tipo de consulta deseas realizar?"}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("tema")}>{" *"}</span>
</label>
{" "}<div className="select-wrap">
{" "}<ReadableSelect id="tema" name="tema" {...ctx.bind("tema")}>
{" "}<option value="">{"Selecciona qué necesitas"}</option>
{" "}<option value="entrevista">{"\n                          Solicitar una entrevista\n                        "}</option>
{" "}<option value="empleo">{"Oportunidades laborales"}</option>
{" "}<option value="estadisticas">{"\n                          Estudios, publicaciones o estadísticas sobre niñez y adolescencia\n                        "}</option>
{" "}<option value="general">{"\n                          Otra consulta u orientación general\n                        "}</option>
{" "}</ReadableSelect>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}<div className="consult-info guidance-alert" hidden={!ctx.shown("consulta-guidance")} id="consulta-guidance" role="status">
{" "}<div>
{" "}<span className="mini-tag">{"ORIENTACIÓN IMPORTANTE"}</span>
{" "}<h3>{"\n                      Antes de realizar una consulta, revisa las vías\n                      disponibles\n                    "}</h3>
{" "}<p>{"\n                      Algunos asuntos cuentan con plataformas oficiales. Revisa\n                      la orientación y confirma que necesitas comunicarte a\n                      través de este formulario.\n                    "}</p>
{" "}<span id="consulta-ack-status" className={ctx.value("consulta-confirmada")==="true"?"ack-status ok":"ack-status"}>{ctx.value("consulta-confirmada") === "true" ? "Orientación revisada" : "Pendiente de lectura"}</span>
{" "}</div>
{" "}<button className="btn btn-secondary" id="abrir-orientacion" type="button" onClick={() => ctx.action("abrir-orientacion")}>{"\n                    Ver orientación\n                    "}<svg aria-hidden="true" className="icon small">
{" "}<use href="#i-arrow">
</use>
{" "}</svg>
{" "}</button>
{" "}</div>
{" "}<div className="resource-panel" hidden={!ctx.shown("consulta-redirect-panel")} id="consulta-redirect-panel">
{" "}<div className="resource-top">
{" "}<span className="mini-tag">{"CANAL RECOMENDADO"}</span>
{" "}<h3 id="resource-title">{ctx.value("tema")==="empleo"?"Las postulaciones laborales se realizan en Empleos Públicos":"Revisa los recursos disponibles"}</h3>
{" "}<p id="resource-description">
</p>
{" "}</div>
{" "}<div hidden={!ctx.shown("resource-empleo")} id="resource-empleo">
{" "}<a className="btn btn-primary external-btn" href="https://www.empleospublicos.cl/" rel="noopener noreferrer" target="_blank">{"Ir a Empleos Públicos ↗"}</a>
{" "}<p className="field-help">{"\n                      La publicación de ofertas y las postulaciones se revisan\n                      en el portal del Servicio Civil. Este formulario no\n                      funciona como canal de postulación.\n                    "}</p>
{" "}</div>
{" "}<div hidden={!ctx.shown("resource-estadisticas")} id="resource-estadisticas"><h3>¿Necesitas acceder a estudios, publicaciones o estadísticas sobre niñez y adolescencia?</h3>
{" "}<div className="resource-links">
{" "}<a className="resource-link" href="https://observatorio.defensorianinez.cl/" rel="noopener noreferrer" target="_blank">
<strong>{"Observatorio de Derechos ↗"}</strong>
<small>{"Indicadores, visualizaciones y publicaciones"}</small>
</a>
<a className="resource-link" href="https://www.defensorianinez.cl/informe-anual/" rel="noopener noreferrer" target="_blank">
<strong>{"Informe Anual ↗"}</strong>
<small>{"Balance institucional y situación de derechos"}</small>
</a>

<a className="resource-link" href="https://www.defensorianinez.cl/home-adulto/estudios-y-estadisticas/" rel="noopener noreferrer" target="_blank"><strong>Estudios ↗</strong><small>Investigaciones, estudios y encuestas sobre diversas materias</small></a>
{" "}</div>
{" "}<label className="ack-checkbox">
<input id="stats-confirm" type="checkbox" {...ctx.check("stats-confirm")} />
<span>{"Ya revisé el material disponible y todavía necesito\n                        solicitar información que no\n                        encontré."}</span>
</label>
{" "}<p className="field-help">{"\n                      Confirma que revisaste las fuentes y describe la información que todavía necesitas.\n                    "}</p>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}</section>
);}
