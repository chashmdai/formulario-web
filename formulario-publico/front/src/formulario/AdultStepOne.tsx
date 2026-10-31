import {ReadableSelect} from '../ui/ReadableSelect';
import type { AdultContext } from "./AdultFlow";
import { IdentificationControls } from "./IdentificationControls";

/** Estructura y textos trasladados del mockup institucional. */
export function AdultStepOne({ctx}: {ctx:AdultContext}) {return (
<section aria-labelledby="titulo-identificacion" className="paper" data-section-tone="identity" id="identificacion" hidden={(!ctx.full && ctx.step !== 1) || ctx.blockIdentity}>
{" "}<div className="paper-top">
{" "}<div className="paper-heading">
{" "}<span className="paper-num">{"01"}</span>
{" "}<div>
{" "}<h2 className="paper-title" id="titulo-identificacion" tabIndex={-1}>{ctx.cu ? "Tus datos de identificación" : "Tus datos de identificación"}</h2>
{" "}<p className="paper-caption">{ctx.cu ? "Datos de sesión y datos complementarios." : "Solo se solicitarán los antecedentes que sea necesario completar."}</p>
{" "}</div>
{" "}</div>
{" "}<span className="status-label" id="adult-identity-status">{ctx.cu && ctx.session?.authenticated ? "Identidad ClaveÚnica" : "Sin ClaveÚnica"}</span>
{" "}<span className="sr-only">{"Los datos de identificación se tratan según su\n                  procedencia."}</span>
{" "}</div>
{" "}<div className="paper-body">
{" "}<div className="hint-line identity-notice" id="adult-identity-notice" role="status">
{" "}<svg aria-hidden="true" className="icon small">
{" "}<use href="#i-info">
</use>
</svg>
<span id="adult-identity-copy">{ctx.cu ? "Tu identidad proviene de ClaveÚnica. Los antecedentes registrales se incorporan internamente." : "Puedes identificarte con RUN chileno o con un documento extranjero si no tienes RUN. Solo solicitamos los antecedentes necesarios."}</span>
{" "}</div>
{" "}<IdentificationControls journey={ctx.journey} session={ctx.session} identityRef={ctx.identityRef} />
<div className="nna-subsection adult-preferences"><h3>¿Cómo prefieres que te llamemos?</h3><p className="section-intro">Es opcional. Estas preferencias se guardan por separado de tus datos de identificación.</p><div className="form-grid">
<div className="field"><label htmlFor="adult-preferred">Nombre que prefieres usar <span className="muted-label">(opcional)</span></label><input id="adult-preferred" type="text" maxLength={500} {...ctx.bind("adult-preferred")}/></div>
<div className="field"><label htmlFor="adult-treatment">¿Cómo prefieres que nos refiramos a ti? <span className="muted-label">(opcional)</span></label><div className="select-wrap"><ReadableSelect id="adult-treatment" {...ctx.bind("adult-treatment")}><option value="">Sin preferencia indicada</option><option value="nombre">Usen mi nombre</option><option value="ella">Ella</option><option value="el">Él</option><option value="elle">Elle</option><option value="otra">De otra forma</option><option value="noresponde">Prefiero no responder</option></ReadableSelect></div></div>
</div><div className="field" hidden={ctx.value("adult-treatment")!=="otra"}><label htmlFor="adult-treatment-text">Cuéntanos cómo prefieres que nos refiramos a ti</label><input id="adult-treatment-text" type="text" maxLength={500} {...ctx.bind("adult-treatment-text")}/></div></div>{" "}{" "}{" "}{" "}</div>
{" "}</section>
);}
