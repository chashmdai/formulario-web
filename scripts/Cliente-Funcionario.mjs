import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
export const origin = process.env.NUCLEO_LOCAL_ORIGIN ?? 'https://localhost:8443';
const password = process.env.NUCLEO_DEMO_PASSWORD;
if (!password) throw new Error('Falta credencial sintética en el entorno de proceso');
export class Operator {
  cookies = new Map();
  constructor(name) { this.name = name; }
  async request(route, options = {}) {
    const r = await fetch(origin + route, { ...options, redirect: 'manual', signal: AbortSignal.timeout(10000), headers: { Cookie: [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; '), ...options.headers } });
    for (const cookie of r.headers.getSetCookie()) { const part = cookie.split(';')[0]; const index = part.indexOf('='); this.cookies.set(part.slice(0, index), part.slice(index + 1)); }
    return r;
  }
  async json(route) { const r = await this.request(route); assert.equal(r.status, 200, `GET ${route}: ${r.status}`); return r.json(); }
  async csrf() { return this.json('/nucleo/csrf'); }
  async login() {
    const c = await this.csrf(); const initial = this.cookies.get('SIGERSTAFFSESSION');
    const r = await this.request('/nucleo/login', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', [c.headerName]: c.token }, body: new URLSearchParams({ username: this.name, password }) });
    assert.equal(r.status, 204, `Login ${this.name}: ${r.status}`); assert.notEqual(initial, this.cookies.get('SIGERSTAFFSESSION'));
    const setCookie = r.headers.getSetCookie().find(v => v.startsWith('SIGERSTAFFSESSION=')); assert.ok(setCookie && /Secure/i.test(setCookie) && /HttpOnly/i.test(setCookie) && /SameSite=Strict/i.test(setCookie));
    const s = await this.json('/nucleo/session'); assert.equal(s.operador.id, this.name);
  }
  async command(route, body, key = randomUUID(), expected = 200) {
    const c = await this.csrf(); const r = await this.request('/nucleo' + route, { method: 'POST', headers: { 'Content-Type': 'application/json', [c.headerName]: c.token, 'Idempotency-Key': key }, body: body === undefined ? undefined : JSON.stringify(body) });
    assert.equal(r.status, expected, `POST ${route}: ${r.status}`); return r.status === 200 ? r.json() : undefined;
  }
}

