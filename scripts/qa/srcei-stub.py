#!/usr/bin/env python3
"""Proveedor de test sin egreso: SOLO RUN sintético 1-9. No utilizar con producción."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import xml.etree.ElementTree as ET

class Stub(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def do_POST(self):
        try:
            size = int(self.headers.get('Content-Length', '0'))
            if self.path != '/srcei/informacionPersonalBasico' or not 0 < size <= 2048:
                self.send_error(400); return
            raw = self.rfile.read(size)
            if b'<!' in raw: raise ValueError()
            root = ET.fromstring(raw)
            if root.tag != 'documento': raise ValueError()
            number, dv = root.findtext('entradaSRCel/run/numero'), root.findtext('entradaSRCel/run/dv')
            if (number, dv) != ('1', '9'):
                body = b'<documento><datosPersona/><glosa>No existe informacion disponible.</glosa></documento>'
            else: body = Path('/test/response.xml').read_bytes()
            self.send_response(200)
            self.send_header('Content-Type', 'application/xml; charset=utf-8')
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers(); self.wfile.write(body)
        except (ValueError, ET.ParseError): self.send_error(400)

if __name__ == '__main__': ThreadingHTTPServer(('0.0.0.0', 8084), Stub).serve_forever()
