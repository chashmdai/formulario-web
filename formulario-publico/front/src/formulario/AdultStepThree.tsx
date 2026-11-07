import type { ReactNode } from 'react';
import {ReadableSelect} from '../ui/ReadableSelect';
import {MediaAttachments} from '../ui/MediaAttachments';
import {MobileInput} from '../ui/MobileInput';
import {EmailInput} from '../ui/EmailInput';
import {AttachmentStatus} from '../ui/AttachmentStatus';
import {requestDate} from './adultRules';
import {RegionOptions} from './RegionOptions';
import type { AdultContext } from "./AdultFlow";

const Icon = ({name, small = false}: {name: string; small?: boolean}) => <svg aria-hidden="true" className={small ? "icon small" : "icon"}><use href={`#i-${name}`} /></svg>;
const Optional = ({text = "(opcional)"}: {text?: string}) => <span className="muted-label"> {text}</span>;

/** Paso 3: detalle según motivo y datos comunes. Identificadores, nombres y valores enviados se conservan. */
export function AdultStepThree({ctx}: {ctx:AdultContext}) {
  const groupLabel = ctx.groupCount + " " + (ctx.groupCount === 1 ? "grupo" : "grupos");
  const mode = ctx.value("afectados-modalidad");
  const label = (id: string, children: ReactNode) => <label htmlFor={id}>{children}<span className="required-mark conditional-required" aria-hidden="true" hidden={!ctx.required(id)}> *</span></label>;
  const communeOptions = (prefix: string) => <><option value="">{ctx.value(`${prefix}-region`) ? "Selecciona una comuna" : "Selecciona primero una región"}</option>{ctx.communes(prefix).map(name => <option key={name}>{name}</option>)}</>;
  const story = ctx.value("relato").length, storyMax = ctx.maxLength("relato");
  return <section aria-labelledby="titulo-informacion" className="paper" data-section-tone="communication" id="informacion" hidden={(!ctx.full && ctx.step !== 3) || !ctx.ready}>
    <div className="paper-top">
      <div className="paper-heading">
        <span className="paper-num">03</span>
        <div>
          <h2 className="paper-title" id="titulo-informacion" tabIndex={-1}>Cuéntanos sobre tu comunicación</h2>
          <p className="paper-caption">Primero los detalles de tu motivo; luego tu relato y cómo contactarte.</p>
        </div>
      </div>
    </div>
    <div className="paper-body">
      <div className="form-grid">
        <div className="form-pane">
          <div className="pane-head">
            <div className="pane-icon"><Icon name="sliders" /></div>
            <div><h3>Detalles de tu motivo</h3><p>{ctx.reasonTitle || "Según el motivo seleccionado"}</p></div>
          </div>

          <div aria-label="Preguntas de consulta" className="pane-fields conditional" id="consulta" hidden={!ctx.shown("consulta")}>
            <div className="conditional pane-fields" data-tema="entrevista" hidden={!ctx.shown("tema:entrevista")}>
              <div className="field">
                {label("entrevista-motivo", <>¿Qué te gustaría tratar en la entrevista?</>)}
                <textarea id="entrevista-motivo" name="entrevista-motivo" placeholder="Cuéntanos brevemente el motivo" rows={4} {...ctx.bind("entrevista-motivo")} maxLength={ctx.maxLength("entrevista-motivo")} />
              </div>
              <div className="field">
                {label("entrevista-rol", <>¿Desde qué ámbito realizas la solicitud?</>)}
                <div className="select-wrap">
                  <ReadableSelect id="entrevista-rol" name="entrevista-rol" {...ctx.bind("entrevista-rol")}>
                    <option value="">Selecciona una opción</option>
                    <option value="estudiante">Estudiante</option>
                    <option value="independiente">Persona independiente / a título personal</option>
                    <option value="prensa">Prensa o medio de comunicación</option>
                    <option value="otro">Otro</option>
                  </ReadableSelect>
                </div>
              </div>
              <div className="field conditional" hidden={!ctx.shown("entrevista-rol-otro-wrap")} id="entrevista-rol-otro-wrap">
                {label("entrevista-rol-otro", <>¿Desde qué otro ámbito solicitas la entrevista?</>)}
                <input id="entrevista-rol-otro" name="entrevista-rol-otro" placeholder="A quién representas o tu vínculo" type="text" {...ctx.bind("entrevista-rol-otro")} maxLength={ctx.maxLength("entrevista-rol-otro")} />
              </div>
              <div className="field conditional" hidden={!ctx.shown("entrevista-entidad-wrap")} id="entrevista-entidad-wrap">
                {label("entrevista-entidad", <>Institución o medio<Optional /></>)}
                <input id="entrevista-entidad" name="entrevista-entidad" placeholder="Nombre de la institución o medio" type="text" {...ctx.bind("entrevista-entidad")} maxLength={ctx.maxLength("entrevista-entidad")} />
              </div>
              <div className="info-tip"><Icon name="info" small /><span>Enviar la solicitud no agenda la entrevista: la Defensoría la evaluará.</span></div>
            </div>

            <div className="conditional pane-fields" data-tema="estadisticas" hidden={!ctx.shown("tema:estadisticas")}>
              <div className="field">
                {label("stats-descripcion", <>¿Qué información necesitas?</>)}
                <textarea id="stats-descripcion" name="stats-descripcion" placeholder="Ej.: tema, publicación, período o dato específico que no encontraste" rows={4} {...ctx.bind("stats-descripcion")} maxLength={ctx.maxLength("stats-descripcion")} />
              </div>
              <div className="paired-fields">
                <div className="field">
                  {label("stats-periodo", <>Período de interés<Optional /></>)}
                  <input id="stats-periodo" name="stats-periodo" placeholder="Ej.: 2023 a 2025" type="text" {...ctx.bind("stats-periodo")} maxLength={ctx.maxLength("stats-periodo")} />
                </div>
                <div className="field">
                  {label("stats-desglose", <>Desglose que necesitas<Optional /></>)}
                  <input id="stats-desglose" name="stats-desglose" placeholder="Ej.: por región y tramo de edad" type="text" {...ctx.bind("stats-desglose")} maxLength={ctx.maxLength("stats-desglose")} />
                </div>
              </div>
            </div>

            <div className="conditional pane-fields" data-tema="general" hidden={!ctx.shown("tema:general")}>
              <div className="field">
                {label("consulta-general-detalle", <>¿Qué necesitas consultar?</>)}
                <textarea id="consulta-general-detalle" name="consulta-general-detalle" rows={4} maxLength={5000} placeholder="Cuéntanos qué información u orientación necesitas" aria-describedby="consulta-general-help" {...ctx.bind("consulta-general-detalle")} />
                <p className="field-help" id="consulta-general-help">La Defensoría definirá el canal de respuesta.</p>
              </div>
            </div>
          </div>

          <div aria-label="Preguntas de vulneración de derechos" className="pane-fields conditional" hidden={!ctx.shown("vulneracion")} id="vulneracion">
            <fieldset className="fieldset">
              <legend>¿Hay algún niño, niña o adolescente en riesgo inminente?</legend>
              <div className="radio-row">
                <label className="radio-pill"><input id="riesgo-si" name="riesgo" type="radio" {...ctx.radio("riesgo","si")} /> Sí</label>
                <label className="radio-pill"><input id="riesgo-no" name="riesgo" type="radio" {...ctx.radio("riesgo","no")} /> No</label>
              </div>
            </fieldset>
            <div className="urgent-tip conditional" hidden={!ctx.shown("urgencia")} id="urgencia" role="status">
              <Icon name="alert" />
              <div><strong>Si hay peligro inmediato</strong><p>Contacta de inmediato a los servicios de emergencia. Este formulario no reemplaza la atención urgente.</p></div>
            </div>
            <div className="field">
              {label("relacion", <>¿Cuál es tu relación con el niño, niña o adolescente?</>)}
              <div className="select-wrap">
                <ReadableSelect id="relacion" name="relacion" {...ctx.bind("relacion")}>
                  <option value="">Selecciona una opción</option>
                  <option value="madre">Madre</option>
                  <option value="padre">Padre</option>
                  <option value="cuidador">Cuidador o cuidadora</option>
                  <option value="otro-familiar">Otro familiar</option>
                  <option value="docente">Docente o profesional educativo</option>
                  <option value="salud">Profesional de salud</option>
                  <option value="testigo">Persona testigo</option>
                  <option value="organizacion">Representante de una organización</option>
                  <option value="otro">Otra persona con vínculo con el NNA</option>
                </ReadableSelect>
              </div>
            </div>
            <div className="field conditional" hidden={!ctx.shown("relacion-familiar-wrap")} id="relacion-familiar-wrap">
              {label("relacion-familiar", <>¿Qué parentesco tienes con el NNA?</>)}
              <div className="select-wrap">
                <ReadableSelect id="relacion-familiar" name="relacion-familiar" {...ctx.bind("relacion-familiar")}>
                  <option value="">Selecciona el parentesco</option>
                  {["Abuela o abuelo", "Tía o tío", "Hermana o hermano", "Prima o primo", "Madrastra o padrastro", "Madrina o padrino", "Otro parentesco familiar"].map(option => <option key={option}>{option}</option>)}
                </ReadableSelect>
              </div>
            </div>
            <div className="pane-fields location-block">
              <p className="micro-heading">Lugar de los hechos</p>
              <div className="paired-fields">
                <div className="field">
                  {label("hechos-region", <>Región donde ocurre u ocurrió</>)}
                  <div className="select-wrap"><ReadableSelect id="hechos-region" name="hechos-region" {...ctx.bind("hechos-region")}><RegionOptions /></ReadableSelect></div>
                </div>
                <div className="field">
                  {label("hechos-commune", <>Comuna de los hechos<Optional /></>)}
                  <div className="select-wrap"><ReadableSelect id="hechos-commune" name="hechos-commune" {...ctx.bind("hechos-commune")}>{communeOptions("hechos")}</ReadableSelect></div>
                </div>
              </div>
              <div className="field">
                {label("lugar", <>Dirección, lugar o referencia<Optional /></>)}
                <input id="lugar" name="lugar" placeholder="Ej.: sector, establecimiento o dirección aproximada" type="text" {...ctx.bind("lugar")} maxLength={ctx.maxLength("lugar")} />
              </div>
            </div>
          </div>

          <div aria-label="Preguntas de Promoción y Difusión" className="pane-fields conditional" hidden={!ctx.shown("promocion")} id="promocion">
            <fieldset className="fieldset">
              <legend>¿Representas a una institución, persona jurídica u ONG?</legend>
              <div className="radio-row">
                <label className="radio-pill"><input name="organizacion" type="radio" {...ctx.radio("organizacion","si")} /> Sí</label>
                <label className="radio-pill"><input name="organizacion" type="radio" {...ctx.radio("organizacion","no")} /> No</label>
              </div>
            </fieldset>
            <div className="conditional" hidden={!ctx.shown("detalle-organizacion")} id="detalle-organizacion">
              <div className="field">
                {label("tipo-organizacion", <>Tipo de entidad</>)}
                <div className="select-wrap">
                  <ReadableSelect id="tipo-organizacion" name="tipo-organizacion" {...ctx.bind("tipo-organizacion")}>
                    <option value="">Selecciona un tipo</option>
                    {["ONG o fundación", "Establecimiento educacional", "Servicio u organismo público", "Empresa u otra persona jurídica", "Otra organización"].map(option => <option key={option}>{option}</option>)}
                  </ReadableSelect>
                </div>
              </div>
              <div className="field">
                {label("nombre-organizacion", <>Nombre de la organización</>)}
                <input id="nombre-organizacion" name="nombre-organizacion" placeholder="Razón social o nombre de la entidad" type="text" {...ctx.bind("nombre-organizacion")} maxLength={ctx.maxLength("nombre-organizacion")} />
              </div>
            </div>
            <div className="field">
              {label("actividad", <>¿Qué actividad te interesa?</>)}
              <div className="select-wrap">
                <ReadableSelect id="actividad" name="actividad" {...ctx.bind("actividad")}>
                  <option value="">Selecciona una actividad</option>
                  {["Charla", "Taller", "Capacitación", "Otra actividad"].map(option => <option key={option}>{option}</option>)}
                </ReadableSelect>
              </div>
            </div>
            <div className="field">
              {label("publico", <>¿A quién estaría dirigida?</>)}
              <input id="publico" name="publico" placeholder="Ej.: comunidad educativa, familias, docentes" type="text" {...ctx.bind("publico")} maxLength={ctx.maxLength("publico")} />
            </div>
            <div className="field">
              {label("fecha-actividad", <>Fecha tentativa<Optional text="(si la tienes)" /></>)}
              <input id="fecha-actividad" name="fecha-actividad" type="date" min={requestDate()} {...ctx.bind("fecha-actividad")} />
            </div>
            <div className="paired-fields">
              <div className="field">
                {label("promocion-region", <>Región de la actividad</>)}
                <div className="select-wrap"><ReadableSelect id="promocion-region" name="promocion-region" aria-describedby="promocion-region-help" {...ctx.bind("promocion-region")}><RegionOptions /></ReadableSelect></div>
                <p className="field-help" id="promocion-region-help">Usamos tu región de residencia; puedes cambiarla.</p>
              </div>
              <div className="field">
                {label("promocion-commune", <>Comuna de la actividad</>)}
                <div className="select-wrap"><ReadableSelect id="promocion-commune" name="promocion-commune" {...ctx.bind("promocion-commune")}>{communeOptions("promocion")}</ReadableSelect></div>
              </div>
            </div>
          </div>
        </div>

        <div className="form-pane">
          <div className="pane-head">
            <div className="pane-icon berry"><Icon name="paper" /></div>
            <div><h3>Tu relato y contacto</h3><p>Para todos los motivos</p></div>
          </div>
          <div className="pane-fields">
            <div className="field" hidden={ctx.value("motivo")==="consulta"&&ctx.value("tema")==="estadisticas"}>
              {label("relato", <>Cuéntanos con tus propias palabras{!ctx.required("relato") && <Optional />}</>)}
              <p className="field-help" id="relato-help">Describe qué ocurre o qué necesitas, con el detalle que puedas.</p>
              <textarea id="relato" maxLength={5000} name="relato" placeholder="Describe qué sucede, qué necesitas o qué te gustaría consultar" rows={6} aria-describedby="relato-help contador" {...ctx.bind("relato")} />
              <div className={`char-counter${story > storyMax * 0.9 ? " is-near-limit" : ""}`} id="contador" aria-live={story > storyMax * 0.9 ? "polite" : "off"}>{story.toLocaleString("es-CL")} / {storyMax.toLocaleString("es-CL")} caracteres</div>
            </div>
            <div className="field">
              {label("email", <>Correo electrónico de contacto<Optional /></>)}
              <EmailInput id="email" name="email" value={ctx.value("email")} onValueChange={v=>ctx.setValue("email",v)} disabled={ctx.bind("email").disabled} required={ctx.required("email")} aria-invalid={!!ctx.errors.email} aria-describedby="email-help email-error" aria-errormessage={ctx.errors.email?"email-error":undefined}/>
              <p className="field-help" id="email-help">Para contactarte sobre tu comunicación.</p>
              <p className="field-error" id="email-error" hidden={!ctx.errors.email}>{ctx.errors.email}</p>
            </div>
            <div className="field">
              {label("telefono", <>Celular de contacto<Optional /></>)}
              <MobileInput id="telefono" name="telefono" value={ctx.value("telefono")} onValueChange={v=>ctx.setValue("telefono",v)} disabled={ctx.bind("telefono").disabled} required={ctx.required("telefono")} aria-invalid={!!ctx.errors.telefono} aria-describedby="telefono-help telefono-error" aria-errormessage={ctx.errors.telefono?"telefono-error":undefined} />
              <p className="field-help" id="telefono-help">Escribe los 8 dígitos. También puedes pegar el número con +56 9.</p>
              <p className="field-error" id="telefono-error" hidden={!ctx.errors.telefono}>{ctx.errors.telefono}</p>
            </div>
            <div className="field">
              {ctx.media ? <MediaAttachments control={ctx.media} id="archivos-elegidos"/> : <>
                <label htmlFor="adjuntos">Documentos o antecedentes<Optional /></label>
                <div className="upload-box">
                  <input className="sr-only" id="adjuntos" name="adjuntos" type="file" multiple accept=".pdf,.png,.jpg,.jpeg,.webp,.txt" disabled={ctx.busy || ctx.uploading} onChange={ctx.onFiles} />
                  <label className="upload-target" htmlFor="adjuntos">
                    <span className="upload-icon"><Icon name="upload" /></span>
                    <span><strong>Seleccionar archivos</strong><small>PDF, PNG, JPG, WebP o TXT. Máx. 5 archivos, 10 MB por archivo y 25 MB en total.</small></span>
                  </label>
                </div>
                <AttachmentStatus id="archivos-elegidos" files={ctx.attachments} pending={ctx.uploading} error={ctx.errors.adjuntos} feedback={ctx.fileFeedback} />
              </>}
            </div>
            <div className="manual-location">
              <div className="pane-head"><div><h3>Dónde vives</h3><p>Nos ayuda a orientar tu comunicación.</p></div></div>
              <div className="field">
                {label("adult-region", <>Región</>)}
                <div className="select-wrap"><ReadableSelect aria-describedby="adult-region-error" id="adult-region" name="adult-region" {...ctx.bind("adult-region")}><RegionOptions /></ReadableSelect></div>
                <p className="field-error" id="adult-region-error" hidden={!ctx.errors["adult-region"]}>{ctx.errors["adult-region"]}</p>
              </div>
              <div className="field">
                {label("adult-commune", <>Comuna<Optional /></>)}
                <div className="select-wrap"><ReadableSelect aria-describedby="adult-commune-error" id="adult-commune" name="adult-commune" {...ctx.bind("adult-commune")}>{communeOptions("adult")}</ReadableSelect></div>
                <p className="field-error" id="adult-commune-error" hidden={!ctx.errors["adult-commune"]}>{ctx.errors["adult-commune"]}</p>
              </div>
              <div className="field">
                {label("adult-address", <>Dirección<Optional text="(si corresponde)" /></>)}
                <input aria-describedby="adult-address-error" id="adult-address" name="adult-address" placeholder="Calle, número o una referencia" type="text" {...ctx.bind("adult-address")} maxLength={ctx.maxLength("adult-address")} />
                <p className="field-error" id="adult-address-error" hidden={!ctx.errors["adult-address"]}>{ctx.errors["adult-address"]}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="vuln-extensions" hidden={!ctx.shown("vulneracion-anexos")} id="vulneracion-anexos">
        <div className="nna-subsection">
          <div className="subsection-header">
            <div>
              <h3>Datos de los NNA involucrados</h3>
              <p className="section-intro">Agrega solo lo que sepas. No necesitas su RUN.</p>
            </div>
            <span className="count-pill" id="afectados-count">{mode === "grupal" ? groupLabel : ctx.count("AFECTADO_NNA") + " NNA" + (mode === "mixta" ? " · " + groupLabel : "")}</span>
          </div>
          <fieldset className="fieldset affected-mode"><legend>¿Cómo quieres registrar a los NNA afectados?</legend>
            <div className="radio-row">
              <label className="radio-pill"><input type="radio" name="afectados-modalidad" {...ctx.radio("afectados-modalidad","individual")}/> Individualmente</label>
              <label className="radio-pill"><input type="radio" name="afectados-modalidad" {...ctx.radio("afectados-modalidad","grupal")}/> Como grupo</label>
              <label className="radio-pill"><input type="radio" name="afectados-modalidad" {...ctx.radio("afectados-modalidad","mixta")}/> Personas y grupos</label>
            </div>
          </fieldset>
          <div className="repeat-list" hidden={mode === "grupal"} id="afectados-list">{ctx.people("AFECTADO_NNA")}</div>
          <button className="btn btn-secondary add-item" id="agregar-afectado" hidden={mode === "grupal"} type="button" onClick={() => ctx.action("agregar-afectado")}>+ Agregar otro NNA</button>
          <div id="afectados-grupos" className="conditional" hidden={!["grupal","mixta"].includes(mode)}>
            <h4>Grupos de NNA afectados <span className="count-pill">{ctx.groupCount}</span></h4>
            <p className="field-help">Puedes describir un curso, un grupo scout u otra agrupación sin identificar a cada integrante.</p>
            {ctx.groups()}
            <button type="button" className="btn btn-secondary add-item" disabled={ctx.busy||ctx.groupCount>=5} onClick={()=>ctx.action("agregar-grupo")}>+ Agregar otro grupo</button>
          </div>
        </div>
        <div className="nna-subsection">
          <div className="subsection-header">
            <div>
              <h3>Personas o instituciones señaladas en los hechos</h3>
              <p className="section-intro">Registra solo lo que sepas, sin presumir responsabilidades.</p>
            </div>
          </div>
          <label className="ack-checkbox subject-toggle">
            <input id="identifica-responsables" type="checkbox" {...ctx.check("identifica-responsables")} />
            <span>Puedo identificar a una persona o institución posiblemente vinculada con los hechos.</span>
          </label>
          <div className="conditional" hidden={!ctx.shown("responsables-contenido")} id="responsables-contenido">
            <div className="repeat-list" id="responsables-list">{ctx.people("PERSONA_SENALADA")}</div>
            <button className="btn btn-secondary add-item" id="agregar-responsable" type="button" onClick={() => ctx.action("agregar-responsable")}>+ Agregar otra persona o institución</button>
          </div>
        </div>
      </div>

      <section aria-labelledby="related-adults-title" className="related-section" hidden={!ctx.shown("related-adults-section")} id="related-adults-section">
        <div className="pane-head">
          <span className="pane-icon berry"><Icon name="message" /></span>
          <div>
            <h3 id="related-adults-title">Otras personas adultas relacionadas</h3>
            <p>Opcional: familiares, representantes, testigos u otros contactos relevantes.</p>
          </div>
          <span className="count-pill" id="related-adults-count">{ctx.count("ADULTO_RELACIONADO")} {ctx.count("ADULTO_RELACIONADO") === 1 ? "persona" : "personas"}</span>
        </div>
        <div className="repeat-list" id="related-adults-list">{ctx.people("ADULTO_RELACIONADO")}</div>
        <button className="add-repeat" id="add-related-adult" type="button" onClick={() => ctx.action("add-related-adult")}><span aria-hidden="true">+</span> Agregar persona adulta</button>
      </section>

      <div className="privacy-note">
        <Icon name="lock" small />
        <span><strong>Privacidad:</strong> usaremos tu información para gestionar tu comunicación, según el aviso que aceptaste al ingresar.</span>
      </div>
    </div>
  </section>;
}
