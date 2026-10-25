#!/usr/bin/env python3
"""Respaldo/restauración local verificada. Ejecutar con acceso administrativo a Docker.
No elimina bases, directorios ni custodias. Los destinos deben ser nuevos y privados.
"""
import argparse,contextlib,fcntl,hashlib,json,os,re,shutil,stat,subprocess,sys,uuid,time
from pathlib import Path
FOLDERS={'objects','derived','chunks','incoming'}
def digest(path):
 h=hashlib.sha256()
 with path.open('rb') as stream:
  while block:=stream.read(65536):h.update(block)
 return h.hexdigest()
def safe(path):
 path=path.absolute()
 for part in [path,*path.parents]:
  if part.is_symlink() or (part/'.git').exists():raise ValueError('Ruta privada fuera de Git y sin enlaces requerida')
 return path
def sync(path):
 fd=os.open(path,os.O_RDONLY)
 try:os.fsync(fd)
 finally:os.close(fd)
def identifier(value):
 if not re.fullmatch(r'[a-z][a-z0-9_]{0,62}',value):raise ValueError('Identificador SQL inválido')
 return value
def command(args,sql):
 result=subprocess.run(['docker','exec','-i',args.container,'mysql','--defaults-extra-file=/run/secrets/root.cnf','--batch','--raw','--skip-column-names'],input=sql.encode(),capture_output=True)
 if result.returncode:raise RuntimeError('Operación MySQL fallida; diagnóstico privado omitido')
 return result.stdout.decode().strip().splitlines()
def copy(source,target):
 if not source.is_file() or source.is_symlink():raise ValueError('Archivo regular requerido')
 with source.open('rb') as src,target.open('xb') as out:
  shutil.copyfileobj(src,out,65536);out.flush();os.fsync(out.fileno())
 os.chmod(target,0o400)
def inspect_tree(root):
 result=[]
 for folder in sorted(FOLDERS):
  directory=root/folder
  if not directory.is_dir() or directory.is_symlink():raise ValueError('Estructura de almacén incompleta')
  for base,dirs,files in os.walk(directory,followlinks=False):
   for name in dirs:
    if (Path(base)/name).is_symlink():raise ValueError('Enlace inesperado')
   for name in files:
    path=Path(base)/name
    if not path.is_file() or path.is_symlink():raise ValueError('Archivo inesperado')
    result.append(path)
 return sorted(result)
def verify_database(args,db,root):
 # V8 sigue restaurable. V9 conserva referencias/firmas pero marca bytes transitorios retirados.
 has_retired=command(args,f"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='{db}' AND table_name='citizen_attachment' AND column_name='bytes_retired';")==['1']
 retained=" AND bytes_retired=FALSE" if has_retired else ""
 if has_retired:
  invalid=command(args,f"SELECT COUNT(*) FROM `{db}`.citizen_attachment a LEFT JOIN `{db}`.storage_retirement r ON r.attachment_id=a.id WHERE a.bytes_retired=TRUE AND (r.attachment_id IS NULL OR r.phase<>'FINALIZADA' OR r.journey_id<>a.journey_id);")
  if invalid!=['0']:raise ValueError('Retirada sin intención finalizada; no se omite verificación del original')
  invalid=command(args,f"SELECT COUNT(*) FROM `{db}`.citizen_attachment a JOIN `{db}`.media_upload u ON u.id=a.id WHERE a.bytes_retired=TRUE AND u.released=FALSE;")
  if invalid!=['0']:raise ValueError('Retirada con reserva no liberada; respaldo no consistente')
 for row in command(args,f"SELECT id,expected_size,expected_sha256 FROM `{db}`.media_upload WHERE state='VALIDADO' AND released=FALSE;"):
  key,size,sha=row.split('\t');uuid.UUID(key);path=root/'objects'/key
  if not path.is_file() or path.is_symlink() or path.stat().st_size!=int(size) or digest(path)!=sha:raise ValueError('Carga validada no íntegra')
 # Las referencias de archivo se verifican contra el original/derivada restaurados.
 for row in command(args,f"SELECT storage_key,size_bytes,sha256 FROM `{db}`.citizen_attachment WHERE storage_kind='FILE'{retained};"):
  key,size,sha=row.split('\t');uuid.UUID(key);path=root/'objects'/key
  if not path.is_file() or path.is_symlink() or path.stat().st_size!=int(size) or digest(path)!=sha:raise ValueError('Original referenciado no íntegro')
 for row in command(args,f"SELECT id,preview_size,preview_sha256 FROM `{db}`.media_upload WHERE state='VALIDADO' AND released=FALSE AND preview_sha256 IS NOT NULL;"):
  key,size,sha=row.split('\t');uuid.UUID(key);path=root/'derived'/key
  if not path.is_file() or path.is_symlink() or path.stat().st_size!=int(size) or digest(path)!=sha:raise ValueError('Derivada referenciada no íntegra')
 bad=command(args,f"SELECT COUNT(*) FROM `{db}`.citizen_attachment WHERE storage_kind='MYSQL'{retained} AND (content IS NULL OR OCTET_LENGTH(content)<>size_bytes OR LOWER(SHA2(content,256))<>sha256);")
 if bad!=['0']:raise ValueError('Adjunto MySQL histórico no íntegro')
 return {'originalesArchivo':int(command(args,f"SELECT COUNT(*) FROM `{db}`.citizen_attachment WHERE storage_kind='FILE'{retained};")[0]),'adjuntosMySQL':int(command(args,f"SELECT COUNT(*) FROM `{db}`.citizen_attachment WHERE storage_kind='MYSQL';")[0]),'comunicaciones':int(command(args,f"SELECT COUNT(*) FROM `{db}`.citizen_submission;")[0])}
