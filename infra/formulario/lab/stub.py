#!/usr/bin/env python3
"""SOLO LABORATORIO: sustituye nodo PISEE, ClamAV e inspector para validar la migración sin
proveedores reales ni firmas antivirus. Nunca usar en producción."""
import os
import socket
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Pisee(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        self.send_response(503)
        self.end_headers()


def antivirus():
    """TCP 3310 (healthcheck nPING) y /ipc/clamd.sock (zPING de la aplicación), como clamd."""
    def tcp():
        server = socket.create_server(('0.0.0.0', 3310))
        while True:
            conn, _ = server.accept()
            with conn:
                if conn.recv(64).strip() == b'nPING':
                    conn.sendall(b'PONG\n')
    threading.Thread(target=tcp, daemon=True).start()
    path = '/ipc/clamd.sock'
    if os.path.exists(path):
        os.unlink(path)
    server = socket.socket(socket.AF_UNIX)
    server.bind(path)
    server.listen()
    while True:
        conn, _ = server.accept()
        with conn:
            if conn.recv(64).startswith(b'zPING'):
                conn.sendall(b'PONG\0')


def inspector():
    path = '/ipc/inspector.sock'
    if os.path.exists(path):
        os.unlink(path)
    server = socket.socket(socket.AF_UNIX)
    server.bind(path)
    server.listen()
    while True:
        conn, _ = server.accept()
        with conn:
            conn.recv(512)
            conn.sendall(b'{"aislamiento":"LANDLOCK3_SECCOMP","version":1}\n')


if __name__ == '__main__':
    role = sys.argv[1]
    if role == 'pisee':
        ThreadingHTTPServer(('0.0.0.0', 8084), Pisee).serve_forever()
    elif role == 'antivirus':
        antivirus()
    elif role == 'inspector':
        inspector()
    else:
        raise SystemExit('rol desconocido')
