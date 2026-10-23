import fs from 'node:fs';
import path from 'node:path';
import dns from 'node:dns/promises';
import { randomUUID } from 'node:crypto';
import { spawnSync } from 'node:child_process';
const [origin, output] = process.argv.slice(2);
const url = new URL(origin);
if (url.origin !== 'https://claveunica.formularioadulto.defensorianinez.gob.cl') throw new Error('Origen de laboratorio inválido');
const addresses = await dns.lookup(url.hostname, { all: true });
if (!addresses.length || addresses.some(a => !['127.0.0.1', '::1'].includes(a.address))) throw new Error('Se requiere resolución loopback local');
fs.mkdirSync(output, { recursive: true });
class Citizen {
  cookie = ''; csrf = null;
  async request(route, init) {
    const response = await fetch(origin + route, { ...init, redirect: 'error', signal: AbortSignal.timeout(12000), headers: { ...init?.headers, ...(this.cookie ? { Cookie: this.cookie } : {}) } });
    for (const item of response.headers.getSetCookie()) {
      if (!/;\s*Secure/i.test(item) || !/;\s*HttpOnly/i.test(item) || !/;\s*SameSite=Lax/i.test(item)) throw new Error('Cookie sin protección');
      this.cookie = item.split(';')[0];
    }
    return response;
  }
  async start() { const r = await this.request('/api/csrf'); if (r.status !== 200) throw new Error('CSRF no disponible'); this.csrf = await r.json(); }
  async send(id, body, token = this.csrf.token) { return this.request('/api/nna/comunicaciones', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': id, [this.csrf.headerName]: token }, body: JSON.stringify(body) }); }
}
const citizen = new Citizen(); await citizen.start();
const session = await (await citizen.request('/api/session')).json();
if (session.authenticated) throw new Error('Esta demostración requiere una sesión nueva sin autenticación');
const id = randomUUID();
const body = { datos: { primerNombre: 'Nombre sintético compuesto', segundoNombre: 'Segundo sintético', primerApellido: 'Apellido sintético compuesto', segundoApellido: null, runNumero: null, runDv: null, direccion: ' Dirección  sintética ', comuna: 'Comuna sintética', region: 'Región sintética' }, mensaje: 'Relato exclusivamente sintético para verificar la custodia, el registro y la firma institucional.\nSin datos personales reales.', avisoPrivacidadLeido: true, avisoVersion: 'laboratorio-v1' };
if ((await citizen.send(randomUUID(), body, 'csrf-invalido')).status !== 403) throw new Error('CSRF no rechazado');
const first = await citizen.send(id, body);
if (first.status !== 202) throw new Error(`Recepción HTTP ${first.status}`);
const pending = await first.json();
if (pending.id !== id) throw new Error('Operación inesperada');
const retry = await citizen.send(id, body);
if (retry.status !== 202 || (await retry.json()).id !== id) throw new Error('Reintento no idempotente');
const other = new Citizen(); await other.start();
for (const suffix of ['', '/comprobante']) if ((await other.request(`/api/nna/comunicaciones/${id}${suffix}`)).status !== 404) throw new Error('Acceso desde otra sesión permitido');
// Prueba HTTP de la ruta retirada usando un ciudadano: nunca debe autorizar la integración.
if ((await citizen.request('/integracion/v3/entregas/reclamar', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"limite":20}' })).status !== 401) throw new Error('Frontera técnica expuesta');
console.log('Custodia técnica aceptada; esperando registro institucional (máximo 90 segundos).');
fs.writeFileSync(path.join(output, 'recepcion-tecnica.json'), JSON.stringify({ id, estadoInicial: pending.estado, sinRun: true, csrfVerificado: true, aislamientoSesiones: true }, null, 2));
let state = pending;
for (let attempt = 0; attempt < 45 && state.estado !== 'REGISTRADA_SIGER'; attempt++) {
  await new Promise(resolve => setTimeout(resolve, 2000));
  const response = await citizen.request(`/api/nna/comunicaciones/${id}`);
  if (response.status !== 200) throw new Error(`Consulta HTTP ${response.status}`);
  state = await response.json();
}
if (state.estado !== 'REGISTRADA_SIGER' || state.registroInstitucional?.registro?.canalOrigen !== 'FORMULARIO_WEB_NNA') throw new Error('No se obtuvo registro NNA institucional');
const response = await citizen.request(`/api/nna/comunicaciones/${id}/comprobante`);
if (response.status !== 200) throw new Error('Comprobante no disponible');
const receipt = await response.json();
if (JSON.stringify(receipt) !== JSON.stringify(state.comprobante)) throw new Error('Comprobante no estable');
const receiptPath = path.join(output, 'comprobante-nna.json'); fs.writeFileSync(receiptPath, JSON.stringify(receipt, null, 2));
const trust = path.resolve('.local/robustez/recepcion/institution-trust.json');
const result = spawnSync(process.execPath, [path.resolve('scripts/Verificar-Comprobante-Institucional.mjs'), receiptPath, trust], { encoding: 'utf8' });
if (result.status !== 0) throw new Error('Firma institucional no válida');
const verified = JSON.parse(result.stdout); fs.writeFileSync(path.join(output, 'verificacion-firma.json'), JSON.stringify(verified, null, 2));
fs.writeFileSync(path.join(output, 'resultado-http.json'), JSON.stringify({ operacionId: id, estado: state.estado, registro: state.registroInstitucional.registro, firmaValida: true, repeticionMismaOperacion: true, aislamientoSesiones: true, csrf: true, tlsVerificado: true, llamadasGobierno: 0 }, null, 2));
console.log(JSON.stringify({ estado: state.estado, folio: verified.folio, firmaValida: true, sinRun: true }));
