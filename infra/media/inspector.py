"""Inspector local sin originales compartidos. Entrada binaria acotada por socket Unix privado."""
import posixpath
from urllib.parse import urlsplit,unquote
import base64,codecs,json,os,re,signal,socket,stat,subprocess,sys,tempfile,time,zipfile
from pathlib import Path
from defusedxml import ElementTree as ET
MAX=150*1024*1024
TYPES={'pdf':'application/pdf','txt':'text/plain','docx':'application/vnd.openxmlformats-officedocument.wordprocessingml.document','xlsx':'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','pptx':'application/vnd.openxmlformats-officedocument.presentationml.presentation','odt':'application/vnd.oasis.opendocument.text','png':'image/png','jpg':'image/jpeg','jpeg':'image/jpeg','webp':'image/webp','mp3':'audio/mpeg','m4a':'audio/mp4','wav':'audio/wav','ogg':'audio/ogg','mp4':'video/mp4','mov':'video/quicktime','webm':'video/webm'}
class Invalid(Exception):pass
def command(args,job,max_output=8*1024*1024):
 with tempfile.TemporaryFile(dir=job) as output,tempfile.TemporaryFile(dir=job) as error:
  run=subprocess.run(args,stdout=output,stderr=error,timeout=35,check=False)
  if run.returncode or output.tell()>max_output:raise Invalid()
  output.seek(0);return output.read(max_output+1)
