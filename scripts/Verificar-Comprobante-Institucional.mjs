import { readFileSync, statSync } from 'node:fs';
import { createPublicKey, createHash, verify } from 'node:crypto';

// Herramienta independiente: la confianza se aporta por archivo externo, nunca desde el documento.
try {
  const [documento, confianza] = process.argv.slice(2);
  if (!documento || !confianza) throw new Error('Uso: node scripts/Verificar-Comprobante-Institucional.mjs <comprobante.json> <claves-publicas-confiadas.json>');
  if (statSync(documento).size > 16384 || statSync(confianza).size > 65536) throw new Error('Archivo fuera de límites');
  const bytes = readFileSync(documento);
  const envelope = JSON.parse(bytes.toString('utf8'));
  const trust = JSON.parse(readFileSync(confianza, 'utf8'));
  if (envelope.algoritmo !== 'Ed25519' || !/^[A-Za-z0-9_-]{1,64}$/.test(envelope.keyId ?? '')
      || typeof envelope.payload !== 'string' || envelope.payload.length > 8192 || !/^[A-Za-z0-9_-]+$/.test(envelope.payload)
      || !/^[A-Za-z0-9_-]{86}$/.test(envelope.firma ?? '')
      || !Object.hasOwn(trust, envelope.keyId)) throw new Error('Documento o firmante desconocido');
  const key = createPublicKey(trust[envelope.keyId]);
  if (key.asymmetricKeyType !== 'ed25519') throw new Error('Clave incompatible');
  const payload = Buffer.from(envelope.payload, 'base64url');
  if (!verify(null, payload, key, Buffer.from(envelope.firma, 'base64url'))) throw new Error('Firma inválida');
  const registro = JSON.parse(payload.toString('utf8'));
  if (!['1.0', '1.1'].includes(registro.version) || registro.emisor !== 'SIGER_DDN' || registro.hecho !== 'COMUNICACION_REGISTRADA'
      || registro.canalOrigen !== (registro.version === '1.0' ? 'FORMULARIO_WEB_PUBLICO' : 'FORMULARIO_WEB_NNA')
      || !/^[A-Z][A-Z0-9]{1,15}-[0-9]{1,19}$/.test(registro.folio ?? '')) throw new Error('Hecho firmado incompatible');
  console.log(JSON.stringify({ firmaValida: true, keyId: envelope.keyId, folio: registro.folio,
    comunicacionId: registro.comunicacionId, operacionPublicaId: registro.operacionPublicaId,
    registradaEn: registro.registradaEn, sha256Documento: createHash('sha256').update(bytes).digest('hex') }, null, 2));
} catch (error) {
  console.error(error instanceof Error ? error.message : 'No se pudo verificar el documento');
  process.exitCode = 1;
}
