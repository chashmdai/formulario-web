#!/usr/bin/env python3
"""CA de desarrollo y certificado de 30 días. No instala confianza ni modifica hosts."""
import argparse
import os
from pathlib import Path
import subprocess

p = argparse.ArgumentParser()
p.add_argument("destino", type=Path)
args = p.parse_args()
target = args.destino.expanduser().resolve()
if target.exists() or any((d / ".git").exists() for d in [target, *target.parents]):
    raise SystemExit("Elegir un directorio privado nuevo fuera de Git; no regenerar una CA existente")
os.umask(0o077)
target.mkdir(parents=True, mode=0o700)
def run(*args):
    subprocess.run(["openssl", *args], cwd=target, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
domain = "claveunica.formularioadulto.defensorianinez.gob.cl"
run("genpkey", "-algorithm", "EC", "-pkeyopt", "ec_paramgen_curve:prime256v1", "-out", "rootCA-key.pem")
run("req", "-new", "-x509", "-key", "rootCA-key.pem", "-out", "rootCA.pem", "-days", "365", "-sha256",
    "-subj", "/CN=Nexbyte SIGER Sandbox local CA", "-addext", "basicConstraints=critical,CA:TRUE,pathlen:0",
    "-addext", "keyUsage=critical,keyCertSign,cRLSign", "-addext", "subjectKeyIdentifier=hash")
run("req", "-new", "-newkey", "rsa:2048", "-nodes", "-keyout", "site-key.pem", "-out", "site.csr", "-subj", "/CN=" + domain)
(target / "site.ext").write_text("basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\n"
    "extendedKeyUsage=serverAuth\nsubjectKeyIdentifier=hash\nauthorityKeyIdentifier=keyid,issuer\nsubjectAltName=DNS:" + domain + "\n")
run("x509", "-req", "-in", "site.csr", "-CA", "rootCA.pem", "-CAkey", "rootCA-key.pem", "-CAcreateserial",
    "-out", "site.pem", "-days", "30", "-sha256", "-extfile", "site.ext")
run("verify", "-CAfile", "rootCA.pem", "-verify_hostname", domain, "site.pem")
for f in target.iterdir(): f.chmod(0o600)
print("CA local y certificado SAN exacto generados y verificados; sin instalar confianza ni revelar claves.")