def backup(args):
 root=safe(Path(args.store));dest=safe(Path(args.destination));db=identifier(args.database)
 if dest.exists() or root==dest or root in dest.parents:raise ValueError('Destino nuevo fuera del almacén requerido')
 dest.mkdir(mode=0o700,parents=False)
 with (root/'instance.lock').open('a+b') as instance,(root/'backup.lock').open('a+b') as lock:
  fcntl.lockf(instance,fcntl.LOCK_EX|fcntl.LOCK_NB) # exige aplicación detenida; nunca copia una BD activa junto a archivos mutando
  fcntl.lockf(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
  with (dest/'database.sql').open('xb') as sql:
   result=subprocess.run(['docker','exec',args.container,'mysqldump','--defaults-extra-file=/run/secrets/root.cnf','--single-transaction','--quick','--hex-blob','--no-tablespaces','--skip-add-drop-table','--skip-triggers','--set-gtid-purged=OFF','--column-statistics=0',db],stdout=sql,stderr=subprocess.PIPE)
   sql.flush();os.fsync(sql.fileno())
   if result.returncode:raise RuntimeError('Respaldo SQL no completado; conservar destino incompleto para diagnóstico privado')
  files=[]
  for folder in FOLDERS:(dest/'store'/folder).mkdir(mode=0o700,parents=True,exist_ok=True)
  for source in inspect_tree(root):
   relative=source.relative_to(root);target=dest/'store'/relative;target.parent.mkdir(mode=0o700,parents=True,exist_ok=True);copy(source,target)
   files.append({'path':relative.as_posix(),'size':target.stat().st_size,'sha256':digest(target)})
  counts=verify_database(args,db,root)
  manifest={'version':1,'owner':{'uid':root.stat().st_uid,'gid':root.stat().st_gid},'database':db,'sqlSha256':digest(dest/'database.sql'),'files':files,'counts':counts,'keys':'EXCLUIDAS: custodiar separadamente firma y confianza históricas'}
  with (dest/'manifest.json').open('x') as out:json.dump(manifest,out,ensure_ascii=False,indent=2);out.flush();os.fsync(out.fileno())
  for directory,_,_ in os.walk(dest,topdown=False):sync(Path(directory))
  sync(dest.parent)
 print(json.dumps({'respaldo':'VERIFICADO','archivos':len(files),'conteos':counts}))
def restore(args):
 source=safe(Path(args.source));dest=safe(Path(args.destination));db=identifier(args.database)
 if dest.exists():raise ValueError('La restauración requiere directorio NUEVO')
 manifest=json.loads((source/'manifest.json').read_text())
 if manifest.get('version')!=1 or (source/'database.sql').is_symlink() or digest(source/'database.sql')!=manifest['sqlSha256']:raise ValueError('Respaldo SQL no íntegro')
 seen=set()
 for entry in manifest['files']:
  rel=Path(entry['path'])
  if rel.is_absolute() or '..' in rel.parts or len(rel.parts)<2 or rel.parts[0] not in FOLDERS or str(rel) in seen:raise ValueError('Manifiesto inválido')
  seen.add(str(rel));path=safe(source/'store'/rel)
  if not path.is_file() or path.stat().st_size!=entry['size'] or digest(path)!=entry['sha256']:raise ValueError('Respaldo binario no íntegro')
 if command(args,f"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='{db}';")!=['0']:raise ValueError('La restauración requiere base NUEVA; no se sobrescribe')
 dest.mkdir(mode=0o700)
 for folder in FOLDERS:(dest/folder).mkdir(mode=0o700)
 for entry in manifest['files']:
  rel=Path(entry['path']);target=dest/rel;target.parent.mkdir(mode=0o700,parents=True,exist_ok=True);copy(source/'store'/rel,target)
 command(args,f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
 with (source/'database.sql').open('rb') as sql:
  result=subprocess.run(['docker','exec','-i',args.container,'mysql','--defaults-extra-file=/run/secrets/root.cnf',db],stdin=sql,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
 if result.returncode:raise RuntimeError('Importación no completada; base nueva conservada para diagnóstico')
 owner=manifest['owner'];uid,gid=int(owner['uid']),int(owner['gid'])
 if uid<=0 or gid<=0:raise ValueError('Propietario de aplicación sin privilegios requerido')
 for directory,_,files in os.walk(dest):
  os.chown(directory,uid,gid)
  for file in files:os.chown(Path(directory)/file,uid,gid)
 counts=verify_database(args,db,dest)
 if counts!=manifest['counts']:raise ValueError('Conteos de restauración difieren')
 for directory,_,_ in os.walk(dest,topdown=False):sync(Path(directory))
 sync(dest.parent)
 print(json.dumps({'restauracion':'VERIFICADA','archivos':len(seen),'conteos':counts}))
def clean_incoming(args):
 root=safe(Path(args.store))
 if args.hours<24:raise ValueError('Margen mínimo de 24 horas requerido')
 with (root/'instance.lock').open('a+b') as instance,(root/'backup.lock').open('a+b') as lock:
  fcntl.lockf(instance,fcntl.LOCK_EX|fcntl.LOCK_NB);fcntl.lockf(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
  incoming=root/'incoming';candidates=[]
  for path in incoming.iterdir():
   if path.is_symlink() or not path.is_file() or not re.fullmatch(r'carga-[A-Za-z0-9_-]+\.part',path.name):raise ValueError('Temporal inesperado; se conserva para revisión')
   if path.stat().st_mtime<time.time()-args.hours*3600:candidates.append(path)
  size=sum(path.stat().st_size for path in candidates)
  if args.apply:
   for path in candidates:path.unlink()
   sync(incoming)
 print(json.dumps({'temporalesHuerfanos':len(candidates),'bytes':size,'aplicado':args.apply,'originalesEliminados':0}))
def main():
 os.umask(0o077);parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--container',default='siger-mysql-lab-mysql-1');sub=parser.add_subparsers(dest='mode',required=True)
 b=sub.add_parser('backup');b.add_argument('--store',required=True);b.add_argument('--destination',required=True);b.add_argument('--database',required=True)
 r=sub.add_parser('restore');r.add_argument('--source',required=True);r.add_argument('--destination',required=True);r.add_argument('--database',required=True)
 c=sub.add_parser('clean-incoming');c.add_argument('--store',required=True);c.add_argument('--hours',type=int,default=24);c.add_argument('--apply',action='store_true')
 args=parser.parse_args()
 try:{'backup':backup,'restore':restore,'clean-incoming':clean_incoming}[args.mode](args)
 except (OSError,ValueError,RuntimeError,KeyError) as error:print('Operación detenida:',str(error),file=sys.stderr);return 1
 return 0
if __name__=='__main__':raise SystemExit(main())