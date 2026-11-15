#!/usr/bin/env python3
"""Paquete de release reproducible para Linux, sin secretos ni material privado.

Construye (o reutiliza) la imagen de la aplicación desde un commit LIMPIO mediante `git archive`,
la exporta con `docker save` y agrega los scripts e infraestructura versionados del mismo commit:

  release-<sha12>/
    imagen-app.tar.gz          imagen inmutable (docker load)
    fuente/                    scripts/, infra/ubuntu/, docs/ACTUALIZACION_UBUNTU_PRODUCCION.md del commit
    manifiesto.json            commit, ID de imagen, digests base, versión de esquema Flyway
    SHA256SUMS                 sha256sum -c SHA256SUMS

Nunca incluye `.local/`, `.codex/`, `target/`, secretos, claves ni documentos sin versionar: todo
proviene de `git archive <commit>`. El destino debe ser NUEVO y estar fuera de Git.
"""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import tempfile

REPO = Path(__file__).resolve().parents[2]
INCLUDE = ['scripts', 'infra/ubuntu', 'infra/sandbox', 'infra/media', 'docs/ACTUALIZACION_UBUNTU_PRODUCCION.md',
           'docs/INSTALACION_UBUNTU_DOCKER.md', 'README.md']


def run(*args, **kwargs):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs)
    if result.returncode:
        raise RuntimeError('Falló ' + args[0] + ' ' + ' '.join(args[1:2]) + ': ' + result.stderr.decode(errors='replace')[-400:])
    return result.stdout


def sha256(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def destination(path):
    target = Path(path).absolute()
    if target.exists() or any(p.is_symlink() or (p / '.git').exists() for p in [target.parent, *target.parent.parents]):
        raise ValueError('Destino NUEVO fuera de Git y sin enlaces requerido')
    return target


def bases(dockerfile_text):
    return sorted(set(re.findall(r'^FROM\s+(\S+)', dockerfile_text, re.MULTILINE)))


def main():
    os.umask(0o022)
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--commit', default='HEAD')
    p.add_argument('--destino', required=True)
    p.add_argument('--imagen', help='Imagen ya construida desde ESE commit (si se omite, se construye)')
    a = p.parse_args()
    try:
        commit = run('git', '-C', str(REPO), 'rev-parse', '--verify', a.commit + '^{commit}').decode().strip()
        target = destination(a.destino)
        with tempfile.TemporaryDirectory(prefix='release-') as tmp:
            source = Path(tmp) / 'fuente-completa'
            source.mkdir()
            archive = run('git', '-C', str(REPO), 'archive', '--format=tar', commit)
            with tarfile.open(fileobj=__import__('io').BytesIO(archive)) as tar:
                tar.extractall(source, filter='data')
            image = a.imagen or 'ddn-formulario:' + commit[:12]
            if not a.imagen:
                run('docker', 'build', '--build-arg', 'SOURCE_REVISION=' + commit, '-t', image, str(source))
            labels = json.loads(run('docker', 'image', 'inspect', '--format', '{{json .Config.Labels}}', image))
            if (labels or {}).get('org.opencontainers.image.revision') != commit:
                raise ValueError('La imagen no declara el commit solicitado; reconstruir sin --imagen')
            image_id = run('docker', 'image', 'inspect', '--format', '{{.Id}}', image).decode().strip()
            target.mkdir()
            saved = target / 'imagen-app.tar.gz'
            with saved.open('xb') as raw, gzip.GzipFile(fileobj=raw, mode='wb', compresslevel=6, mtime=0) as out:
                proc = subprocess.Popen(['docker', 'save', image], stdout=subprocess.PIPE)
                shutil.copyfileobj(proc.stdout, out, 1 << 20)
                if proc.wait():
                    raise RuntimeError('docker save falló')
            for name in INCLUDE:
                src = source / name
                if src.is_dir():
                    shutil.copytree(src, target / 'fuente' / name, symlinks=False)
                elif src.is_file():
                    (target / 'fuente' / name).parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(src, target / 'fuente' / name)
            migrations = sorted(int(m.group(1)) for f in (source / 'formulario-publico/backend/src/main/resources/db/publico/mysql').iterdir()
                                if (m := re.match(r'V(\d+)__', f.name)))
            manifest = {'schema': 'DDN_RELEASE_V1', 'commit': commit, 'imagen': image, 'imagenId': image_id,
                        'basesDockerfile': bases((source / 'Dockerfile').read_text()),
                        'mysqlCompose': re.search(r'image:\s*(mysql:\S+)', (source / 'infra/ubuntu/compose.yml').read_text()).group(1),
                        'flywayMaxima': max(migrations), 'contieneSecretos': False}
            (target / 'manifiesto.json').write_text(json.dumps(manifest, indent=2) + '\n')
            files = sorted(f for f in target.rglob('*') if f.is_file())
            (target / 'SHA256SUMS').write_text(''.join(f'{sha256(f)}  {f.relative_to(target)}\n' for f in files))
        print(json.dumps({'release': str(target), **manifest, 'archivos': len(files) + 1}, indent=2))
        return 0
    except (OSError, ValueError, RuntimeError, tarfile.TarError) as error:
        # Un paquete incompleto nunca debe confundirse con uno válido: sin SHA256SUMS no se distribuye.
        print('Empaquetado detenido (paquete incompleto, no distribuir): ' + str(error))
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
