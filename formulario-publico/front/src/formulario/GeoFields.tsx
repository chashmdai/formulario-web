import {ReadableSelect} from '../ui/ReadableSelect';
import type { NnaContext } from './NnaFlow';
import { comunas, regiones } from './territorio';
const regionOrder = ['15','01','02','03','04','05','13','06','07','16','08','09','14','10','11','12'];

export function GeoFields({ctx}: {ctx: Pick<NnaContext, 'bind' | 'value' | 'errors'>}) {
  return <>
    <div className="field"><label htmlFor="nna-region">Región <span aria-hidden="true" className="required-mark">*</span></label>
      <div className="select-wrap"><ReadableSelect id="nna-region" name="nna-region" aria-describedby="nna-region-help nna-region-error" {...ctx.bind('nna-region')}>
        <option value="">Selecciona una región</option>{regionOrder.map(id => <option key={id} value={id}>{regiones[id]}</option>)}
      </ReadableSelect></div><p className="field-help" id="nna-region-help"></p><p className="field-error" id="nna-region-error" hidden={!ctx.errors['nna-region']}>{ctx.errors['nna-region']}</p>
    </div>
    <div className="field"><label htmlFor="nna-commune">Comuna <span className="muted-label">(opcional)</span></label>
      <div className="select-wrap"><ReadableSelect id="nna-commune" name="nna-commune" aria-describedby="nna-commune-help nna-commune-error" {...ctx.bind('nna-commune')}>
        <option value="">{ctx.value('nna-region') ? 'Selecciona una comuna' : 'Selecciona primero una región'}</option>
        {(comunas[ctx.value('nna-region')] || []).map(name => <option key={name} value={name}>{name}</option>)}
      </ReadableSelect></div><p className="field-help" id="nna-commune-help">Comunas disponibles según la región seleccionada. Campo opcional.</p>
      <p className="field-error" id="nna-commune-error" hidden={!ctx.errors['nna-commune']}>{ctx.errors['nna-commune']}</p>
    </div>
  </>;
}
