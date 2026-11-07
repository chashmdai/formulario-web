import {ReadableSelect} from '../ui/ReadableSelect';
import type { AdultContext } from "./AdultFlow";
import { IdentificationControls } from "./IdentificationControls";

/** Paso 1: identificación del presentador y preferencias de trato, separadas de la identidad. */
export function AdultStepOne({ctx}: {ctx:AdultContext}) {
  const verified = ctx.cu && ctx.session?.authenticated;
  return <section aria-labelledby="titulo-identificacion" className="paper" data-section-tone="identity" id="identificacion" hidden={(!ctx.full && ctx.step !== 1) || ctx.blockIdentity}>
    <div className="paper-top">
      <div className="paper-heading">
        <span className="paper-num">01</span>
        <div>
          <h2 className="paper-title" id="titulo-identificacion" tabIndex={-1}>Tus datos de identificación</h2>
          <p className="paper-caption">Te pediremos solo los datos que falten.</p>
        </div>
      </div>
      <span className={`status-label${verified ? " is-verified" : ""}`} id="adult-identity-status">{verified ? "Identidad ClaveÚnica" : "Sin ClaveÚnica"}</span>
    </div>
    <div className="paper-body">
      <IdentificationControls journey={ctx.journey} session={ctx.session} identityRef={ctx.identityRef} />
      <div className="nna-subsection adult-preferences">
        <h3>¿Cómo prefieres que te llamemos?</h3>
        <p className="section-intro">Opcional. No cambia tus datos de identificación.</p>
        <div className="form-grid">
          <div className="field"><label htmlFor="adult-preferred">Nombre que prefieres usar <span className="muted-label">(opcional)</span></label><input id="adult-preferred" type="text" maxLength={500} autoCapitalize="words" {...ctx.bind("adult-preferred")}/></div>
          <div className="field"><label htmlFor="adult-treatment">¿Cómo prefieres que nos refiramos a ti? <span className="muted-label">(opcional)</span></label><div className="select-wrap"><ReadableSelect id="adult-treatment" {...ctx.bind("adult-treatment")}><option value="">Sin preferencia indicada</option><option value="nombre">Usen mi nombre</option><option value="ella">Ella</option><option value="el">Él</option><option value="elle">Elle</option><option value="otra">De otra forma</option><option value="noresponde">Prefiero no responder</option></ReadableSelect></div></div>
        </div>
        <div className="field conditional" hidden={ctx.value("adult-treatment")!=="otra"}><label htmlFor="adult-treatment-text">Cuéntanos cómo prefieres que nos refiramos a ti</label><input id="adult-treatment-text" type="text" maxLength={500} {...ctx.bind("adult-treatment-text")}/></div>
      </div>
    </div>
  </section>;
}
