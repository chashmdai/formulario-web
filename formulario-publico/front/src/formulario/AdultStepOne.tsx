import type { AdultContext } from "./AdultFlow";
import { IdentificationControls } from "./IdentificationControls";

/** Estructura y textos trasladados del mockup institucional. */
export function AdultStepOne({ctx}: {ctx:AdultContext}) {return (
<section aria-labelledby="titulo-identificacion" className="paper" data-section-tone="identity" id="identificacion" hidden={(!ctx.full && ctx.step !== 1) || ctx.blockIdentity}>
{" "}<div className="paper-top">
{" "}<div className="paper-heading">
{" "}<span className="paper-num">{"01"}</span>
{" "}<div>
{" "}<h2 className="paper-title" id="titulo-identificacion" tabIndex={-1}>{ctx.cu ? "Tus datos de identificación" : "Comencemos con tu RUN"}</h2>
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
<span id="adult-identity-copy">{ctx.cu ? "Tu identidad proviene de ClaveÚnica. Los antecedentes registrales se incorporan internamente." : "Ingresa primero tu RUN. Solo te pediremos los datos personales que falten; los antecedentes recuperados no se muestran en pantalla."}</span>
{" "}</div>
{" "}<IdentificationControls journey={ctx.journey} session={ctx.session} identityRef={ctx.identityRef} />{" "}{" "}{" "}{" "}</div>
{" "}</section>
);}
