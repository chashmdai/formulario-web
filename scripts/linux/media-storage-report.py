#!/usr/bin/env python3
"""Inventario agregado de solo lectura. No elimina, libera cuotas ni verifica ACK.
Linux/WSL. La medición en línea no es una instantánea transaccional de BD y disco.
"""
import argparse
import json
import os
import re
import stat
import subprocess
import sys
from pathlib import Path

GIB = 1024 ** 3
FOLDERS = ('objects', 'chunks', 'incoming', 'derived')


def walk_error(error):
    raise error


def inventory(root):
    root = Path(root).absolute()
    for part in (root, *root.parents):
        if part.is_symlink():
            raise ValueError('No se admiten enlaces en la ruta del almacén')
    if not root.is_dir():
        raise ValueError('Almacén existente requerido')
    folders = {}
    for folder in FOLDERS:
        base = root / folder
        if base.is_symlink() or not base.is_dir():
            raise ValueError('Estructura privada incompleta')
        count = logical = allocated = 0
        for directory, dirs, files in os.walk(base, followlinks=False, onerror=walk_error):
            for name in dirs:
                if (Path(directory) / name).is_symlink():
                    raise ValueError('Enlace inesperado; inventario detenido')
            for name in files:
                info = (Path(directory) / name).lstat()
                if not stat.S_ISREG(info.st_mode):
                    raise ValueError('Objeto no regular; inventario detenido')
                count += 1
                logical += info.st_size
                allocated += info.st_blocks * 512
        folders[folder] = dict(archivos=count, bytesLogicos=logical, bytesAsignados=allocated)
    fs = os.statvfs(root)
    return dict(carpetas=folders, filesystem=dict(
        total=fs.f_blocks * fs.f_frsize, libreAplicacion=fs.f_bavail * fs.f_frsize,
        libreTotal=fs.f_bfree * fs.f_frsize, inodosLibres=fs.f_favail),
        nota='No sumar filesystem a carpetas: las carpetas están incluidas. Bytes asignados solo a archivos, no directorios. No incluye MySQL, Docker ni respaldos; el libre sí refleja otros usos del mismo filesystem.')


def identifier(value):
    if not re.fullmatch(r'[a-z][a-z0-9_]{0,62}', value):
        raise ValueError('Esquema SQL inválido')
    return value


