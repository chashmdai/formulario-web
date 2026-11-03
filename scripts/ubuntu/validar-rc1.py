#!/usr/bin/env python3
"""Preflight de manifiesto RC1, sin iniciar servicios ni imprimir configuración privada."""
import argparse
import ipaddress
import json
from pathlib import Path
import re
import subprocess

def validar(config, node, props):
    services = config['services']
    for name, s in services.items():
        image = s.get('image', '')
        if not re.search(r'(?:@|^)sha256:[a-f0-9]{64}$', image):
            raise ValueError('Todas las imágenes deben estar fijadas por digest')
        if s.get('build') or s.get('privileged'):
            raise ValueError('Producción no construye imágenes ni usa privilegios')
        if s.get('ports') and name != 'proxy':
            raise ValueError('Solo Caddy puede publicar puertos')
        if name != 'migrar' and (s.get('restart') != 'unless-stopped' or not s.get('healthcheck')):
            raise ValueError('Servicios persistentes requieren restart y healthcheck')
    for port in services['proxy'].get('ports', []):
        if port.get('host_ip') != '127.0.0.1':
            raise ValueError('El manifiesto cerrado RC1 solo admite loopback')
    network = config['networks']['pisee_privada']
    if not network.get('internal'): raise ValueError('Red PISEE debe ser interna')
    subnet = ipaddress.ip_network(network['ipam']['config'][0]['subnet'])
    backend = services['app']['networks']['pisee_privada']['ipv4_address']
    provider = services['pisee']['networks']['pisee_privada']['ipv4_address']
    if backend == provider or any(ipaddress.ip_address(ip) not in subnet for ip in [backend, provider]):
        raise ValueError('Direcciones estáticas inválidas')
    if set(node['servidores']['interno']['seguridad']['ipPermitidas']) != {'127.0.0.1', backend}:
        raise ValueError('El nodo debe admitir exclusivamente loopback y backend fijo')
    if node['log'].get('ruta') != '/dev/null' or str(node['log'].get('nivel')).lower() != 'error':
        raise ValueError('Prohibidos logs persistidos del nodo que puedan contener cuerpos')
    if props.get('PISEE_ENVIRONMENT') != 'PRODUCTION' or props.get('PISEE_RESTRICTED_CAMPAIGN','false') != 'false':
        raise ValueError('Producción institucional sin restricciones de campaña')
    if props.get('PISEE_ALLOWED_RUNS_PATH') or 'stub' in props.get('PISEE_BASE_URL','').lower():
        raise ValueError('Manifiesto productivo no admite lista ni proveedor stub')
    if props.get('PISEE_BASE_URL') != 'http://pisee:8084':
        raise ValueError('Cliente debe utilizar el nodo en la red privada')
    if props.get('siger.certificacion-claveunica','false') != 'false':
        raise ValueError('RC1 completo no usa modo de certificación')

if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--env-file',required=True);p.add_argument('--node-config',required=True);p.add_argument('--properties',required=True)
    a=p.parse_args();repo=Path(__file__).resolve().parents[2]
    try:
        cmd=['docker','compose','--env-file',a.env_file,'-f',str(repo/'infra/ubuntu/compose.yml'),'-f',str(repo/'infra/ubuntu/compose.production.yml'),'config','--format','json']
        result=subprocess.run(cmd,capture_output=True)
        if result.returncode: raise ValueError('Compose no válido')
        props=dict(line.split('=',1) for line in Path(a.properties).read_text().splitlines() if '=' in line and not line.lstrip().startswith('#'))
        validar(json.loads(result.stdout),json.loads(Path(a.node_config).read_text()),props)
        print('RC1 preflight OK: digests, redes, IP exacta, reinicio, salud, privacidad y configuración')
    except (OSError,ValueError,KeyError):
        raise SystemExit('Preflight RC1 rechazado; revisar manifiesto privado sin imprimirlo')
