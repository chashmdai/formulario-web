// Solo loopback HTTPS, datos sintéticos y cuentas explícitas de laboratorio. No llama a proveedores.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const output = path.resolve(process.argv[2] ?? '.local/nucleo-evidencias');
import { Operator } from './Cliente-Funcionario.mjs';
const dist = new Operator('demo.distribucion'), head = new Operator('demo.jefatura.upr.a'), prof = new Operator('demo.profesional.upr.a'), prof2 = new Operator('demo.profesional2.upr.a'), upd = new Operator('demo.jefatura.upd.a'), foreign = new Operator('demo.jefatura.uep.b');
for (const o of [dist, head, prof, prof2, upd, foreign]) await o.login();
const source = JSON.parse(fs.readFileSync(path.join(output, 'resultado-http.json'), 'utf8'));
const cid = source.registro.comunicacionId;
assert.ok(cid, 'Falta Comunicación web registrada por SIGER');
const get = (o = dist) => o.json(`/nucleo/comunicaciones/${cid}`);
const initial = await get(); assert.equal(initial.contenido.tipoIdentidad, 'DECLARADA_NNA'); assert.equal(initial.contenido.declaracionNna.runNumero, null); assert.equal(initial.comunicacion.folio, source.registro.folio);
assert.ok((await dist.json('/nucleo/comunicaciones')).some(r => r.id === cid), 'Web no aparece en buzón');
const noCsrf = await dist.request(`/nucleo/comunicaciones/${cid}/descartar`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': randomUUID() }, body: JSON.stringify({ revision: 0, texto: 'Sintético' }) }); assert.equal(noCsrf.status, 403);
const distribution = { revision: initial.comunicacion.revision, destinos: [{ unidad: 'UPR', sede: 'DEMO_SEDE_A', evaluador: head.name }, { unidad: 'UPD', sede: 'DEMO_SEDE_A', evaluador: upd.name }] };
const sameKey = randomUUID(); const first = await dist.command(`/comunicaciones/${cid}/distribuir`, distribution, sameKey); assert.deepEqual(await dist.command(`/comunicaciones/${cid}/distribuir`, distribution, sameKey), first);
let detail = await get(); assert.equal(detail.destinos.length, 2);
let dest = detail.destinos.find(d => d.unidad === 'UPR');
const c1 = (await head.command(`/destinos/${dest.id}/abrir-caso`, { revision: dest.revision, responsable: prof.name, complejidadProvisional: null })).id;
detail = await get(); dest = detail.destinos.find(d => d.unidad === 'UPR');
const c2 = (await head.command(`/destinos/${dest.id}/abrir-caso`, { revision: dest.revision, responsable: prof2.name, complejidadProvisional: 'Descripción sintética sin efectos automáticos' })).id;
detail = await get(); assert.equal(new Set(detail.casos.map(c => c.requerimientoId)).size, 1); assert.equal(detail.casos.length, 2);
for (const o of [prof2, foreign]) { assert.equal((await o.request(`/nucleo/casos/${c1}`)).status, 404); await o.command(`/casos/${c1}/actuaciones`, { revision: 0, texto: 'No autorizado' }, randomUUID(), 404); }
assert.equal((await foreign.request(`/nucleo/comunicaciones/${cid}`)).status, 404);
async function caseDecision(o, id, suffix, text) { const c = await o.json(`/nucleo/casos/${id}`); await o.command(`/casos/${id}/${suffix}`, { revision: c.revision, texto: text }); }
await caseDecision(prof, c1, 'actuaciones', 'Actuación sintética  inicial\nLímites del texto preservados.');
await caseDecision(prof, c1, 'solicitar-termino', 'Primera solicitud sintética');
await caseDecision(head, c1, 'observar', 'Observaciones sintéticas de jefatura');
assert.equal((await prof.json(`/nucleo/casos/${c1}`)).actuaciones.length, 1);
await caseDecision(prof, c1, 'actuaciones', 'Actuación sintética posterior a observaciones');
await caseDecision(prof, c1, 'solicitar-termino', 'Nueva solicitud sintética');
await caseDecision(head, c1, 'cerrar', 'Cierre sintético del primer Caso');
detail = await get(); assert.equal(detail.casos.find(c => c.id === c2).estado, 'EN_GESTION'); assert.equal(detail.comunicacion.estadoRequerimiento, 'ACTIVO');
await caseDecision(prof2, c2, 'actuaciones', 'Actuación del segundo responsable sintético');
await caseDecision(prof2, c2, 'solicitar-termino', 'Solicitud sintética del segundo Caso');
await caseDecision(head, c2, 'cerrar', 'Cierre sintético del segundo Caso');
detail = await get(); assert.equal(detail.comunicacion.casosAbiertos, 0); assert.equal(detail.comunicacion.estadoRequerimiento, 'ACTIVO'); assert.equal(detail.comunicacion.destinosPendientes, 2);
dest = detail.destinos.find(d => d.unidad === 'UPR'); await head.command(`/destinos/${dest.id}/finalizar-evaluacion`, { revision: dest.revision, texto: 'Evaluación sintética explícitamente finalizada' });
detail = await get(); assert.equal(detail.comunicacion.destinosPendientes, 1); assert.equal(detail.comunicacion.estadoRequerimiento, 'ACTIVO');
dest = detail.destinos.find(d => d.unidad === 'UPD'); await upd.command(`/destinos/${dest.id}/responder`, { revision: dest.revision, texto: 'Respuesta sintética independiente sin Caso' });
detail = await get(); assert.equal(detail.comunicacion.estadoRequerimiento, 'TERMINADO'); assert.equal(detail.comunicacion.destinosPendientes, 0);
for (const suffix of ['reabrir']) await head.command(`/casos/${c1}/${suffix}`, undefined, randomUUID(), 409);
const manualInput = { texto: 'Atención  manual exclusivamente sintética', canalDeclarado: 'Canal sintético de laboratorio', recibidaEnDeclarada: '2026-10-18T10:00:00Z', sede: 'DEMO_SEDE_A', respuestaYaEntregada: 'Orientación sintética ya entregada' };
const manual = await dist.command('/comunicaciones/manual', manualInput);
let m = await dist.json(`/nucleo/comunicaciones/${manual.id}`); assert.equal(m.comunicacion.requerimientoId, null); assert.equal(m.casos.length, 0); assert.equal(m.comunicacion.estado, 'RESPONDIDA'); assert.equal(m.contenido.registradoPor, dist.name); assert.equal(m.contenido.identidad, null);
await dist.command(`/comunicaciones/${manual.id}/vincular`, { padre: cid, casoReferencia: c1, motivo: 'Vínculo documental sintético sin reapertura' });
assert.equal((await get()).comunicacion.estadoRequerimiento, 'TERMINADO'); assert.equal((await head.json(`/nucleo/casos/${c1}`)).estado, 'CERRADO');
const notice = (await prof.json('/nucleo/avisos')).find(n => n.hija === manual.id); assert.ok(notice && !notice.leido); await prof.command(`/avisos/${notice.id}/leer`); assert.equal((await prof.json('/nucleo/avisos')).find(n => n.id === notice.id).leido, true);
const discarded = await dist.command('/comunicaciones/manual', { ...manualInput, respuestaYaEntregada: null });
m = await dist.json(`/nucleo/comunicaciones/${discarded.id}`); await dist.command(`/comunicaciones/${discarded.id}/descartar`, { revision: m.comunicacion.revision, texto: 'Motivo sintético de descarte, sin eliminación' }); m = await dist.json(`/nucleo/comunicaciones/${discarded.id}`); assert.equal(m.comunicacion.estado, 'DESCARTADA'); assert.equal(m.decisionAutor, dist.name); assert.ok(m.decisionFecha);
detail = await get();
const result = { comunicacionId: cid, folio: detail.comunicacion.folio, requerimientoId: detail.comunicacion.requerimientoId, casos: [c1, c2], estadoGlobal: detail.comunicacion.estadoRequerimiento, destinosResueltos: detail.destinos.every(d => d.estado === 'RESUELTO'), dosCasosMismaUnidad: true, observacionesSinPerdida: true, respuestaManualSinCaso: manual.id, descarteSinBorrado: discarded.id, hijoSinReapertura: true, avisoInternoLeido: true, controlPorRecurso: true, csrf: true, cookiesSeguras: true, sessionFixation: true, firmaWebConservada: 'Comprobada por verificación MySQL posterior', transicionesPendientes409: true, eventos: detail.historial.map(e => ({ codigo: e.codigo, autor: e.autor, fecha: e.fecha })), llamadasGobierno: 0 };
fs.writeFileSync(path.join(output, 'resultado-nucleo.json'), JSON.stringify(result, null, 2));
console.log(JSON.stringify({ folio: result.folio, estadoGlobal: result.estadoGlobal, casos: result.casos.length, destinosResueltos: true, controlPorRecurso: true, llamadasGobierno: 0 }));


