import {Fragment} from 'react';
import type {SummarySection} from './reviewModel';

/** Presentación HTML de las secciones de revisión; el comprobante PDF usa las mismas secciones. */
export function SummarySections({sections}:{sections:SummarySection[]}){
  return <>{sections.map((section,index)=><Fragment key={section.title+index}>
    <section className="dialog-section review-section" aria-label={section.title}><h3>{section.title}</h3>
      {section.description&&<p className="review-note">{section.description}</p>}
      {section.rows.length||!section.empty?<dl>{section.rows.map(row=><div className={`summary-row${row.long?' summary-row-long':''}`} key={row.key}><dt>{row.label}</dt><dd>{row.value}</dd></div>)}</dl>:<p className="review-empty">{section.empty}</p>}
    </section>
    {section.note&&<p className="field-help">{section.note}</p>}
  </Fragment>)}</>;
}