def database_summary(database, container):
    db = identifier(database)
    # Una única conexión de lectura; sin nombres, UUID, RUN, cuerpos, firmas ni secretos.
    sql = f'''SET TRANSACTION READ ONLY;
START TRANSACTION WITH CONSISTENT SNAPSHOT;
SELECT reserved_bytes FROM `{db}`.media_capacity WHERE id=1;
SELECT COALESCE(SUM(reserved_bytes),0) FROM `{db}`.media_upload WHERE released=FALSE;
SELECT state,COUNT(*),COALESCE(SUM(expected_size),0),COALESCE(SUM(CASE WHEN released=FALSE THEN reserved_bytes ELSE 0 END),0) FROM `{db}`.media_upload GROUP BY state;
SELECT storage_kind,COUNT(*),COALESCE(SUM(size_bytes),0) FROM `{db}`.citizen_attachment GROUP BY storage_kind;
SELECT 'RECIBIDOS',COUNT(*),COALESCE(SUM(a.size_bytes),0) FROM `{db}`.citizen_attachment a JOIN `{db}`.citizen_submission s ON s.journey_id=a.journey_id;
SELECT 'CON_ACK_ALMACENADO',COUNT(*),COALESCE(SUM(a.size_bytes),0) FROM `{db}`.citizen_attachment a JOIN `{db}`.citizen_submission s ON s.journey_id=a.journey_id WHERE s.state='CONFIRMADA' AND s.ack IS NOT NULL;
SELECT 'SANEABLES',COUNT(*),COALESCE(SUM(u.reserved_bytes),0) FROM `{db}`.media_upload u WHERE u.expires_ms<=UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AND u.released=FALSE AND u.state IN ('RECIBIENDO','REINTENTABLE','RECHAZADO','CANCELADO','EXPIRADO') AND NOT EXISTS(SELECT 1 FROM `{db}`.citizen_attachment a WHERE a.id=u.id) AND NOT EXISTS(SELECT 1 FROM `{db}`.citizen_submission s WHERE s.journey_id=u.journey_id);
SELECT 'VALIDADOS_SIN_REFERENCIA',COUNT(*),COALESCE(SUM(u.reserved_bytes),0) FROM `{db}`.media_upload u WHERE u.state='VALIDADO' AND u.released=FALSE AND NOT EXISTS(SELECT 1 FROM `{db}`.citizen_attachment a WHERE a.id=u.id);
SELECT 'MYSQL_TABLAS_INDICES',COALESCE(SUM(data_length+index_length),0) FROM information_schema.tables WHERE table_schema='{db}';
COMMIT;'''
    result = subprocess.run(['docker', 'exec', '-i', container, 'mysql',
        '--defaults-extra-file=/run/secrets/root.cnf', '--batch', '--raw', '--skip-column-names'],
        input=sql, text=True, capture_output=True, timeout=30)
    if result.returncode:
        raise ValueError('Consulta agregada fallida; requiere esquema V8 y acceso administrativo local. Diagnóstico privado omitido')
    rows = [line.split('\t') for line in result.stdout.splitlines()]
    if len(rows) < 2 or len(rows[0]) != 1 or len(rows[1]) != 1:
        raise ValueError('Contador de capacidad ausente')
    reserved, accounted = int(rows[0][0]), int(rows[1][0])
    return dict(reservados=reserved, sumaReservasNoLiberadas=accounted,
        discrepanciaReserva=reserved != accounted, agregados=rows[2:],
        nota='CON_ACK_ALMACENADO no revalida firmas ni demuestra recuperación en SIGER. SANEABLES es estimación, no permiso de borrado. Tablas/índices son estimación InnoDB, no tamaño total del volumen.')


def alerts(report, quota, minimum):
    if quota <= 0 or minimum < 0:
        raise ValueError('Umbrales inválidos')
    free = report['filesystem']['libreAplicacion']
    db = report.get('mysql')
    result = []
    if db and (db['reservados'] < 0 or db['discrepanciaReserva']):
        result.append('CONTABILIDAD_INCONSISTENTE')
    if free < minimum:
        result.append('ESPACIO_CRITICO')
    elif free < minimum + GIB:
        result.append('ESPACIO_PREVENTIVO')
    if db:
        used = db['reservados']
        report['mysql']['disponibleCuota'] = max(0, quota - used)
        report['mysql']['limiteEvaluado'] = quota
        if used >= quota * 95 // 100:
            result.append('CUOTA_CRITICA')
        elif used >= quota * 80 // 100:
            result.append('CUOTA_PREVENTIVA')
    else:
        result.append('RESERVAS_NO_MEDIDAS')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--store', required=True, type=Path)
    parser.add_argument('--database', help='Opcional: esquema V8; requiere acceso local Docker/MySQL')
    parser.add_argument('--container', default='siger-mysql-lab-mysql-1')
    parser.add_argument('--quota-gib', type=float, default=10)
    parser.add_argument('--min-free-gib', type=float, default=4)
    args = parser.parse_args()
    try:
        report = inventory(args.store)
        if args.database:
            report['mysql'] = database_summary(args.database, args.container)
        report['alertas'] = alerts(report, int(args.quota_gib * GIB), int(args.min_free_gib * GIB))
        report['soloLectura'] = True
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 2 if report['alertas'] else 0
    except (OSError, ValueError, subprocess.SubprocessError, OverflowError):
        print('Inventario no completado; revisar permisos, estructura, esquema y umbrales en entorno privado.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())