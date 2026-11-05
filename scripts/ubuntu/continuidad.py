#!/usr/bin/env python3
"""Control explícito de PULL continuo. Sin relojes, daemon, cambios de configuración ni reaperturas automáticas."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
from productor import Productor


def control(op):
    status, headers, body = op.request('GET', '/operacion/v1/control', 'operations-token')
    if status != 200 or 'etag' not in headers:
        raise ValueError('Control no disponible')
    value = json.loads(body)
    if type(value.get('revision')) is not int or any(type(value.get(k)) is not bool for k in ['aceptarNuevos', 'recuperar']):
        raise ValueError('Control inválido')
    return value, headers['etag']


def cambiar_pull(op, enabled, revision, reference, ack_id=None, trust_sha256=None):
    if not re.fullmatch(r'[A-Za-z0-9_-]{8,120}', reference):
        raise ValueError('Referencia operacional sin datos personales requerida')
    before, etag = control(op)
    if before['revision'] != revision:
        raise ValueError('Revisión cambió; volver a inspeccionar, sin reintento de escritura')
    if enabled:
        # Una activación no revoca un cierre ciudadano o una contingencia del operador.
        if not before['aceptarNuevos'] or before['contingencia'] != 'NORMAL':
            raise ValueError('Recepción pausada o contingencia; no reabrir por inferencia')
        props = dict(line.split('=', 1) for line in (op.root/'config/application.properties').read_text().splitlines()
                     if '=' in line and not line.lstrip().startswith('#'))
        if any(props.get(k) != 'true' for k in ['publico.integracion.preparada', 'publico.integracion.habilitada']):
            raise ValueError('Integración no configurada')
        raw = (op.root/'keys/institutional-trust.json').read_bytes()
        if not trust_sha256 or not re.fullmatch('[a-f0-9]{64}', trust_sha256) or hashlib.sha256(raw).hexdigest() != trust_sha256:
            raise ValueError('Confianza ACK cambió o huella ausente')
        if not ack_id or ack_id not in json.loads(raw):
            raise ValueError('Identidad ACK no instalada')
    if before['recuperar'] == enabled:
        return {'reference': reference, 'changed': False, 'control': before}
    status, _, body = op.request('PUT', '/operacion/v1/control', 'operations-token',
        {'aceptarNuevos': before['aceptarNuevos'], 'recuperar': enabled,
         'contingencia': before['contingencia'], 'motivo': 'REANUDACION' if enabled else 'INCIDENTE'},
        {'If-Match': etag})
    if status != 200:
        raise ValueError('CAS rechazado/resultado incierto; inspeccionar estado, no reabrir ni reintentar automáticamente')
    after = json.loads(body)
    if after['recuperar'] != enabled or after['aceptarNuevos'] != before['aceptarNuevos'] or after['contingencia'] != before['contingencia']:
        raise ValueError('Resultado de control inesperado; intervención requerida')
    return {'reference': reference, 'changed': True, 'control': after}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root', required=True)
    sub = p.add_subparsers(dest='action', required=True)
    sub.add_parser('status')
    for name in ['activate', 'pause']:
        action = sub.add_parser(name)
        action.add_argument('--if-match', type=int, required=True)
        action.add_argument('--reference', required=True)
        if name == 'activate':
            action.add_argument('--ack-id', required=True)
            action.add_argument('--trust-sha256', required=True)
    a = p.parse_args()
    if os.geteuid() != 0 or Path(a.root).resolve() != Path('/srv/ddn-formulario-produccion'):
        raise ValueError('Solo operador y destino productivo explícito')
    op = Productor(a.root)
    if op.meta['project'] != 'ddn-formulario-produccion':
        raise ValueError('Proyecto inesperado')
    if a.action == 'status':
        value, _ = control(op)
    else:
        value = cambiar_pull(op, a.action == 'activate', a.if_match, a.reference,
                             getattr(a, 'ack_id', None), getattr(a, 'trust_sha256', None))
    print(json.dumps(value))


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError):
        raise SystemExit('Control no completado; verificar estado operativo. No se muestran secretos ni se reintenta la escritura.')
