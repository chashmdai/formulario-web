#!/usr/bin/env python3
# Solo material sintético diminuto; ejecución secuencial, sin archivos ciudadanos.
from pathlib import Path
import subprocess,zipfile,json
import sys
out=Path(sys.argv[1] if len(sys.argv)>1 else '/out');out.mkdir(parents=True,exist_ok=True)
def run(args):subprocess.run(['ffmpeg','-v','error','-threads','1','-y']+args,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,timeout=15)
for ext in ['png','jpg','webp']:run(['-f','lavfi','-i','color=c=0x2fa9d7:s=64x64:d=0.04','-frames:v','1','-threads','1',str(out/f'imagen.{ext}')])
for ext,codec in [('mp3','libmp3lame'),('m4a','aac'),('wav','pcm_s16le'),('ogg','libopus')]:run(['-f','lavfi','-i','sine=frequency=440:sample_rate=16000:duration=1','-ac','1','-c:a',codec,'-threads','1',str(out/f'audio.{ext}')])
for ext,codec in [('mp4','libx264'),('mov','libx264'),('webm','libvpx-vp9')]:run(['-f','lavfi','-i','color=c=0x8bc53f:s=64x64:r=5:d=1','-an','-c:v',codec,'-threads','1','-pix_fmt','yuv420p',str(out/f'video.{ext}')])
(out/'texto.txt').write_text('Antecedente completamente sintetico.\n',encoding='utf-8')
def pdf(active=False):
 objects=[b'<< /Type /Catalog /Pages 2 0 R'+(b' /OpenAction 5 0 R' if active else b'')+b' >>',b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>',b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Contents 4 0 R >>',b'<< /Length 0 >>\nstream\n\nendstream']
 if active:objects.append(br'<< /S /JavaScript /JS (app.alert\(1\)) >>')
 data=b'%PDF-1.4\n';offsets=[]
 for i,obj in enumerate(objects,1):offsets.append(len(data));data+=str(i).encode()+b' 0 obj\n'+obj+b'\nendobj\n'
 start=len(data);data+=b'xref\n0 '+str(len(objects)+1).encode()+b'\n0000000000 65535 f \n'+b''.join(f'{o:010d} 00000 n \n'.encode() for o in offsets)+b'trailer\n<< /Size '+str(len(objects)+1).encode()+b' /Root 1 0 R >>\nstartxref\n'+str(start).encode()+b'\n%%EOF\n';return data
(out/'documento.pdf').write_bytes(pdf());(out/'activo.pdf').write_bytes(pdf(True));(out/'corrupto.pdf').write_bytes(b'%PDF-1.7\nno es un pdf\n%%EOF')
def office(path,ext,extra=None):
 main={'docx':('word/document.xml','http://schemas.openxmlformats.org/wordprocessingml/2006/main','document','<w:body><w:p><w:r><w:t>Sintetico</w:t></w:r></w:p></w:body>','application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml'), 'xlsx':('xl/workbook.xml','http://schemas.openxmlformats.org/spreadsheetml/2006/main','workbook','<w:sheets/>','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml'), 'pptx':('ppt/presentation.xml','http://schemas.openxmlformats.org/presentationml/2006/main','presentation','<w:sldIdLst/>','application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml')}[ext]
 name,ns,root,body,mime=main
 with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_DEFLATED) as z:
  z.writestr('[Content_Types].xml',f'<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Override PartName="/{name}" ContentType="{mime}"/></Types>')
  z.writestr('_rels/.rels',f'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="{name}"/></Relationships>')
  z.writestr(name,f'<w:{root} xmlns:w="{ns}">{body}</w:{root}>')
  if extra:
   for key,value in extra.items():z.writestr(key,value)
for ext in ['docx','xlsx','pptx']:office(out/f'oficina.{ext}',ext)
with zipfile.ZipFile(out/'oficina.odt','w') as z:
 z.writestr('mimetype','application/vnd.oasis.opendocument.text',compress_type=zipfile.ZIP_STORED);z.writestr('content.xml','<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"><office:body><office:text>Sintetico</office:text></office:body></office:document-content>')
with zipfile.ZipFile(out/'oficina.odt','a') as z:z.writestr('META-INF/manifest.xml','<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0"><manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.text"/></manifest:manifest>')
office(out/'macro.docx','docx',{'word/vbaProject.bin':b'MACRO_NO_EJECUTABLE_DE_PRUEBA'})
office(out/'externo.docx','docx',{'word/_rels/document.xml.rels':'<Relationships><Relationship TargetMode="External" Target="https://example.invalid/"/></Relationships>'})
office(out/'traversal.docx','docx',{'../outside.txt':'no debe extraerse'})
with zipfile.ZipFile(out/'falso.docx','w') as z:z.writestr('[Content_Types].xml','<Types/>');z.writestr('word/document.xml','<document/>')
(out/'falso.png').write_bytes(b'\x89PNG\r\n\x1a\nINVALIDO');(out/'corrupto.mp4').write_bytes(b'\x00\x00\x00\x18ftypisomINVALIDO');(out/'corrupto.mp3').write_bytes(b'ID3INVALIDO');(out/'imagen.jpeg').write_bytes((out/'imagen.jpg').read_bytes())
office(out/'enlace-falso.docx','docx',{'word/_rels/document.xml.rels':'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="bad" Type="hyperlink" Target="https://example.invalid/"/></Relationships>'})
with zipfile.ZipFile(out/'oficina.odt') as src,zipfile.ZipFile(out/'externo.odt','w') as dst:
 for entry in src.infolist():
  data=src.read(entry)
  if entry.filename=='content.xml':data=b'<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:xlink="http://www.w3.org/1999/xlink"><office:body xlink:href="https://example.invalid/a"/></office:document-content>'
  dst.writestr(entry,data)

office(out/'formula.xlsx','xlsx',{'xl/worksheets/sheet1.xml':'<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row><c><f>1+1</f></c></row></sheetData></worksheet>'})
with zipfile.ZipFile(out/'oficina.odt') as src,zipfile.ZipFile(out/'objeto.odt','w') as dst:
 for entry in src.infolist():
  data=src.read(entry)
  if entry.filename=='content.xml':data=b'<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"><office:body><draw:object-ole><office:binary-data>U0lOVEVUSUNP</office:binary-data></draw:object-ole></office:body></office:document-content>'
  dst.writestr(entry,data)
print('Generados',len(list(out.iterdir())),'archivos sintéticos pequeños.')
