#!/usr/bin/env python3
"""Regresión de lanzadores Linux: solo temporales sintéticos y JVM propias, sin BD/proveedores."""
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
import urllib.request
import zipfile

LINUX = Path(__file__).resolve().parents[1] / "linux"
sys.path.insert(0, str(LINUX))
if sys.platform == "linux":
    from jar_laboratorio import RuntimeJar, sha256


@unittest.skipUnless(sys.platform == "linux", "Lanzadores exclusivos de Linux/POSIX")
class RuntimeJarTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not all(shutil.which(p) for p in ("java", "javac", "jar")):
            raise unittest.SkipTest("Prueba real requiere JDK existente")
        cls.compilation = tempfile.TemporaryDirectory(prefix="siger-jar-fixture-")
        cls.fixture = Path(cls.compilation.name)
        source = cls.fixture / "TinyServer.java"
        source.write_text('''import java.net.*;import java.io.*;
public class TinyServer {
 public static void main(String[] args)throws Exception {
  int port=Integer.parseInt(System.getenv("SIGER_TEST_PORT"));
  try(var server=new ServerSocket(port,8,InetAddress.getByName("127.0.0.1"))) {
   while(true)try(var socket=server.accept()) {
    var in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
    String line;do{line=in.readLine();}while(line!=null&&!line.isEmpty());
    String body=new String(TinyServer.class.getResourceAsStream("/version.txt").readAllBytes());
    var out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\\r\\nConnection: close\\r\\nContent-Length: "+body.length()+"\\r\\n\\r\\n"+body).getBytes());out.flush();
   }
  }
 }
}''')
        subprocess.run(["javac", str(source)], check=True, capture_output=True)
        cls.jars = []
        for value in ("ANTERIOR", "NUEVA"):
            target = cls.fixture / (value + ".jar")
            with zipfile.ZipFile(target, "w") as z:
                z.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: TinyServer\n\n")
                z.write(cls.fixture / "TinyServer.class", "TinyServer.class")
                z.writestr("version.txt", value)
            cls.jars.append(target)

    @classmethod
    def tearDownClass(cls):
        cls.compilation.cleanup()

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="siger-jar-runtime-")
        self.root = Path(self.directory.name)
        self.private = self.root / "private"
        self.private.mkdir(mode=0o700)
        (self.private / "application.properties").write_text("fixture=true\nsecret=NO_PUBLICAR_FIXTURE\n")
        with socket.socket() as s:
            s.bind(("127.0.0.1", 0))
            self.port = s.getsockname()[1]
        self.runtime = RuntimeJar(self.private, self.port)
        self.children = []
        self.old_env = os.environ.get("SIGER_TEST_PORT")
        os.environ["SIGER_TEST_PORT"] = str(self.port)

    def tearDown(self):
        for child in self.children:
            if child.poll() is None:
                child.terminate()
            child.wait(timeout=5)
        if self.old_env is None:
            os.environ.pop("SIGER_TEST_PORT", None)
        else:
            os.environ["SIGER_TEST_PORT"] = self.old_env
        self.directory.cleanup()

    def install(self, version=0):
        return self.runtime.install(self.jars[version])["sha256"]

    def start(self, version=0):
        value = self.install(version)
        self.runtime.select(value)
        original_popen = subprocess.Popen
        def spawn(*args, **kwargs):
            child = original_popen(*args, **kwargs)
            self.children.append(child)
            return child
        with patch("jar_laboratorio.subprocess.Popen", side_effect=spawn):
            result = self.runtime.start(["-Xmx64m"], self.root)
        for _ in range(50):
            try:
                return result, self.get()
            except OSError:
                time.sleep(.1)
        self.fail("JVM sintética no entregó respuesta HTTP")

    def get(self):
        with urllib.request.urlopen(f"http://127.0.0.1:{self.port}/", timeout=2) as response:
            return response.read().decode()

    def stop(self):
        result = self.runtime.stop()
        if result.get("pid"):
            next(child for child in self.children if child.pid == result["pid"]).wait(timeout=5)
        return result

    def test_copia_independiente_sobrevive_sobrescritura_y_clean(self):
        build = self.root / "target"
        build.mkdir()
        source = build / "application.jar"
        shutil.copyfile(self.jars[0], source)
        value = self.runtime.install(source)["sha256"]
        target = self.runtime.release(value)
        self.assertNotEqual(source.stat().st_ino, target.stat().st_ino)
        source.write_bytes(self.jars[1].read_bytes())
        shutil.rmtree(build)  # Solo el target sintético creado dentro de TemporaryDirectory.
        self.assertEqual(value, sha256(target))
        self.assertEqual(target.stat().st_mode & 0o777, 0o400)

    def test_instalacion_idempotente_y_versiones_conservadas(self):
        first = self.install()
        inode = self.runtime.release(first).stat().st_ino
        self.assertEqual(first, self.install())
        self.assertEqual(inode, self.runtime.release(first).stat().st_ino)
        second = self.install(1)
        self.assertNotEqual(first, second)
        self.assertEqual(2, len(list(self.runtime.releases.glob("*.jar"))))
        self.assertFalse((self.private / "selected-jar.json").exists())

    def test_jar_invalido_no_reemplaza_seleccion_ni_deja_temporales(self):
        value = self.install()
        self.runtime.select(value)
        invalid = self.root / "invalid.jar"
        invalid.write_bytes(b"no es un ZIP")
        with self.assertRaises(ValueError):
            self.runtime.install(invalid)
        self.assertEqual(value, sha256(self.runtime.selected()))
        self.assertEqual([], list(self.runtime.releases.glob(".jar-*")))

    def test_enlaces_y_hardlinks_rechazados(self):
        link = self.root / "link.jar"
        link.symlink_to(self.jars[0])
        with self.assertRaises(ValueError):
            self.runtime.install(link)
        source = self.root / "hard.jar"
        shutil.copyfile(self.jars[0], source)
        os.link(source, self.root / "alias.jar")
        with self.assertRaises(ValueError):
            self.runtime.install(source)

    def test_copia_corrupta_y_escribible_rechazada(self):
        value = self.install()
        jar = self.runtime.release(value)
        jar.chmod(0o600)
        with self.assertRaises(ValueError):
            self.runtime.release(value)
        jar.write_bytes(b"corrupto")
        jar.chmod(0o400)
        with self.assertRaises(ValueError):
            self.runtime.release(value)
        with self.assertRaises(ValueError):
            self.install()

    def test_nunca_hay_fallback_a_target_o_ultima_compilacion(self):
        self.install()
        with self.assertRaises(ValueError):
            self.runtime.selected()
        self.assertFalse((self.private / "app.pid").exists())

    def test_no_operar_dentro_repositorio_target_o_directorio_abierto(self):
        self.private.chmod(0o755)
        with self.assertRaises(ValueError):
            RuntimeJar(self.private, self.port)
        self.private.chmod(0o700)
        (self.private / ".git").mkdir()
        with self.assertRaises(ValueError):
            RuntimeJar(self.private, self.port)

    def test_puerto_ajeno_ocupado_impide_seleccionar(self):
        value = self.install()
        with socket.socket() as s:
            s.bind(("127.0.0.1", self.port));s.listen()
            with self.assertRaises(ValueError):
                self.runtime.select(value)
        self.assertFalse((self.private / "app.pid").exists())

    def test_lock_interprocesos(self):
        code = "import sys,time;sys.path.insert(0,sys.argv[1]);from jar_laboratorio import RuntimeJar;r=RuntimeJar(sys.argv[2],12345);ctx=r.lock();ctx.__enter__();print('LOCK',flush=True);time.sleep(10)"
        child = subprocess.Popen([sys.executable, "-c", code, str(LINUX), str(self.private)], stdout=subprocess.PIPE, text=True)
        try:
            self.assertEqual("LOCK", child.stdout.readline().strip())
            with self.assertRaises(BlockingIOError):
                with self.runtime.lock():
                    self.fail("Se permitió solapamiento")
        finally:
            child.terminate();child.wait(timeout=5);child.stdout.close()

    def test_jvm_activa_sigue_igual_al_reemplazar_salida_de_build(self):
        active, response = self.start()
        self.assertEqual("ANTERIOR", response)
        build = self.root / "target"
        build.mkdir()
        shutil.copyfile(self.jars[1], build / "application.jar")
        self.runtime.install(build / "application.jar")
        self.assertEqual("ANTERIOR", self.get())
        self.assertEqual(active["pid"], self.runtime.active()["pid"])
        self.assertEqual(active["sha256"], self.runtime.active()["sha256"])
        with self.assertRaises(ValueError):
            self.runtime.select(sha256(self.jars[1]))
        with self.assertRaises(ValueError):
            self.runtime.start(["-Xmx64m"], self.root)

    def test_actualizacion_y_reversion_explicitas_sin_cambiar_configuracion(self):
        before = sha256(self.runtime.config)
        self.start();self.stop()
        _, response = self.start(1)
        self.assertEqual("NUEVA", response)
        self.stop()
        _, response = self.start(0)
        self.assertEqual("ANTERIOR", response)
        self.assertEqual(before, sha256(self.runtime.config))

    def test_identidad_pid_start_ticks_y_config_sin_secretos(self):
        self.start()
        state = self.runtime.status()
        self.assertNotIn("NO_PUBLICAR_FIXTURE", json.dumps(state))
        self.runtime.config.write_text("fixture=editada\n")
        self.assertFalse(self.runtime.active()["configuracionCoincide"])
        saved = self.runtime._read("active-runtime.json")
        saved["start_ticks"] = "0"
        self.runtime._write("active-runtime.json", saved)
        with self.assertRaises(ValueError):
            self.runtime.stop()
        self.assertEqual("ANTERIOR", self.get())

    def test_pid_ajeno_no_recibe_senal(self):
        self.runtime._write("app.pid", os.getpid())
        with self.assertRaises(ValueError):
            self.runtime.stop()

    def test_estado_cli_no_arranca_ni_instala_implicitamente(self):
        self.runtime.config.write_text("server.address=127.0.0.1\nserver.port=18443\nserver.ssl.enabled=true\nsiger.claveunica.environment=SANDBOX\nsiger.claveunica.callback-approved=false\n")
        command = [sys.executable, str(LINUX / "arrancar-nna-local.py"), str(self.private), "--estado"]
        result = subprocess.run(command, capture_output=True, text=True, check=True)
        self.assertEqual("DETENIDO", json.loads(result.stdout)["estado"])
        self.assertFalse((self.private / "app.pid").exists())
        result = subprocess.run(command + ["--jar", str(self.jars[0])], capture_output=True)
        self.assertNotEqual(0, result.returncode)

    def test_preparar_cli_no_ejecuta_java_ni_selecciona_ni_migra(self):
        self.runtime.config.write_text("server.address=127.0.0.1\nserver.port=18443\nserver.ssl.enabled=true\nsiger.claveunica.environment=SANDBOX\nsiger.claveunica.callback-approved=false\n")
        command = [sys.executable, str(LINUX / "arrancar-nna-local.py"), str(self.private)]
        result = subprocess.run(command + ["--instalar", str(self.jars[0])], check=True, capture_output=True, text=True)
        self.assertEqual(sha256(self.jars[0]), json.loads(result.stdout)["sha256"])
        self.assertFalse((self.private / "selected-jar.json").exists())
        self.assertFalse((self.private / "app.pid").exists())
        self.assertFalse((self.private / "migration.log").exists())

    def test_cli_cu_productivo_usa_habilitacion_privada_sin_bloqueo_por_ambiente(self):
        props={"server.address":"127.0.0.1","server.port":"18443","server.ssl.enabled":"true",
               "siger.claveunica.environment":"PRODUCTION","siger.claveunica.callback-approved":"true",
               "siger.claveunica.public-origin":"https://claveunica.formularioadulto.defensorianinez.gob.cl",
               "siger.claveunica.redirect-uri":"https://claveunica.formularioadulto.defensorianinez.gob.cl/redirect",
               "siger.claveunica.logout-uri":"https://claveunica.formularioadulto.defensorianinez.gob.cl/logout"}
        self.runtime.config.write_text("".join(k+"="+v+chr(10) for k,v in props.items()))
        command=[sys.executable,str(LINUX/"arrancar-nna-local.py"),str(self.private),"--estado"]
        result=subprocess.run(command,check=True,capture_output=True,text=True)
        self.assertEqual("DETENIDO",json.loads(result.stdout)["estado"])
        self.assertFalse((self.private/"app.pid").exists())
        for key,value in [("server.address","0.0.0.0"),("server.ssl.enabled","false"),("siger.claveunica.redirect-uri","https://otro.gob.cl/redirect")]:
            invalid=dict(props);invalid[key]=value
            self.runtime.config.write_text("".join(k+"="+v+chr(10) for k,v in invalid.items()))
            self.assertNotEqual(0,subprocess.run(command,capture_output=True).returncode)

    def test_cli_dominio_formulario_exacto_y_callbacks_coherentes(self):
        origin = "https://formulario.defensorianinez.gob.cl"
        props = {"server.address": "127.0.0.1", "server.port": "18443", "server.ssl.enabled": "true",
                 "siger.claveunica.environment": "PRODUCTION", "siger.claveunica.callback-approved": "true",
                 "siger.claveunica.public-origin": origin, "siger.claveunica.redirect-uri": origin + "/redirect",
                 "siger.claveunica.logout-uri": origin + "/logout"}
        command = [sys.executable, str(LINUX / "arrancar-nna-local.py"), str(self.private), "--estado"]
        self.runtime.config.write_text("".join(k + "=" + v + chr(10) for k, v in props.items()))
        result = subprocess.run(command, check=True, capture_output=True, text=True)
        self.assertEqual("DETENIDO", json.loads(result.stdout)["estado"])
        for key, value in [("siger.claveunica.public-origin", origin + ".example.org"),
                           ("siger.claveunica.public-origin", "https://formulario.defensorianinez.cl"),
                           ("siger.claveunica.redirect-uri", origin + "/otra-ruta"),
                           ("siger.claveunica.logout-uri", "https://otro.gob.cl/logout")]:
            invalid = dict(props); invalid[key] = value
            self.runtime.config.write_text("".join(k + "=" + v + chr(10) for k, v in invalid.items()))
            self.assertNotEqual(0, subprocess.run(command, capture_output=True).returncode)
        self.assertFalse((self.private / "app.pid").exists())

    def test_multimedia_cli_conserva_guardas_del_laboratorio(self):
        self.runtime.config.write_text("server.address=127.0.0.1\nserver.port=19443\nserver.ssl.enabled=true\nsiger.claveunica.callback-approved=false\nPISEE_SRCEI_ENABLED=false\nformulario.multimedia.habilitada=true\npublico.db.url=jdbc:mysql://127.0.0.1:13306/robust_public_media_lab?fixture=true\n")
        command = [sys.executable, str(LINUX / "arrancar-multimedia-local.py"), str(self.private), "--estado"]
        result = subprocess.run(command, check=True, capture_output=True, text=True)
        self.assertEqual("DETENIDO", json.loads(result.stdout)["estado"])
        with self.runtime.config.open("a") as out:
            out.write("PISEE_SRCEI_ENABLED=true\n")
        self.assertNotEqual(0, subprocess.run(command, capture_output=True).returncode)

    def test_reconoce_y_detiene_copia_privada_legacy_sin_target(self):
        legacy = self.private / "runtime.jar"
        shutil.copyfile(self.jars[0], legacy)
        with (self.private / "app.log").open("ab") as log:
            child = subprocess.Popen(["java", "-Xmx64m", "-jar", str(legacy)],
                env=dict(os.environ, SPRING_CONFIG_ADDITIONAL_LOCATION="file:" + str(self.runtime.config)),
                stdin=subprocess.DEVNULL, stdout=log, stderr=log)
        self.children.append(child)
        self.runtime._write("app.pid", child.pid)
        state = self.runtime.active()
        self.assertEqual(str(legacy), state["jar"])
        self.assertFalse(state["registrado"])
        self.stop()
        self.assertIsNone(self.runtime.active())


if __name__ == "__main__":
    unittest.main(verbosity=2)
