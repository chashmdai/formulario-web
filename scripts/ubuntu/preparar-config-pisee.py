#!/usr/bin/env python3
"""Crea una config NUEVA del nodo: IP exclusiva del backend, nunca una subred.
No modifica el original ni credenciales/certificados. Fuera de Git, 0400 uid 1000.
"""
import argparse
import ipaddress
import json
import os
from pathlib import Path

def preparar(source, target, backend, node, subnet):
    source, target = Path(source), Path(target).absolute()
    network = ipaddress.ip_network(subnet)
    backend, node = ipaddress.ip_address(backend), ipaddress.ip_address(node)
    if not network.is_private or backend not in network or node not in network or backend == node:
        raise ValueError('Red privada y direcciones distintas dentro de su rango requeridas')
    if any(ip in (network.network_address, network.broadcast_address) for ip in (backend, node)):
        raise ValueError('No usar dirección de red o broadcast')
    if any(p.is_symlink() or (p / '.git').exists() for p in [target, *target.parents]):
        raise ValueError('Destino sin enlaces y fuera de Git requerido')
    data = json.loads(source.read_text())
    data['servidores']['interno']['seguridad']['ipPermitidas'] = ['127.0.0.1', str(backend)]
    # No permitir logs de cuerpos del nodo, ni siquiera en INFO.
    data['log'].update(nivel='error', ruta='/dev/null')
    os.umask(0o077)
    with target.open('x') as f:
        json.dump(data, f, indent=2)
    target.chmod(0o400)
    if os.geteuid() == 0:
        os.chown(target, 1000, 1000)

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    for name in ['source', 'target', 'backend', 'node', 'subnet']:
        p.add_argument('--' + name, required=True)
    args = p.parse_args()
    try:
        preparar(**vars(args))
        print('Config privada creada; original intacto; solo IP exacta del backend autorizada')
    except (OSError, ValueError, KeyError):
        raise SystemExit('Preparación rechazada; revisar archivos privados y rango de red sin imprimirlos')
