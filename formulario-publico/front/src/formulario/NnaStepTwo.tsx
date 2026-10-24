import type { NnaContext } from "./NnaFlow";

/** Estructura y textos del mockup institucional; la lógica reside en NnaFlow. */
export function NnaStepTwo({ctx}: {ctx: NnaContext}) {
  return (<section aria-labelledby="nna-story-title" className="paper" data-section-tone="communication" id="nna-step-two" hidden={!ctx.full && ctx.step !== 2}>
{" "}<div className="paper-top">
{" "}<div className="paper-heading">
{" "}<span className="paper-num">{"02"}</span>
{" "}<div>
{" "}<h2 className="paper-title" id="nna-story-title" tabIndex={-1}>{"\n                      ¿Qué quieres contarnos?\n                    "}</h2>
{" "}<p className="paper-caption">{"\n                      Escribe con tus propias palabras. No necesitas conocer\n                      todos los detalles.\n                    "}</p>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}<div className="paper-body">
{" "}<div className="pane-fields">
{" "}<div className="field">
{" "}<label htmlFor="nna-story">{"Cuéntanos qué pasó o cómo podemos ayudarte\n                      "}<span aria-hidden="true" className="required-mark">{"*"}</span>
</label>
<textarea aria-describedby="nna-story-help nna-story-error" id="nna-story" maxLength={5000} name="nna-story" placeholder="Escribe aquí lo que quieras contarnos..." rows={6} {...ctx.bind("nna-story")}>
</textarea>
{" "}<p className="field-help" id="nna-story-help">{"\n                      Puedes contar qué ocurrió, cuándo, quiénes participaron o\n                      qué necesitas. No tienes que responder todas esas\n                      preguntas.\n                    "}</p>
{" "}<p className="field-error" id="nna-story-error" hidden={!ctx.errors["nna-story"]}>{ctx.errors["nna-story"]}</p>
{" "}</div>
{" "}<div className="char-counter" id="nna-story-count">{ctx.value("nna-story").length} caracteres</div>
{" "}<div className="field">
{" "}<label htmlFor="nna-files">{"¿Quieres agregar fotos o archivos?\n                      "}<span className="muted-label">{"(opcional)"}</span>
</label>
{" "}<div className="upload-box">
{" "}<input className="sr-only" id="nna-files" name="nna-files" type="file" multiple accept=".pdf,.png,.jpg,.jpeg,.webp,.txt,.doc,.docx" disabled={ctx.uploading} onChange={ctx.onFiles} />
<label className="upload-target" htmlFor="nna-files">
<span className="upload-icon">
<svg aria-hidden="true" className="icon">
{" "}<use href="#i-upload">
</use>
</svg>
</span>
<span>
<strong>{"Seleccionar archivos"}</strong>
<small>{"Opcional. Máx. 5 archivos, 10 MB por archivo\n                            y 25 MB en total."}</small>
</span>
</label>
{" "}</div>
{" "}<p aria-live="polite" className="file-feedback" id="nna-files-feedback">{ctx.fileFeedback}</p>
{" "}<button className="btn btn-secondary remove-files" hidden={!ctx.shown("nna-files-clear")} id="nna-files-clear" type="button" onClick={() => ctx.action("nna-files-clear")}>{"\n                      Quitar archivos seleccionados\n                    "}</button>
{" "}</div>
{" "}</div>
{" "}<div className="privacy-note">
{" "}<svg aria-hidden="true" className="icon small">
{" "}<use href="#i-lock">
</use>
</svg>
<span>{"Tu información se utilizará para gestionar tu comunicación.\n                    Revisa tus datos antes de enviarlos."}</span>
{" "}</div>
{" "}<div className="step-actions">
{" "}<button className="btn btn-secondary" id="nna-back" type="button" onClick={() => ctx.action("nna-back")} hidden={ctx.full}>{"\n                    Volver a mis datos"}</button>
<button className="btn btn-primary" id="nna-review" type="button" onClick={() => ctx.action("nna-review")}>{"\n                    Revisar mi comunicación\n                    "}<svg aria-hidden="true" className="icon small">
{" "}<use href="#i-arrow">
</use>
{" "}</svg>
{" "}</button>
{" "}</div>
{" "}</div>
{" "}</section>);
}
