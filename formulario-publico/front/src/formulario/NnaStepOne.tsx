import type { NnaContext } from "./NnaFlow";
import { IdentificationControls } from "./IdentificationControls";
import { GeoFields } from "./GeoFields";

/** Estructura y textos del mockup institucional; la lógica reside en NnaFlow. */
export function NnaStepOne({ctx}: {ctx: NnaContext}) {
  return (<section aria-labelledby="nna-ident-title" className="paper" data-section-tone="identity" id="nna-step-one" hidden={!ctx.full && ctx.step !== 1}>
{" "}<div className="paper-top">
{" "}<div className="paper-heading">
{" "}<span className="paper-num">{"01"}</span>
{" "}<div>
{" "}<h2 className="paper-title" id="nna-ident-title" tabIndex={-1}>{"\n                      Cuéntanos sobre ti\n                    "}</h2>
{" "}<p className="paper-caption">{"\n                      Puedes escribirnos sin RUN y sin ClaveÚnica. También\n                      puedes pedir ayuda a alguien de confianza, pero no es un\n                      requisito.\n                    "}</p>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}<div className="paper-body">
{" "}<IdentificationControls journey={ctx.journey} session={ctx.session} nna identityRef={ctx.identityRef}>
<GeoFields ctx={ctx} />
</IdentificationControls>{" "}{" "}{" "}<div className="nna-help-identity" id="nna-identity-help-panel">
<label className="ack-checkbox">
<input type="checkbox" disabled aria-describedby="nna-assistance-policy" />
<span>
<strong>Necesito ayuda para indicar mi nombre o apellido.</strong>
<small className="assistance-copy" id="nna-assistance-policy">Puedes pedir ayuda a alguien de confianza para completar primer nombre, primer apellido y región. La excepción para omitir estos datos está pendiente de validación institucional.</small>
</span>
</label>
</div>
{" "}<div className="nna-subsection">
{" "}<h3>{"¿Cómo te gustaría que te llamemos?"}</h3>
{" "}<p className="section-intro">{"\n                    Es opcional. Nos ayuda a saber cómo prefieres que nos\n                    dirijamos a ti; no sustituye tus datos de identificación.\n                  "}</p>
{" "}<div className="form-grid">
{" "}<div className="field">
{" "}<label htmlFor="nna-preferred">{"Nombre que prefieres usar\n                        "}<span className="muted-label">{"(opcional)"}</span>
</label>
<input aria-describedby="nna-preferred-help nna-preferred-error" id="nna-preferred" name="nna-preferred" placeholder="Puedes dejarlo en blanco" type="text" {...ctx.bind("nna-preferred")} />
{" "}<p className="field-help" id="nna-preferred-help">
</p>
{" "}<p className="field-error" id="nna-preferred-error" hidden={!ctx.errors["nna-preferred"]}>{ctx.errors["nna-preferred"]}</p>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="nna-treatment">{"¿Cómo prefieres que nos refiramos a ti?"}</label>
{" "}<div className="select-wrap">
{" "}<select aria-describedby="nna-treatment-help nna-treatment-error" id="nna-treatment" name="nna-treatment" {...ctx.bind("nna-treatment")}>
{" "}<option value="">{"Sin preferencia indicada"}</option>
{" "}<option value="nombre">{"Usen mi nombre"}</option>
{" "}<option value="ella">{"Ella"}</option>
{" "}<option value="el">{"Él"}</option>
{" "}<option value="elle">{"Elle"}</option>
{" "}<option value="otra">{"De otra forma"}</option>
{" "}<option value="noresponde">{"\n                            Prefiero no responder\n                          "}</option>
{" "}</select>
{" "}</div>
{" "}<p className="field-help" id="nna-treatment-help">{"\n                        Opcional. No estamos preguntando tu sexo registral.\n                      "}</p>
{" "}<p className="field-error" id="nna-treatment-error" hidden={!ctx.errors["nna-treatment"]}>{ctx.errors["nna-treatment"]}</p>
{" "}</div>
{" "}</div>
{" "}<div hidden={ctx.value("nna-treatment") !== "otra"} id="nna-treatment-other">
{" "}<div className="field">
{" "}<label htmlFor="nna-treatment-text">{"Cuéntanos cómo prefieres que nos refiramos a ti"}</label>
<input aria-describedby="nna-treatment-text-help nna-treatment-text-error" id="nna-treatment-text" name="nna-treatment-text" placeholder="La forma que prefieras" type="text" {...ctx.bind("nna-treatment-text")} />
{" "}<p className="field-help" id="nna-treatment-text-help">
</p>
{" "}<p className="field-error" id="nna-treatment-text-error" hidden={!ctx.errors["nna-treatment-text"]}>{ctx.errors["nna-treatment-text"]}</p>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}<div className="nna-subsection">
{" "}<h3>{"Una forma de comunicarnos contigo"}</h3>
{" "}<p className="section-intro">{"\n                    Elige la que te resulte más cómoda y segura.\n                  "}</p>
{" "}<div className="pane-fields">
{" "}<div className="field">
{" "}<label htmlFor="nna-primary-method">{"¿Cómo podemos contactarte?\n                        "}<span aria-hidden="true" className="required-mark">{"*"}</span>
</label>
{" "}<div className="select-wrap">
{" "}<select aria-describedby="nna-primary-method-help nna-primary-method-error" id="nna-primary-method" name="nna-primary-method" {...ctx.bind("nna-primary-method")}>
{" "}<option value="">{"Selecciona una opción"}</option>
{" "}<option value="celular">{"Celular"}</option>
{" "}<option value="email">{"Correo electrónico"}</option>
{" "}<option value="social">{"Red social"}</option>
{" "}<option value="unsafe">{"\n                            No tengo una forma segura de contacto\n                          "}</option>
{" "}</select>
{" "}</div>
{" "}<p className="field-help" id="nna-primary-method-help">{"\n                        Solo se usarían canales habilitados y seguros por la\n                        institución. Esta maqueta no envía mensajes.\n                      "}</p>
{" "}<p className="field-error" id="nna-primary-method-error" hidden={!ctx.errors["nna-primary-method"]}>{ctx.errors["nna-primary-method"]}</p>
{" "}</div>
{" "}<div data-channel="celular" data-contact="nna-primary" hidden={!ctx.shown("nna-primary:celular")}>
{" "}<div className="field">
{" "}<label htmlFor="nna-primary-celular">{"Número de celular"}</label>
<input aria-describedby="nna-primary-celular-help nna-primary-celular-error" autoComplete="tel" data-mask="mobile" id="nna-primary-celular" inputMode="tel" maxLength={17} name="nna-primary-celular" placeholder="+56 9 XXXX XXXX" type="tel" {...ctx.bind("nna-primary-celular")} />
{" "}<p className="field-help" id="nna-primary-celular-help">
</p>
{" "}<p className="field-error" id="nna-primary-celular-error" hidden={!ctx.errors["nna-primary-celular"]}>{ctx.errors["nna-primary-celular"]}</p>
{" "}</div>
{" "}</div>
{" "}<div data-channel="email" data-contact="nna-primary" hidden={!ctx.shown("nna-primary:email")}>
{" "}<div className="field">
{" "}<label htmlFor="nna-primary-email">{"Correo electrónico"}</label>
<input aria-describedby="nna-primary-email-help nna-primary-email-error" id="nna-primary-email" name="nna-primary-email" placeholder="nombre@ejemplo.cl" type="email" {...ctx.bind("nna-primary-email")} />
{" "}<p className="field-help" id="nna-primary-email-help">
</p>
{" "}<p className="field-error" id="nna-primary-email-error" hidden={!ctx.errors["nna-primary-email"]}>{ctx.errors["nna-primary-email"]}</p>
{" "}</div>
{" "}</div>
{" "}<div className="paired-fields" data-channel="social" data-contact="nna-primary" hidden={!ctx.shown("nna-primary:social")}>
{" "}<div className="field">
{" "}<label htmlFor="nna-primary-network">{"¿Qué red social utilizas?"}</label>
{" "}<div className="select-wrap">
{" "}<select aria-describedby="nna-primary-network-help nna-primary-network-error" id="nna-primary-network" name="nna-primary-network" {...ctx.bind("nna-primary-network")}>
{" "}<option value="">{"Selecciona una red social"}</option>
{" "}<option>{"Instagram"}</option>
{" "}<option>{"TikTok"}</option>
{" "}<option>{"Snapchat"}</option>
{" "}<option>{"Discord"}</option>
{" "}<option>{"Facebook"}</option>
{" "}<option>{"X (Twitter)"}</option>
{" "}<option value="otra">{"Otra red social"}</option>
{" "}</select>
{" "}</div>
{" "}<p className="field-help" id="nna-primary-network-help">
</p>
{" "}<p className="field-error" id="nna-primary-network-error" hidden={!ctx.errors["nna-primary-network"]}>{ctx.errors["nna-primary-network"]}</p>
{" "}</div>
{" "}<div className="field social-other" hidden={!ctx.shown("nna-primary-other-network-wrap")} id="nna-primary-other-network-wrap">
{" "}<label htmlFor="nna-primary-other-network">{"¿Cuál red social?"}</label>
<input id="nna-primary-other-network" name="nna-primary-other-network" placeholder="Escribe el nombre de la red" type="text" {...ctx.bind("nna-primary-other-network")} />
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="nna-primary-handle">{"Tu usuario o enlace de perfil"}</label>
<input aria-describedby="nna-primary-handle-help nna-primary-handle-error" id="nna-primary-handle" name="nna-primary-handle" placeholder="Ej.: @usuario" type="text" {...ctx.bind("nna-primary-handle")} />
{" "}<p className="field-help" id="nna-primary-handle-help">
</p>
{" "}<p className="field-error" id="nna-primary-handle-error" hidden={!ctx.errors["nna-primary-handle"]}>{ctx.errors["nna-primary-handle"]}</p>
{" "}</div>
{" "}</div>
{" "}<div className="info-tip" hidden={!ctx.shown("nna-unsafe-note")} id="nna-unsafe-note">
{" "}<svg aria-hidden="true" className="icon small">
{" "}<use href="#i-info">
</use>
</svg>
<span>{"Puedes dejar tu comunicación. Si no indicas un medio\n                        seguro, podría no ser posible contactarte. Esta\n                        alternativa debe validarse con la institución."}</span>
{" "}</div>
{" "}<div hidden={!ctx.shown("nna-contact-safety")} id="nna-contact-safety">
{" "}<div className="field">
{" "}<label htmlFor="nna-safe">{"¿Es seguro que te contactemos por ese medio?"}</label>
{" "}<div className="select-wrap">
{" "}<select aria-describedby="nna-safe-help nna-safe-error" id="nna-safe" name="nna-safe" {...ctx.bind("nna-safe")}>
{" "}<option value="">{"Prefiero no indicarlo"}</option>
{" "}<option value="yes">{"Sí"}</option>
{" "}<option value="unsure">{"\n                              No estoy seguro o segura\n                            "}</option>
{" "}<option value="no">{"\n                              No, necesito acordar otra forma\n                            "}</option>
{" "}</select>
{" "}</div>
{" "}<p className="field-help" id="nna-safe-help">{"\n                          Es opcional. Puedes decirnos si necesitas alguna\n                          precaución.\n                        "}</p>
{" "}<p className="field-error" id="nna-safe-error" hidden={!ctx.errors["nna-safe"]}>{ctx.errors["nna-safe"]}</p>
{" "}</div>
{" "}</div>
{" "}<label className="radio-pill add-contact">
<input id="nna-extra-contact" type="checkbox" {...ctx.check("nna-extra-contact")} />{" Quiero\n                      agregar otra forma de contacto (opcional)"}</label>
{" "}<div className="pane-fields secondary-contact" hidden={!ctx.shown("nna-secondary-panel")} id="nna-secondary-panel">
{" "}<div className="field">
{" "}<label htmlFor="nna-secondary-method">{"Otra forma de contacto"}</label>
{" "}<div className="select-wrap">
{" "}<select aria-describedby="nna-secondary-method-help nna-secondary-method-error" id="nna-secondary-method" name="nna-secondary-method" {...ctx.bind("nna-secondary-method")}>
{" "}<option value="">{"Selecciona una opción"}</option>
{" "}<option value="celular">{"Celular"}</option>
{" "}<option value="email">{"Correo electrónico"}</option>
{" "}<option value="social">{"Red social"}</option>
{" "}</select>
{" "}</div>
{" "}<p className="field-help" id="nna-secondary-method-help">
</p>
{" "}<p className="field-error" id="nna-secondary-method-error" hidden={!ctx.errors["nna-secondary-method"]}>{ctx.errors["nna-secondary-method"]}</p>
{" "}</div>
{" "}<div data-channel="celular" data-contact="nna-secondary" hidden={!ctx.shown("nna-secondary:celular")}>
{" "}<div className="field">
{" "}<label htmlFor="nna-secondary-celular">{"Número de celular"}</label>
<input aria-describedby="nna-secondary-celular-help nna-secondary-celular-error" autoComplete="tel" data-mask="mobile" id="nna-secondary-celular" inputMode="tel" maxLength={17} name="nna-secondary-celular" placeholder="+56 9 XXXX XXXX" type="tel" {...ctx.bind("nna-secondary-celular")} />
{" "}<p className="field-help" id="nna-secondary-celular-help">
</p>
{" "}<p className="field-error" id="nna-secondary-celular-error" hidden={!ctx.errors["nna-secondary-celular"]}>{ctx.errors["nna-secondary-celular"]}</p>
{" "}</div>
{" "}</div>
{" "}<div data-channel="email" data-contact="nna-secondary" hidden={!ctx.shown("nna-secondary:email")}>
{" "}<div className="field">
{" "}<label htmlFor="nna-secondary-email">{"Correo electrónico"}</label>
<input aria-describedby="nna-secondary-email-help nna-secondary-email-error" id="nna-secondary-email" name="nna-secondary-email" placeholder="nombre@ejemplo.cl" type="email" {...ctx.bind("nna-secondary-email")} />
{" "}<p className="field-help" id="nna-secondary-email-help">
</p>
{" "}<p className="field-error" id="nna-secondary-email-error" hidden={!ctx.errors["nna-secondary-email"]}>{ctx.errors["nna-secondary-email"]}</p>
{" "}</div>
{" "}</div>
{" "}<div className="paired-fields" data-channel="social" data-contact="nna-secondary" hidden={!ctx.shown("nna-secondary:social")}>
{" "}<div className="field">
{" "}<label htmlFor="nna-secondary-network">{"¿Qué red social utilizas?"}</label>
{" "}<div className="select-wrap">
{" "}<select aria-describedby="nna-secondary-network-help nna-secondary-network-error" id="nna-secondary-network" name="nna-secondary-network" {...ctx.bind("nna-secondary-network")}>
{" "}<option value="">{"\n                                Selecciona una red social\n                              "}</option>
{" "}<option>{"Instagram"}</option>
{" "}<option>{"TikTok"}</option>
{" "}<option>{"Snapchat"}</option>
{" "}<option>{"Discord"}</option>
{" "}<option>{"Facebook"}</option>
{" "}<option>{"X (Twitter)"}</option>
{" "}<option value="otra">{"Otra red social"}</option>
{" "}</select>
{" "}</div>
{" "}<p className="field-help" id="nna-secondary-network-help">
</p>
{" "}<p className="field-error" id="nna-secondary-network-error" hidden={!ctx.errors["nna-secondary-network"]}>{ctx.errors["nna-secondary-network"]}</p>
{" "}</div>
{" "}<div className="field social-other" hidden={!ctx.shown("nna-secondary-other-network-wrap")} id="nna-secondary-other-network-wrap">
{" "}<label htmlFor="nna-secondary-other-network">{"¿Cuál red social?"}</label>
<input id="nna-secondary-other-network" name="nna-secondary-other-network" placeholder="Escribe el nombre de la red" type="text" {...ctx.bind("nna-secondary-other-network")} />
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="nna-secondary-handle">{"Tu usuario o enlace de perfil"}</label>
<input aria-describedby="nna-secondary-handle-help nna-secondary-handle-error" id="nna-secondary-handle" name="nna-secondary-handle" placeholder="Ej.: @usuario" type="text" {...ctx.bind("nna-secondary-handle")} />
{" "}<p className="field-help" id="nna-secondary-handle-help">
</p>
{" "}<p className="field-error" id="nna-secondary-handle-error" hidden={!ctx.errors["nna-secondary-handle"]}>{ctx.errors["nna-secondary-handle"]}</p>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="nna-contact-instructions">{"¿Hay algo que debamos considerar al contactarte?\n                        "}<span className="muted-label">{"(opcional)"}</span>
</label>
<input aria-describedby="nna-contact-instructions-help nna-contact-instructions-error" id="nna-contact-instructions" name="nna-contact-instructions" placeholder="Ej.: un horario o una precaución importante" type="text" {...ctx.bind("nna-contact-instructions")} />
{" "}<p className="field-help" id="nna-contact-instructions-help">
</p>
{" "}<p className="field-error" id="nna-contact-instructions-error" hidden={!ctx.errors["nna-contact-instructions"]}>{ctx.errors["nna-contact-instructions"]}</p>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}<div className="step-actions">
{" "}<span>{"No se consulta ni se muestra información de Registro Civil\n                    en esta vista."}</span>
<button className="btn btn-primary" id="nna-next" type="button" onClick={() => ctx.action("nna-next")} hidden={ctx.full}>{"\n                    Continuar\n                    "}<svg aria-hidden="true" className="icon small">
{" "}<use href="#i-arrow">
</use>
{" "}</svg>
{" "}</button>
{" "}</div>
{" "}<details className="simulator-panel" id="demo-tools-nna">
{" "}<summary>{"\n                    Resultado SRCeI y herramientas de demostración\n                  "}</summary>
{" "}<div className="demo-tool-grid">
{" "}<div className="field">
{" "}<label htmlFor="demo-srcei-nna">{"Resultado SRCeI (simulado)"}</label>
{" "}<div className="select-wrap">
{" "}<select id="demo-srcei-nna" {...ctx.bind("demo-srcei-nna")}>
{" "}<option value="full">{"Antecedentes recuperados"}</option>
{" "}<option value="partial">{"\n                            Antecedentes parciales\n                          "}</option>
{" "}<option value="empty">{"\n                            Sin información concluyente\n                          "}</option>
{" "}<option value="unavailable">{"\n                            Servicio no disponible\n                          "}</option>
{" "}</select>
{" "}</div>
{" "}<p className="field-help">{"\n                        El resultado nunca revela datos registrales en el\n                        formulario ciudadano.\n                      "}</p>
{" "}</div>
{" "}<div className="demo-actions">
{" "}<button className="btn btn-secondary" id="demo-fill-nna" type="button" onClick={() => ctx.action("demo-fill-nna")}>{"\n                        Cargar datos de demostración"}</button>
<button className="btn btn-secondary" id="demo-internal-nna" type="button" onClick={() => ctx.action("demo-internal-nna")}>{"\n                        Ver recepción interna simulada\n                      "}</button>
{" "}</div>
{" "}</div>
{" "}<p className="demo-warning">{"\n                    La pantalla simula escenarios sin verificar existencia,\n                    identidad o titularidad de un RUN.\n                  "}</p>
{" "}<p className="field-help">{"\n                    Solo demostración. La consulta completa omite la\n                    identificación recuperada; la parcial pide lo faltante. Sin\n                    RUN no se consulta.\n                  "}</p>
{" "}</details>
{" "}</div>
{" "}</section>);
}
