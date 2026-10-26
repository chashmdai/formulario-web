#!/usr/bin/env python3
"""Copias append-only de ejecución Linux. Compilar nunca instala ni selecciona una versión."""
import contextlib
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import socket
import stat
import subprocess
import tempfile
import zipfile


def sha256(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def opciones(parser):
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument("--estado", action="store_true", help="Consultar PID, JAR y hashes; no arranca")
    actions.add_argument("--instalar", type=Path, metavar="JAR", help="Preparar copia; no seleccionar ni arrancar")
    actions.add_argument("--seleccionar", metavar="SHA256", help="Seleccionar copia instalada con el laboratorio detenido")
    actions.add_argument("--detener", action="store_true", help="SIGTERM solo al proceso identificado")
    parser.add_argument("--jar", type=Path, help="Instalar y seleccionar explícitamente antes de arrancar")
    parser.add_argument("--migrar", action="store_true", help="Migración explícita previa; nunca implícita")


class RuntimeJar:
    def __init__(self, private, port):
        supplied = Path(private).expanduser().absolute()
        if any(p.is_symlink() for p in (supplied, *supplied.parents)):
            raise ValueError("El directorio privado no admite enlaces")
        self.private = supplied.resolve()
        if (not self.private.is_dir() or self.private.stat().st_uid != os.getuid()
                or self.private.stat().st_mode & 0o077
                or any((p / ".git").exists() for p in (self.private, *self.private.parents))
                or "target" in self.private.parts):
            raise ValueError("Directorio privado propio, 0700, fuera de Git/target requerido")
        self.port = port
        self.config = self.private / "application.properties"
        self._regular(self.config)
        self.releases = self.private / "jars"

    def _regular(self, path):
        st = path.lstat()
        if not stat.S_ISREG(st.st_mode) or st.st_uid != os.getuid() or st.st_nlink != 1:
            raise ValueError("Archivo regular propio sin enlaces requerido")

    @contextlib.contextmanager
    def lock(self):
        fd = os.open(self.private / "launcher.lock", os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            yield
        finally:
            os.close(fd)

    def _sync_directory(self, path):
        fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)

    def _write(self, name, value):
        target = self.private / name
        if target.exists() or target.is_symlink():
            self._regular(target)
        fd, temporary = tempfile.mkstemp(prefix=".launcher-", dir=self.private)
        try:
            with os.fdopen(fd, "w") as out:
                json.dump(value, out, ensure_ascii=True, indent=2)
                out.flush()
                os.fsync(out.fileno())
            os.replace(temporary, target)
            self._sync_directory(self.private)
        finally:
            Path(temporary).unlink(missing_ok=True)

    def _read(self, name):
        path = self.private / name
        if not path.exists() and not path.is_symlink():
            return None
        self._regular(path)
        if path.stat().st_size > 16384:
            raise ValueError("Registro de ejecución excesivo")
        return json.loads(path.read_text())

    def install(self, source):
        source = Path(source).expanduser().absolute()
        self._regular(source)
        if not self.releases.exists():
            self.releases.mkdir(mode=0o700)
        if self.releases.is_symlink() or self.releases.stat().st_uid != os.getuid() or self.releases.stat().st_mode & 0o077:
            raise ValueError("Almacén de JAR privado inválido")
        fd, temporary = tempfile.mkstemp(prefix=".jar-", dir=self.releases)
        temporary = Path(temporary)
        try:
            digest = hashlib.sha256()
            with os.fdopen(fd, "wb") as out, source.open("rb") as inp:
                while block := inp.read(1024 * 1024):
                    out.write(block)
                    digest.update(block)
                out.flush()
                os.fsync(out.fileno())
            try:
                with zipfile.ZipFile(temporary) as jar:
                    manifest = jar.getinfo("META-INF/MANIFEST.MF")
                    if manifest.file_size > 65536 or b"Main-Class:" not in jar.read(manifest) or jar.testzip() is not None:
                        raise ValueError("JAR ejecutable íntegro requerido")
            except (zipfile.BadZipFile, KeyError) as error:
                raise ValueError("JAR ejecutable íntegro requerido") from error
            value = digest.hexdigest()
            target = self.releases / (value + ".jar")
            os.chmod(temporary, 0o400)
            try:
                # Nunca se enlaza Maven: solo el temporario privado copiado, que luego se retira.
                os.link(temporary, target, follow_symlinks=False)
            except FileExistsError:
                self.release(value)
            temporary.unlink()
            self._sync_directory(self.releases)
            self.release(value)
            return {"jar": str(target), "sha256": value}
        finally:
            temporary.unlink(missing_ok=True)

    def release(self, value):
        if not isinstance(value, str) or re.fullmatch(r"[0-9a-f]{64}", value) is None:
            raise ValueError("SHA-256 completo requerido")
        if self.releases.is_symlink():
            raise ValueError("Almacén de JAR inválido")
        path = self.releases / (value + ".jar")
        self._regular(path)
        if path.stat().st_mode & 0o222 or sha256(path) != value:
            raise ValueError("Copia de ejecución modificada o con permiso de escritura")
        return path

    def active(self):
        saved = self._read("active-runtime.json")
        pid_path = self.private / "app.pid"
        if not pid_path.exists():
            return None
        self._regular(pid_path)
        pid = int(pid_path.read_text().strip())
        if pid <= 1:
            raise ValueError("PID inválido")
        proc = Path("/proc") / str(pid)
        if not proc.exists():
            return None
        if proc.stat().st_uid != os.getuid():
            raise ValueError("PID pertenece a otra identidad; no operar")
        process_stat = (proc / "stat").read_text().rsplit(")", 1)[1].split()
        if process_stat[0] == "Z":
            return None
        argv = (proc / "cmdline").read_bytes().decode().rstrip("\0").split("\0")
        env = (proc / "environ").read_bytes().split(b"\0")
        config_env = ("SPRING_CONFIG_ADDITIONAL_LOCATION=file:" + str(self.config)).encode()
        if argv.count("-jar") != 1 or config_env not in env:
            raise ValueError("PID no corresponde al JAR y configuración de este laboratorio")
        jar = Path(argv[argv.index("-jar") + 1])
        if jar.parent not in (self.private, self.releases) or jar.is_symlink() or "target" in jar.parts:
            raise ValueError("JAR activo fuera del almacén privado; no operar automáticamente")
        ticks = process_stat[19]
        java = str((proc / "exe").resolve())
        if Path(java).name != "java":
            raise ValueError("PID no corresponde al ejecutable Java")
        if saved and (saved["pid"] != pid or saved["start_ticks"] != ticks
                      or saved["jar"] != str(jar) or saved["config"] != str(self.config)
                      or saved["java"] != java):
            raise ValueError("PID reutilizado o registro de ejecución discordante; no operar")
        self._regular(jar)
        current_hash = sha256(jar)
        return {"pid": pid, "start_ticks": ticks, "java": java, "jar": str(jar),
                "sha256": current_hash, "config": str(self.config), "config_sha256": sha256(self.config),
                "registrado": saved is not None,
                "integridadJar": saved is None or saved["sha256"] == current_hash,
                "configuracionCoincide": saved is None or saved["config_sha256"] == sha256(self.config)}

    def stopped(self):
        if self.active() is not None:
            raise ValueError("El laboratorio sigue activo; no se selecciona ni arranca otra instancia")
        with socket.socket() as sock:
            if sock.connect_ex(("127.0.0.1", self.port)) == 0:
                raise ValueError("Puerto ocupado; no se detiene ni reemplaza ningún servicio")

    def select(self, value):
        self.stopped()
        jar = self.release(value)
        self._write("selected-jar.json", {"jar": str(jar), "sha256": value})
        return {"jar": str(jar), "sha256": value, "arrancado": False}

    def selected(self):
        selected = self._read("selected-jar.json")
        if selected is None:
            raise ValueError("Seleccionar con --seleccionar SHA256 o indicar --jar JAR explícito; nunca se usa target por defecto")
        jar = self.release(selected["sha256"])
        if str(jar) != selected["jar"]:
            raise ValueError("Selección de JAR discordante")
        return jar

    def status(self):
        active = self.active()
        selected = self._read("selected-jar.json")
        if selected:
            self.selected()
        return {"puerto": self.port, "estado": "ACTIVO" if active else "DETENIDO",
                "activo": active, "seleccionado": selected}

    def stop(self):
        if self.active() is None:
            return {"estado": "DETENIDO", "senalEnviada": False}
        pid = int((self.private / "app.pid").read_text())
        fd = os.pidfd_open(pid)
        try:
            active = self.active()
            if active is None:
                return {"estado": "DETENIDO", "senalEnviada": False}
            signal.pidfd_send_signal(fd, signal.SIGTERM)
            return {"pid": pid, "detencionSolicitada": True, "senalEnviada": True}
        finally:
            os.close(fd)

    def start(self, java_options, cwd):
        self.stopped()
        jar = self.selected()
        expected = sha256(jar)
        config_hash = sha256(self.config)
        with (self.private / "app.log").open("ab") as log:
            process = subprocess.Popen(["java", *java_options, "-jar", str(jar)],
                env=dict(os.environ, SPRING_CONFIG_ADDITIONAL_LOCATION="file:" + str(self.config)),
                cwd=cwd, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            proc = Path("/proc") / str(process.pid)
            saved = {"pid": process.pid, "start_ticks": (proc / "stat").read_text().rsplit(")", 1)[1].split()[19],
                     "java": str((proc / "exe").resolve()), "jar": str(jar), "sha256": expected,
                     "config": str(self.config), "config_sha256": config_hash, "port": self.port}
            self._write("active-runtime.json", saved)
            self._write("app.pid", process.pid)
            return {**saved, "arrancado": True, "readinessVerificada": False}
        except Exception:
            process.terminate()
            process.wait(timeout=10)
            raise

    def action(self, args):
        if (args.estado or args.instalar or args.seleccionar or args.detener) and (args.jar or args.migrar):
            raise ValueError("No combinar consulta/preparación/parada con --jar o --migrar")
        if args.estado:
            return self.status()
        if args.instalar:
            return self.install(args.instalar)
        if args.seleccionar:
            return self.select(args.seleccionar)
        if args.detener:
            return self.stop()
        return None