def inspect(job,ext):
 source=job/'input';preview=None;kind='DESCARGA'
 if ext=='txt':
  decoder=codecs.getincrementaldecoder('utf-8')('strict')
  with source.open('rb') as f:
   while data:=f.read(65536):
    text=decoder.decode(data)
    if any(ord(c)<32 and c not in '\t\r\n' or ord(c)==127 for c in text):raise Invalid()
   decoder.decode(b'',final=True)
 elif ext in ('docx','xlsx','pptx','odt'):
  with source.open('rb') as header:
   if header.read(4)!=b'PK\x03\x04':raise Invalid()
  with zipfile.ZipFile(source) as archive:
   entries=archive.infolist();names=[e.filename for e in entries]
   if len(entries)>2000 or len(set(names))!=len(names) or sum(e.file_size for e in entries)>100*1024*1024:raise Invalid()
   required={'docx':'word/document.xml','xlsx':'xl/workbook.xml','pptx':'ppt/presentation.xml','odt':'content.xml'}[ext]
   if required not in names:raise Invalid()
   if archive.getinfo(required).file_size>8*1024*1024 or ext!='odt' and ('[Content_Types].xml' not in names or archive.getinfo('[Content_Types].xml').file_size>8*1024*1024):raise Invalid()
   main=ET.fromstring(archive.read(required))
   roots={'docx':'{http://schemas.openxmlformats.org/wordprocessingml/2006/main}document','xlsx':'{http://schemas.openxmlformats.org/spreadsheetml/2006/main}workbook','pptx':'{http://schemas.openxmlformats.org/presentationml/2006/main}presentation','odt':'{urn:oasis:names:tc:opendocument:xmlns:office:1.0}document-content'}
   if main.tag!=roots[ext]:raise Invalid()
   if ext!='odt':
    ctypes=ET.fromstring(archive.read('[Content_Types].xml'))
    expected={'docx':'application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml','xlsx':'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml','pptx':'application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml'}[ext]
    if ctypes.tag!='{http://schemas.openxmlformats.org/package/2006/content-types}Types' or not any(n.attrib.get('PartName')=='/'+required and n.attrib.get('ContentType')==expected for n in ctypes):raise Invalid()
    if '_rels/.rels' not in names or archive.getinfo('_rels/.rels').file_size>8*1024*1024:raise Invalid()
    rels=ET.fromstring(archive.read('_rels/.rels'))
    if not any(n.attrib.get('Type','').endswith('/officeDocument') and n.attrib.get('Target','').lstrip('/')==required for n in rels):raise Invalid()
   if ext=='odt':
    if 'META-INF/manifest.xml' not in names or archive.getinfo('META-INF/manifest.xml').file_size>8*1024*1024:raise Invalid()
    if not entries or entries[0].filename!='mimetype' or entries[0].compress_type!=0 or archive.read('mimetype')!=b'application/vnd.oasis.opendocument.text':raise Invalid()
   elif '[Content_Types].xml' not in names:raise Invalid()
   for entry in entries:
    name=entry.filename.lower()
    if name.startswith('/') or '..' in name or '\\' in name or entry.flag_bits&1 or stat.S_ISLNK(entry.external_attr>>16) or any(x in name for x in ('vbaproject','activex/','embeddings/','scripts/','externallinks/','connections.xml','macrosheets/')) or name.endswith(('.exe','.dll','.bin','.js','.vbs','.html','.htm','.svg')):raise Invalid()
    if entry.file_size>50*1024*1024 or entry.file_size>max(1,entry.compress_size)*200:raise Invalid()
    if name.endswith(('.xml','.rels')):
     if entry.file_size>8*1024*1024:raise Invalid()
     content=archive.read(entry);tree=ET.fromstring(content)
     if name.endswith('.rels'):
      if tree.tag!='{http://schemas.openxmlformats.org/package/2006/relationships}Relationships':raise Invalid()
      base=entry.filename.rsplit('/_rels/',1)[0] if '/_rels/' in entry.filename else ''
      for rel in tree:
       target=unquote(rel.attrib.get('Target',''));uri=urlsplit(target)
       if not target or uri.scheme or uri.netloc or uri.query or '\\' in target or any(ord(c)<32 for c in target):raise Invalid()
       resolved=posixpath.normpath(uri.path.lstrip('/') if uri.path.startswith('/') else posixpath.join(base,uri.path))
       if resolved.startswith('../') or resolved=='..' or resolved not in names:raise Invalid()
     for node in tree.iter():
      for attr,value in node.attrib.items():
       if attr.split('}')[-1].lower() in ('href','src'):
        uri=urlsplit(unquote(value.strip()))
        if uri.scheme or uri.netloc or uri.path.startswith(('/', '\\')) or '..' in uri.path.split('/'):raise Invalid()
      local=node.tag.split('}')[-1];
      if local.lower() in ('object','object-ole','oleobj','binary-data','control','customui','externaldata') or ext=='xlsx' and local=='f':raise Invalid()
      joined=''.join(node.itertext())+' '.join(node.attrib.values());normalized=''.join(joined.lower().split())
      if local in ('script','event-listener') or local=='scripts' and (len(node)>0 or (node.text or '').strip()) or local in ('f','instrText','fldSimple') and any(token in normalized for token in ('dde','webservice','includetext','includepicture','database','link','http:','https:','file:','|')):raise Invalid()
     if any(v.lower()=='external' for node in tree.iter() for k,v in node.attrib.items() if k.endswith('TargetMode')):raise Invalid()
     low=content.lower()
     if any(x in low for x in (b'macroenabled',b'macrosheet',b'oleobject',b'javascript',b'vbaproject',b'ddeauto',b'includetext',b'webservice(',b'dde(',b'script:')):raise Invalid()
    else:
     with archive.open(entry) as f:
      while f.read(65536):pass
 elif ext=='pdf':
  command(['qpdf','--check',str(source)],job)
  encrypted=subprocess.run(['qpdf','--is-encrypted',str(source)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=10)
  if encrypted.returncode!=2:raise Invalid()
  tree=json.loads(command(['qpdf','--json','--json-stream-data=none',str(source)],job))
  forbidden={'/JavaScript','/JS','/Launch','/EmbeddedFiles','/RichMedia','/XFA','/AA','/OpenAction','/SubmitForm','/ImportData','/GoToR','/Sound','/Movie'}
  def walk(obj):
   if isinstance(obj,dict):
    if forbidden.intersection(obj):raise Invalid()
    for value in obj.values():walk(value)
   elif isinstance(obj,list):
    for value in obj:walk(value)
   elif isinstance(obj,str) and obj in forbidden:raise Invalid()
  walk(tree)
  try:
   command(['pdftoppm','-f','1','-l','1','-singlefile','-scale-to','1024','-png',str(source),str(job/'preview')],job,max_output=1024)
   preview=(job/'preview.png').read_bytes();kind='IMAGEN_DERIVADA'
  except Exception:preview=None
 else:
  data=json.loads(command(['ffprobe','-v','error','-protocol_whitelist','file,pipe','-threads','1','-show_entries','format=format_name,duration:stream=codec_type,codec_name,width,height,channels,sample_rate','-of','json',str(source)],job,65536))
  streams=data.get('streams',[]);formats=set(data.get('format',{}).get('format_name','').split(','))
  if not streams or len(streams)>3:raise Invalid()
  if ext in ('png','jpg','jpeg','webp'):
   expected={'png':('png','png_pipe'),'jpg':('mjpeg','jpeg_pipe'),'jpeg':('mjpeg','jpeg_pipe'),'webp':('webp','webp_pipe')}[ext]
   if len(streams)!=1 or streams[0].get('codec_name')!=expected[0] or streams[0].get('width',0)*streams[0].get('height',0) not in range(1,24000001):raise Invalid()
   command(['ffmpeg','-v','error','-xerror','-protocol_whitelist','file,pipe','-threads','1','-i',str(source),'-frames:v','1','-vf','scale=1024:1024:force_original_aspect_ratio=decrease','-threads','1',str(job/'preview.png')],job,1024)
   preview=(job/'preview.png').read_bytes();kind='IMAGEN_DERIVADA'
  else:
   containers={'mp3':{'mp3'},'wav':{'wav'},'ogg':{'ogg'},'m4a':{'mov','mp4','m4a'},'mp4':{'mov','mp4'},'mov':{'mov','mp4'},'webm':{'matroska','webm'}}[ext]
   if not containers.intersection(formats):raise Invalid()
   duration=float(data.get('format',{}).get('duration','nan'));video=ext in ('mp4','mov','webm')
   if not 0<duration<=(180 if video else 1800):raise Invalid()
   for stream in streams:
    typ=stream.get('codec_type');codec=stream.get('codec_name')
    if typ=='video':
     if not video or codec not in ('h264','vp8','vp9') or not 0<stream.get('width',0)*stream.get('height',0)<=2073600:raise Invalid()
    elif typ=='audio':
     if codec not in ('mp3','aac','opus','vorbis','pcm_s16le','pcm_s24le','pcm_u8') or not 0<int(stream.get('channels',0))<=2 or not 0<int(stream.get('sample_rate',0))<=96000:raise Invalid()
    else:raise Invalid()
   if video and not any(s.get('codec_type')=='video' for s in streams):raise Invalid()
   command(['ffmpeg','-v','error','-xerror','-protocol_whitelist','file,pipe','-threads','1','-i',str(source),'-map','0','-threads','1','-f','null','-'],job,1024)
   kind='VIDEO' if video else 'AUDIO'
 result={'tipo':TYPES[ext],'vista':kind}
 if preview:
  if len(preview)>2*1024*1024 or not preview.startswith(b'\x89PNG\r\n\x1a\n'):result['vista']='DESCARGA'
  else:result['png']=base64.b64encode(preview).decode()
 return result

def isolation_test():
 with tempfile.TemporaryDirectory(dir='/scratch') as directory:
  job=Path(directory);outside=Path('/scratch/isolation-proof');outside.write_text('controlado')
  code="import socket;from pathlib import Path\ntry:Path('/scratch/isolation-proof').read_bytes();raise SystemExit(2)\nexcept PermissionError:pass\ntry:socket.socket();raise SystemExit(3)\nexcept PermissionError:pass\nprint('AISLADO')"
  run=subprocess.run(['/opt/sandbox',str(job),'python3','-c',code],capture_output=True,timeout=5)
  outside.unlink()
  if run.returncode or run.stdout.strip()!=b'AISLADO':raise RuntimeError('Aislamiento obligatorio no disponible')
def serve():
 isolation_test()
 os.umask(0o077);ipc=Path('/ipc/inspector.sock');ipc.unlink(missing_ok=True)
 with socket.socket(socket.AF_UNIX) as server:
  server.bind(str(ipc));os.chmod(ipc,0o660);server.listen(2)
  while True:
   connection,_=server.accept()
   with connection:
    connection.settimeout(90)
    try:
     header=bytearray()
     while not header.endswith(b'\n'):
      byte=connection.recv(1)
      if not byte or len(header)>=2048:raise Invalid()
      header.extend(byte)
     request=json.loads(header)
     if request=={'salud':True}:
      connection.sendall(b'{"aislamiento":"LANDLOCK3_SECCOMP","version":1}\n');continue
     ext=request['extension'];size=request['tamanio']
     if ext not in TYPES or not isinstance(size,int) or not 0<size<=MAX:raise Invalid()
     with tempfile.TemporaryDirectory(dir='/scratch') as directory:
      job=Path(directory)
      with (job/'input').open('wb') as out:
       remaining=size
       while remaining:
        block=connection.recv(min(65536,remaining))
        if not block:raise Invalid()
        out.write(block);remaining-=len(block)
      with (job/'stdout').open('wb') as output,(job/'stderr').open('wb') as error:
       process=subprocess.Popen(['/opt/sandbox',str(job),'python3','/opt/inspector.py','--inspect',str(job),ext],stdout=output,stderr=error,stdin=subprocess.DEVNULL,start_new_session=True,env={'PATH':'/usr/bin:/bin','LANG':'C.UTF-8','TMPDIR':str(job),'PYTHONDONTWRITEBYTECODE':'1'})
       try:process.wait(timeout=50)
       finally:
        try:os.killpg(process.pid,signal.SIGKILL)
        except ProcessLookupError:pass
       if process.returncode or (job/'stdout').stat().st_size>3*1024*1024:raise Invalid()
      response=(job/'stdout').read_bytes();json.loads(response);connection.sendall(response+b'\n')
    except Exception:
     try:connection.sendall(b'{"error":"INSPECCION_RECHAZADA"}\n')
     except OSError:pass
if __name__=='__main__':
 if len(sys.argv)==4 and sys.argv[1]=='--inspect':
  try:print(json.dumps(inspect(Path(sys.argv[2]),sys.argv[3]),separators=(',',':')))
  except Exception:sys.exit(1)
 else:serve()
