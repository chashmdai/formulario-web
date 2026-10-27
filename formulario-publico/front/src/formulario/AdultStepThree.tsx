import {MediaAttachments} from '../ui/MediaAttachments';
import {MobileInput} from '../ui/MobileInput';
import {cleanEmail} from './contacts';
import {AttachmentStatus} from '../ui/AttachmentStatus';
import type { AdultContext } from "./AdultFlow";

/** Estructura y textos trasladados del mockup institucional. */
export function AdultStepThree({ctx}: {ctx:AdultContext}) {return (
<section aria-labelledby="titulo-informacion" className="paper" data-section-tone="communication" id="informacion" hidden={(!ctx.full && ctx.step !== 3) || !ctx.ready}>
{" "}<div className="paper-top">
{" "}<div className="paper-heading">
{" "}<span className="paper-num">{"03"}</span>
{" "}<div>
{" "}<h2 className="paper-title" id="titulo-informacion" tabIndex={-1}>{"\n                      Cuéntanos sobre tu comunicación\n                    "}</h2>
{" "}<p className="paper-caption">{"\n                      Algunas preguntas dependen del motivo; otras son comunes a\n                      todos los ingresos.\n                    "}</p>
{" "}</div>
{" "}</div>
{" "}<span className="status-label">{"Preguntas por validar"}</span>
{" "}</div>
{" "}<div className="paper-body">
{" "}<div className="form-grid">
{" "}<div className="form-pane">
{" "}<div className="pane-head">
{" "}<div className="pane-icon">
{" "}<svg className="icon">
<use href="#i-sliders">
</use>
</svg>
{" "}</div>
{" "}<div>
{" "}<h3>{"Información específica"}</h3>
{" "}<p>{"Depende del motivo seleccionado"}</p>
{" "}</div>
{" "}</div>
{" "}<div aria-label="Preguntas de consulta" className="pane-fields conditional" id="consulta" hidden={!ctx.shown("consulta")}>
{" "}<p className="section-intro">{"\n                        Según tu consulta, podrás solicitar una entrevista,\n                        antecedentes estadísticos no publicados o enviar una\n                        pregunta de orientación general.\n                      "}</p>
{" "}<div className="conditional pane-fields" data-tema="entrevista" hidden={!ctx.shown("tema:entrevista")}>
{" "}<div className="field">
{" "}<label htmlFor="entrevista-motivo">{"¿Qué te gustaría tratar en la entrevista?\n                            "}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("entrevista-motivo")}>{" *"}</span>
</label>
<textarea id="entrevista-motivo" name="entrevista-motivo" placeholder="Cuéntanos brevemente por qué solicitas la entrevista..." rows={4} {...ctx.bind("entrevista-motivo")} maxLength={ctx.maxLength("entrevista-motivo")}>
</textarea>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="entrevista-rol">{"¿Desde qué ámbito realizas la solicitud?\n                            "}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("entrevista-rol")}>{" *"}</span>
</label>
{" "}<div className="select-wrap">
{" "}<select id="entrevista-rol" name="entrevista-rol" {...ctx.bind("entrevista-rol")}>
{" "}<option value="">{"Selecciona una opción"}</option>
{" "}<option value="estudiante">{"Estudiante"}</option>
{" "}<option value="independiente">{"\n                                Persona independiente / a título personal\n                              "}</option>
{" "}<option value="prensa">{"\n                                Prensa o medio de comunicación\n                              "}</option>
{" "}<option value="otro">{"Otro"}</option>
{" "}</select>
{" "}</div>
{" "}</div>
{" "}<div className="field" hidden={!ctx.shown("entrevista-rol-otro-wrap")} id="entrevista-rol-otro-wrap">
{" "}<label htmlFor="entrevista-rol-otro">{"¿Desde qué otro ámbito solicitas la\n                            entrevista?"}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("entrevista-rol-otro")}>{" *"}</span>
</label>
<input id="entrevista-rol-otro" name="entrevista-rol-otro" placeholder="Indica a quién representas o tu vínculo" type="text" {...ctx.bind("entrevista-rol-otro")} maxLength={ctx.maxLength("entrevista-rol-otro")} />
{" "}</div>
{" "}<div className="field" hidden={!ctx.shown("entrevista-entidad-wrap")} id="entrevista-entidad-wrap">
{" "}<label htmlFor="entrevista-entidad">{"Institución o medio\n                            "}<span className="muted-label">{"(opcional)"}</span>
<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("entrevista-entidad")}>{" *"}</span>
</label>
<input id="entrevista-entidad" name="entrevista-entidad" placeholder="Nombre de la institución o medio" type="text" {...ctx.bind("entrevista-entidad")} maxLength={ctx.maxLength("entrevista-entidad")} />
{" "}</div>
{" "}<div className="info-tip">
{" "}<svg aria-hidden="true" className="icon small">
{" "}<use href="#i-info">
</use>
</svg>
<span>{"La solicitud de entrevista queda sujeta a\n                            evaluación institucional. El envío no agenda\n                            una entrevista ni confirma disponibilidad."}</span>
{" "}</div>
{" "}</div>
{" "}<div className="conditional pane-fields" data-tema="estadisticas" hidden={!ctx.shown("tema:estadisticas")}>
{" "}<p className="section-intro">{"\n                          Indica qué dato no encontraste en las fuentes\n                          públicas.\n                        "}</p>
{" "}<div className="field">
{" "}<label htmlFor="stats-descripcion">{"¿Qué dato o indicador necesitas?\n                            "}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("stats-descripcion")}>{" *"}</span>
</label>
<textarea id="stats-descripcion" name="stats-descripcion" placeholder="Ej.: cantidad de NNA, tema, unidad de medida y desglose requerido..." rows={4} {...ctx.bind("stats-descripcion")} maxLength={ctx.maxLength("stats-descripcion")}>
</textarea>
{" "}</div>
{" "}<div className="paired-fields">
{" "}<div className="field">
{" "}<label htmlFor="stats-periodo">{"Período de interés\n                              "}<span className="muted-label">{"(opcional)"}</span>
<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("stats-periodo")}>{" *"}</span>
</label>
<input id="stats-periodo" name="stats-periodo" placeholder="Ej.: 2023 a 2025" type="text" {...ctx.bind("stats-periodo")} maxLength={ctx.maxLength("stats-periodo")} />
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="stats-desglose">{"Desglose que necesitas\n                              "}<span className="muted-label">{"(opcional)"}</span>
<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("stats-desglose")}>{" *"}</span>
</label>
<input id="stats-desglose" name="stats-desglose" placeholder="Ej.: por región y tramo de edad" type="text" {...ctx.bind("stats-desglose")} maxLength={ctx.maxLength("stats-desglose")} />
{" "}</div>
{" "}</div>
{" "}</div>
{" "}<div className="conditional pane-fields" data-tema="general" hidden={!ctx.shown("tema:general")}>
{" "}<p className="section-intro">{"\n                          Si tu consulta no corresponde a otra alternativa,\n                          descríbela con tus propias palabras. La Defensoría\n                          evaluará el canal de respuesta que corresponda.\n                        "}</p>
{" "}<div className="field">
{" "}<label htmlFor="consulta-general-detalle">{"¿Qué necesitas consultar?\n                            "}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("consulta-general-detalle")}>{" *"}</span>
</label>
<textarea id="consulta-general-detalle" name="consulta-general-detalle" rows={4} maxLength={5000} placeholder="Cuéntanos qué información u orientación necesitas..." {...ctx.bind("consulta-general-detalle")}>
</textarea>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}<div aria-label="Preguntas de vulneración de derechos" className="pane-fields conditional" hidden={!ctx.shown("vulneracion")} id="vulneracion">
{" "}<p className="section-intro">{"\n                        Estas preguntas ayudan a describir una situación que\n                        podría afectar los derechos de niños, niñas o\n                        adolescentes.\n                      "}</p>
{" "}<fieldset className="fieldset">
{" "}<legend>{"\n                          ¿Hay algún niño, niña o adolescente en riesgo\n                          inminente?\n                        "}</legend>
{" "}<div className="radio-row">
{" "}<label className="radio-pill">
<input id="riesgo-si" name="riesgo" type="radio" {...ctx.radio("riesgo","si")} />{"\n                            Sí"}</label>
{" "}<label className="radio-pill">
<input id="riesgo-no" name="riesgo" type="radio" {...ctx.radio("riesgo","no")} />{"\n                            No"}</label>
{" "}</div>
{" "}</fieldset>
{" "}<div className="urgent-tip" hidden={!ctx.shown("urgencia")} id="urgencia" role="status">
{" "}<svg className="icon">
<use href="#i-alert">
</use>
</svg>
{" "}<div>
{" "}<strong>{"Si hay peligro inmediato"}</strong>
{" "}<p>{"\n                            Si existe peligro inmediato, contacta a los\n                            servicios de emergencia pertinentes. Este formulario\n                            no sustituye la atención urgente.\n                          "}</p>
{" "}</div>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="relacion">{"¿Cuál es tu relación con el niño, niña o\n                          adolescente?"}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("relacion")}>{" *"}</span>
</label>
{" "}<div className="select-wrap">
{" "}<select id="relacion" name="relacion" {...ctx.bind("relacion")}>
{" "}<option value="">{"Selecciona una opción"}</option>
{" "}<option value="madre">{"Madre"}</option>
{" "}<option value="padre">{"Padre"}</option>
{" "}<option value="cuidador">{"\n                              Cuidador o cuidadora\n                            "}</option>
{" "}<option value="otro-familiar">{"Otro familiar"}</option>
{" "}<option value="docente">{"\n                              Docente o profesional educativo\n                            "}</option>
{" "}<option value="salud">{"Profesional de salud"}</option>
{" "}<option value="testigo">{"Persona testigo"}</option>
{" "}<option value="organizacion">{"\n                              Representante de una organización\n                            "}</option>
{" "}<option value="otro">{"\n                              Otra persona con vínculo con el NNA\n                            "}</option>
{" "}</select>
{" "}</div>
{" "}</div>
{" "}<div className="field" hidden={!ctx.shown("relacion-familiar-wrap")} id="relacion-familiar-wrap">
{" "}<label htmlFor="relacion-familiar">{"¿Qué parentesco tienes con el NNA?"}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("relacion-familiar")}>{" *"}</span>
</label>
{" "}<div className="select-wrap">
{" "}<select id="relacion-familiar" name="relacion-familiar" {...ctx.bind("relacion-familiar")}>
{" "}<option value="">{"Selecciona el parentesco"}</option>
{" "}<option>{"Abuela o abuelo"}</option>
{" "}<option>{"Tía o tío"}</option>
{" "}<option>{"Hermana o hermano"}</option>
{" "}<option>{"Prima o primo"}</option>
{" "}<option>{"Madrastra o padrastro"}</option>
{" "}<option>{"Madrina o padrino"}</option>
{" "}<option>{"Otro parentesco familiar"}</option>
{" "}</select>
{" "}</div>
{" "}</div>
{" "}<div className="pane-fields location-block">
{" "}<p className="micro-heading">{"\n                          Lugar de los hechos\n                          "}<span className="field-help">{"Selecciona la región y la comuna."}</span>
{" "}</p>
{" "}<div className="paired-fields">
{" "}<div className="field">
{" "}<label htmlFor="hechos-region">{"Región donde ocurre u ocurrió"}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("hechos-region")}>{" *"}</span>
</label>
{" "}<div className="select-wrap">
{" "}<select id="hechos-region" name="hechos-region" {...ctx.bind("hechos-region")}>
{" "}<option value="">{"Selecciona una región"}</option>
{" "}<option value="15">{"Arica y Parinacota"}</option>
{" "}<option value="01">{"Tarapacá"}</option>
{" "}<option value="02">{"Antofagasta"}</option>
{" "}<option value="03">{"Atacama"}</option>
{" "}<option value="04">{"Coquimbo"}</option>
{" "}<option value="05">{"Valparaíso"}</option>
{" "}<option value="13">{"\n                                  Metropolitana de Santiago\n                                "}</option>
{" "}<option value="06">{"\n                                  Libertador General Bernardo O'Higgins\n                                "}</option>
{" "}<option value="07">{"Maule"}</option>
{" "}<option value="16">{"Ñuble"}</option>
{" "}<option value="08">{"Biobío"}</option>
{" "}<option value="09">{"La Araucanía"}</option>
{" "}<option value="14">{"Los Ríos"}</option>
{" "}<option value="10">{"Los Lagos"}</option>
{" "}<option value="11">{"\n                                  Aysén del General Carlos Ibáñez del Campo\n                                "}</option>
{" "}<option value="12">{"\n                                  Magallanes y de la Antártica Chilena\n                                "}</option>
{" "}</select>
{" "}</div>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="hechos-commune">{"Comuna de los hechos"}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("hechos-commune")}>{" *"}</span>
</label>
{" "}<div className="select-wrap">
{" "}<select id="hechos-commune" name="hechos-commune" {...ctx.bind("hechos-commune")}>
<option value="">{ctx.value("hechos-region") ? "Selecciona una comuna" : "Selecciona primero una región"}</option>{ctx.communes("hechos").map(name => <option key={name}>{name}</option>)}</select>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="lugar">{"Dirección, lugar o referencia\n                            "}<span className="muted-label">{"(opcional)"}</span>
<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("lugar")}>{" *"}</span>
</label>
<input id="lugar" name="lugar" placeholder="Ej.: sector, establecimiento, dirección aproximada" type="text" {...ctx.bind("lugar")} maxLength={ctx.maxLength("lugar")} />
{" "}</div>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="momento">{"¿Cuándo ocurrió o ocurre la situación?"}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("momento")}>{" *"}</span>
</label>
{" "}<div className="select-wrap">
{" "}<select id="momento" name="momento" {...ctx.bind("momento")}>
{" "}<option value="">{"Selecciona una opción"}</option>
{" "}<option>{"Está ocurriendo actualmente"}</option>
{" "}<option>{"Ocurrió recientemente"}</option>
{" "}<option>{"Ocurrió tiempo atrás"}</option>
{" "}<option>{"No lo sé"}</option>
{" "}</select>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}<div aria-label="Preguntas de Promoción y Difusión" className="pane-fields conditional" hidden={!ctx.shown("promocion")} id="promocion">
{" "}<p className="section-intro">{"\n                        Cuéntanos qué tipo de actividad te interesa y para quién\n                        estaría dirigida.\n                      "}</p>
{" "}<fieldset className="fieldset">
{" "}<legend>{"\n                          ¿Representas a una institución, persona jurídica u\n                          ONG?\n                        "}</legend>
{" "}<div className="radio-row">
{" "}<label className="radio-pill">
<input name="organizacion" type="radio" {...ctx.radio("organizacion","si")} />{"\n                            Sí"}</label>
{" "}<label className="radio-pill">
<input name="organizacion" type="radio" {...ctx.radio("organizacion","no")} />{"\n                            No"}</label>
{" "}</div>
{" "}</fieldset>
{" "}<div className="conditional" hidden={!ctx.shown("detalle-organizacion")} id="detalle-organizacion">
{" "}<div className="field">
{" "}<label htmlFor="tipo-organizacion">{"Tipo de entidad"}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("tipo-organizacion")}>{" *"}</span>
</label>
{" "}<div className="select-wrap">
{" "}<select id="tipo-organizacion" name="tipo-organizacion" {...ctx.bind("tipo-organizacion")}>
{" "}<option value="">{"Selecciona un tipo"}</option>
{" "}<option>{"ONG o fundación"}</option>
{" "}<option>{"Establecimiento educacional"}</option>
{" "}<option>{"Servicio u organismo público"}</option>
{" "}<option>{"Empresa u otra persona jurídica"}</option>
{" "}<option>{"Otra organización"}</option>
{" "}</select>
{" "}</div>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="nombre-organizacion">{"Nombre de la organización"}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("nombre-organizacion")}>{" *"}</span>
</label>
<input id="nombre-organizacion" name="nombre-organizacion" placeholder="Razón social o nombre de la entidad" type="text" {...ctx.bind("nombre-organizacion")} maxLength={ctx.maxLength("nombre-organizacion")} />
{" "}</div>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="actividad">{"¿Qué actividad te interesa?"}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("actividad")}>{" *"}</span>
</label>
{" "}<div className="select-wrap">
{" "}<select id="actividad" name="actividad" {...ctx.bind("actividad")}>
{" "}<option value="">{"Selecciona una actividad"}</option>
{" "}<option>{"Charla"}</option>
{" "}<option>{"Taller"}</option>
{" "}<option>{"Capacitación"}</option>
{" "}<option>{"Otra actividad"}</option>
{" "}</select>
{" "}</div>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="publico">{"¿A quién estaría dirigida?"}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("publico")}>{" *"}</span>
</label>
<input id="publico" name="publico" placeholder="Ej.: comunidad educativa, familias, docentes..." type="text" {...ctx.bind("publico")} maxLength={ctx.maxLength("publico")} />
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="fecha-actividad">{"Fecha tentativa\n                          "}<span className="muted-label">{"(si la tienes)"}</span>
<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("fecha-actividad")}>{" *"}</span>
</label>
<input id="fecha-actividad" name="fecha-actividad" type="date" {...ctx.bind("fecha-actividad")} maxLength={ctx.maxLength("fecha-actividad")} />
{" "}</div>
{" "}<div className="paired-fields">
{" "}<div className="field">
{" "}<label htmlFor="promocion-region">{"Región donde se realizaría la actividad\n                            "}<span className="muted-label">{"(opcional)"}</span>
<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("promocion-region")}>{" *"}</span>
</label>
{" "}<div className="select-wrap">
{" "}<select id="promocion-region" name="promocion-region" {...ctx.bind("promocion-region")}>
{" "}<option value="">{"Selecciona una región"}</option>
{" "}<option value="15">{"Arica y Parinacota"}</option>
{" "}<option value="01">{"Tarapacá"}</option>
{" "}<option value="02">{"Antofagasta"}</option>
{" "}<option value="03">{"Atacama"}</option>
{" "}<option value="04">{"Coquimbo"}</option>
{" "}<option value="05">{"Valparaíso"}</option>
{" "}<option value="13">{"\n                                Metropolitana de Santiago\n                              "}</option>
{" "}<option value="06">{"\n                                Libertador General Bernardo O'Higgins\n                              "}</option>
{" "}<option value="07">{"Maule"}</option>
{" "}<option value="16">{"Ñuble"}</option>
{" "}<option value="08">{"Biobío"}</option>
{" "}<option value="09">{"La Araucanía"}</option>
{" "}<option value="14">{"Los Ríos"}</option>
{" "}<option value="10">{"Los Lagos"}</option>
{" "}<option value="11">{"\n                                Aysén del General Carlos Ibáñez del Campo\n                              "}</option>
{" "}<option value="12">{"\n                                Magallanes y de la Antártica Chilena\n                              "}</option>
{" "}</select>
{" "}</div>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="promocion-commune">{"Comuna de la actividad\n                            "}<span className="muted-label">{"(opcional)"}</span>
<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("promocion-commune")}>{" *"}</span>
</label>
{" "}<div className="select-wrap">
{" "}<select id="promocion-commune" name="promocion-commune" {...ctx.bind("promocion-commune")}>
<option value="">{ctx.value("promocion-region") ? "Selecciona una comuna" : "Selecciona primero una región"}</option>{ctx.communes("promocion").map(name => <option key={name}>{name}</option>)}</select>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}<div className="form-pane">
{" "}<div className="pane-head">
{" "}<div className="pane-icon berry">
{" "}<svg className="icon">
<use href="#i-paper">
</use>
</svg>
{" "}</div>
{" "}<div>
{" "}<h3>{"Datos adicionales comunes"}</h3>
{" "}<p>{"Disponibles para todos los motivos"}</p>
{" "}</div>
{" "}</div>
{" "}<div className="pane-fields">
{" "}<div className="field">
{" "}<label htmlFor="relato">{"Cuéntanos con tus propias palabras"}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("relato")}>{" *"}</span>
</label>
{" "}<p className="field-help" id="relato-help">{"\n                          Para vulneraciones y actividades de Promoción, incluye\n                          una descripción que permita comprender tu\n                          comunicación.\n                        "}</p>
{" "}<textarea id="relato" maxLength={5000} name="relato" placeholder="Describe qué sucede, qué necesitas o qué te gustaría consultar..." rows={6} {...ctx.bind("relato")}>
</textarea>
{" "}<div className="char-counter" id="contador">{ctx.value("relato").length} caracteres</div>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="email">{"Correo electrónico de contacto\n                          "}<span className="muted-label">{"(opcional)"}</span>
<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("email")}>{" *"}</span>
</label>
<input autoComplete="email" id="email" inputMode="email" name="email" placeholder="nombre@ejemplo.cl" type="email" {...ctx.bind("email")} onBlur={e=>ctx.setValue("email",cleanEmail(e.target.value))} aria-describedby="email-help email-error" maxLength={ctx.maxLength("email")} />
{" "}<div className="field-help" id="email-help">{"\n                          Podría utilizarse para dar respuesta, según el\n                          procedimiento aprobado.\n                        "}</div>
<p className="field-error" id="email-error" hidden={!ctx.errors.email}>{ctx.errors.email}</p>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="telefono">{"Celular de contacto\n                          "}<span className="muted-label">{"(opcional)"}</span>
<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("telefono")}>{" *"}</span>
</label>
<MobileInput id="telefono" name="telefono" value={ctx.value("telefono")} onValueChange={v=>ctx.setValue("telefono",v)} disabled={ctx.bind("telefono").disabled} required={ctx.required("telefono")} aria-invalid={!!ctx.errors.telefono} aria-describedby="telefono-help telefono-error" aria-errormessage={ctx.errors.telefono?"telefono-error":undefined} />
{" "}<p className="field-help" id="telefono-help">Escribe los ocho dígitos restantes. Puedes pegar el número completo con su prefijo.</p><p className="field-error" id="telefono-error" hidden={!ctx.errors.telefono}>{ctx.errors.telefono}</p>
{" "}</div>
{" "}<div className="field">
{" "}{ctx.media?<MediaAttachments control={ctx.media} id="archivos-elegidos"/>:<><label htmlFor="adjuntos">{"Documentos o antecedentes\n                          "}<span className="muted-label">{"(opcional)"}</span>
</label>
{" "}<div className="upload-box">
{" "}<input className="sr-only" id="adjuntos" name="adjuntos" type="file" multiple accept=".pdf,.png,.jpg,.jpeg,.webp,.txt" disabled={ctx.busy || ctx.uploading} onChange={ctx.onFiles} />
<label className="upload-target" htmlFor="adjuntos">
<span className="upload-icon">
<svg className="icon">
{" "}<use href="#i-upload">
</use>
</svg>
</span>
<span>
<strong>{"Seleccionar archivos"}</strong>
<small>{"PDF, PNG, JPG, WebP o TXT. Máx. 5 archivos, 10 MB por archivo y 25 MB en total."}</small>
</span>
</label>
{" "}</div>
{" "}<AttachmentStatus id="archivos-elegidos" files={ctx.attachments} pending={ctx.uploading} error={ctx.errors.adjuntos} feedback={ctx.fileFeedback} /></>}
{" "}</div>
{" "}<div className="manual-location">
{" "}<div className="pane-head">
{" "}<div>
{" "}<h3>{"Ubicación informada por ti"}</h3>
{" "}<p>{"Indica dónde resides para orientar tu comunicación."}</p>
{" "}</div>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="adult-region">{"Región"}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("adult-region")}>{" *"}</span>
</label>
{" "}<div className="select-wrap">
{" "}<select aria-describedby="adult-region-help adult-region-error" id="adult-region" name="adult-region" {...ctx.bind("adult-region")}>
{" "}<option value="">{"Selecciona una región"}</option>
{" "}<option value="15">{"Arica y Parinacota"}</option>
{" "}<option value="01">{"Tarapacá"}</option>
{" "}<option value="02">{"Antofagasta"}</option>
{" "}<option value="03">{"Atacama"}</option>
{" "}<option value="04">{"Coquimbo"}</option>
{" "}<option value="05">{"Valparaíso"}</option>
{" "}<option value="13">{"\n                                Metropolitana de Santiago\n                              "}</option>
{" "}<option value="06">{"\n                                Libertador General Bernardo O'Higgins\n                              "}</option>
{" "}<option value="07">{"Maule"}</option>
{" "}<option value="16">{"Ñuble"}</option>
{" "}<option value="08">{"Biobío"}</option>
{" "}<option value="09">{"La Araucanía"}</option>
{" "}<option value="14">{"Los Ríos"}</option>
{" "}<option value="10">{"Los Lagos"}</option>
{" "}<option value="11">{"\n                                Aysén del General Carlos Ibáñez del Campo\n                              "}</option>
{" "}<option value="12">{"\n                                Magallanes y de la Antártica Chilena\n                              "}</option>
{" "}</select>
{" "}</div>
{" "}<p className="field-help" id="adult-region-help">{"\n                            Selecciona la región donde resides.\n                          "}</p>
{" "}<p className="field-error" id="adult-region-error" hidden={!ctx.errors["adult-region"]}>{ctx.errors["adult-region"]}</p>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="adult-commune">{"Comuna\n                            "}<span className="muted-label">{"(opcional)"}</span>
<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("adult-commune")}>{" *"}</span>
</label>
{" "}<div className="select-wrap">
{" "}<select aria-describedby="adult-commune-help adult-commune-error" id="adult-commune" name="adult-commune" {...ctx.bind("adult-commune")}>
<option value="">{ctx.value("adult-region") ? "Selecciona una comuna" : "Selecciona primero una región"}</option>{ctx.communes("adult").map(name => <option key={name}>{name}</option>)}</select>
{" "}</div>
{" "}<p className="field-help" id="adult-commune-help">{"\n                            Comunas disponibles según la región seleccionada.\n                            Campo opcional.\n                          "}</p>
{" "}<p className="field-error" id="adult-commune-error" hidden={!ctx.errors["adult-commune"]}>{ctx.errors["adult-commune"]}</p>
{" "}</div>
{" "}<div className="field">
{" "}<label htmlFor="adult-address">{"Dirección\n                            "}<span className="muted-label">{"(si corresponde)"}</span>
<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required("adult-address")}>{" *"}</span>
</label>
<input aria-describedby="adult-address-help adult-address-error" id="adult-address" name="adult-address" placeholder="Calle, número o una referencia" type="text" {...ctx.bind("adult-address")} maxLength={ctx.maxLength("adult-address")} />
{" "}<p className="field-help" id="adult-address-help">
</p>
{" "}<p className="field-error" id="adult-address-error" hidden={!ctx.errors["adult-address"]}>{ctx.errors["adult-address"]}</p>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}<div className="vuln-extensions" hidden={!ctx.shown("vulneracion-anexos")} id="vulneracion-anexos">
{" "}<div className="nna-subsection">
{" "}<div className="subsection-header">
{" "}<div>
{" "}<h3>{"Datos de los NNA involucrados"}</h3>
{" "}<p className="section-intro">{"\n                          Agrega solo los antecedentes que conozcas. No\n                          necesitas conocer el RUN para informar la situación.\n                        "}</p>
{" "}</div>
{" "}<span className="count-pill" id="afectados-count">{ctx.count("AFECTADO_NNA")} NNA</span>
{" "}</div>
{" "}<div className="repeat-list" id="afectados-list">{ctx.people("AFECTADO_NNA")}</div>
{" "}<button className="btn btn-secondary add-item" id="agregar-afectado" type="button" onClick={() => ctx.action("agregar-afectado")}>{"\n                      + Agregar otro NNA\n                    "}</button>
{" "}</div>
{" "}<div className="nna-subsection">
{" "}<div className="subsection-header">
{" "}<div>
{" "}<h3>{"\n                          Personas o instituciones señaladas en los hechos\n                        "}</h3>
{" "}<p className="section-intro">{"\n                          Registra solo lo que sepas, sin presumir\n                          responsabilidades.\n                        "}</p>
{" "}</div>
{" "}</div>
{" "}<label className="ack-checkbox subject-toggle">
<input id="identifica-responsables" type="checkbox" {...ctx.check("identifica-responsables")} />
<span>{"Puedo identificar a una persona o institución\n                        posiblemente vinculada con los hechos que informo."}</span>
</label>
{" "}<div hidden={!ctx.shown("responsables-contenido")} id="responsables-contenido">
{" "}<div className="repeat-list" id="responsables-list">{ctx.people("PERSONA_SENALADA")}</div>
{" "}<button className="btn btn-secondary add-item" id="agregar-responsable" type="button" onClick={() => ctx.action("agregar-responsable")}>{"\n                        + Agregar otra persona o institución\n                      "}</button>
{" "}</div>
{" "}</div>
{" "}</div>
{" "}<section aria-labelledby="related-adults-title" className="related-section" hidden={!ctx.shown("related-adults-section")} id="related-adults-section">
{" "}<div className="pane-head">
{" "}<span className="pane-icon berry">
<svg aria-hidden="true" className="icon">
{" "}<use href="#i-message">
</use>
</svg>
</span>
{" "}<div>
{" "}<h3 id="related-adults-title">{"\n                        Otras personas adultas relacionadas\n                      "}</h3>
{" "}<p>{"\n                        Opcional. Agrega solo a quienes sean relevantes para tu\n                        comunicación.\n                      "}</p>
{" "}</div>
{" "}<span className="count-pill" id="related-adults-count">{ctx.count("ADULTO_RELACIONADO")} personas</span>
{" "}</div>
{" "}<p className="field-help">{"\n                    Puede ser un familiar, representante, testigo o contacto. No\n                    cambia quién presenta esta comunicación. Si no conoces su\n                    RUN, puedes describir a la persona sin ingresarlo.\n                  "}</p>
{" "}<div className="repeat-list" id="related-adults-list">{ctx.people("ADULTO_RELACIONADO")}</div>
{" "}<button className="add-repeat" id="add-related-adult" type="button" onClick={() => ctx.action("add-related-adult")}>
{" "}<span aria-hidden="true">{"+"}</span>{" Agregar persona adulta\n                  "}</button>
{" "}</section>
{" "}<div className="privacy-note">
<svg className="icon small">
<use href="#i-lock" />
</svg>
<span>
<strong>Privacidad y tratamiento de datos:</strong> Tu información se utilizará para gestionar tu comunicación conforme al aviso que revisaste antes de ingresar.</span>
</div>
{" "}</div>
{" "}</section>
);}
