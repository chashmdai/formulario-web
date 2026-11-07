# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Reglas obligatorias (resumen de AGENTS.md)

`AGENTS.md` es la fuente normativa; se resume aquí porque se aplica en cada sesión:

- Comenzar por `git status`/`diff`/historial y conservar trabajo local y remoto. Nunca `reset --hard`, force-push, `docker compose down -v` ni podas de volúmenes como diagnóstico.
- Antes de operar, leer `README.md` y `docs/TRASPASO_DDN_UBUNTU_GATE_H1_2026-11-01.md`; instalación en `docs/INSTALACION_UBUNTU_DOCKER.md` y `docs/INTEGRACIONES_UBUNTU.md`. El estado operativo vigente lo determina `docs/CHECKPOINT_PRODUCCION_SIGER_2026-11-04.md`; los demás documentos fechados son históricos.
- El Formulario es un **receptor transitorio** en Linux; **SIGER** (Windows) es independiente y realiza la custodia definitiva. No tocar su repositorio, BD ni servicios sin un encargo explícito.
- DDN-UBUNTU ya ejecuta GATE 4A y el orquestador `ddn-formulario-lab.service`. Inspeccionar primero; todo candidato usa proyecto, directorio, volumen y puerto propios. No reiniciar ni reemplazar servicios activos sin autorización.
- Ejecutar solo el GATE autorizado. Preparar código **no** autoriza publicación ciudadana, túnel/DNS, recepción/PULL, consultas reales a proveedores, garantías, mantenimiento ni retirada. Preservar la QA WSL integrada (DDN-TEST-SIGER).
- **No editar** migraciones Flyway V1–V12 (ni sus checksums), contratos firmados 1.0/1.1, UUID, COM/ACK ni firmas históricas. Los cambios de esquema van en una migración nueva, en ambos dialectos.
- Secretos, claves privadas, RUN, volcados y evidencias quedan fuera de Git y del contexto Docker (ver `.gitignore`/`.dockerignore`). Informar contadores y hashes, nunca datos personales. Las regresiones no usan proveedores gubernamentales (ClaveÚnica, PISEE/SRCeI) reales.
- Runtime solo con JAR o imagen inmutable; nunca desde `target/` de Maven. Distinguir siempre entre CI remota y evidencia local.

## Comandos

Requisitos: Linux/POSIX (el almacén multimedia y sus pruebas lo exigen), JDK 25, Maven 3.9+, Node 24.15+. Ejecutar desde la raíz.

```bash
# Frontend (React 19 + TS + Vite, Vitest)
npm --prefix formulario-publico/front ci
npm --prefix formulario-publico/front run lint
npm --prefix formulario-publico/front test -- --maxWorkers=2
npm --prefix formulario-publico/front test -- src/formulario/adultRules.test.ts   # un archivo
npm --prefix formulario-publico/front test -- -t "nombre del caso"                 # un caso
npm --prefix formulario-publico/front run build   # tsc -b && vite build → front/dist

# Backend (Spring Boot 4.1, H2 en pruebas). Excluir siempre las pruebas de carga.
# siger.build.directory desvía la salida fuera de backend/target en clones compartidos.
mvn -B -ntp -f formulario-publico/backend/pom.xml \
  -Dsiger.build.directory="$(pwd)/.local/desarrollo-target" \
  '-Dtest=*Test,!Carga*Test' verify
mvn -B -ntp -f formulario-publico/backend/pom.xml -Dtest=NombreClaseTest test      # una clase
mvn -B -ntp -f formulario-publico/backend/pom.xml '-Dtest=NombreClaseTest#metodo' test

# Controles Python sintéticos (los mismos que CI)
python3 scripts/qa/test_media_archive.py
python3 scripts/qa/test_media_storage_report.py
python3 scripts/qa/test_runtime_jar.py
python3 scripts/qa/test_ubuntu_prepare.py

# Empaquetado: valida Compose sin crear servicios y construye la imagen completa
docker compose --env-file infra/ubuntu/.env.example -f infra/ubuntu/compose.yml config --quiet
docker build --build-arg SOURCE_REVISION="$(git rev-parse HEAD)" -t formulario-h1:local .
```

- `npm run dev` levanta Vite en localhost y hace proxy de `/api`, `/oauth2`, `/redirect`, `/logout` y `/auth` hacia un backend separado en `127.0.0.1:18080`. No sustituye el origen HTTPS ni los callbacks registrados de ClaveÚnica.
- El JAR incluye `front/dist`, por lo que el backend debe compilarse **después** de `npm run build` si se desea la UI empaquetada. Las pruebas de frontend y backend leen `contratos/fixtures/`.
- CI (`.github/workflows/ci.yml`) tiene tres jobs: Linux (React + Maven/H2 + Python), Windows (agente de custodia con `-Pagente-core`, fixtures NTFS/criptografía para contratos 1.0 y 1.1) y Docker (Compose `config`, `caddy adapt` del Caddyfile y build de la imagen).

## Arquitectura

**Flujo de entrega:** ciudadanía → Caddy (HTTPS) → Spring Boot (sirve React desde el JAR) → recepción transaccional en MySQL + custodia POSIX de originales → inspección (ClamAV + inspector aislado) → SIGER Windows hace **PULL** saliente (reserva, descarga, verificación, COMMIT) y devuelve un **ACK firmado**. El Formulario nunca abre conexiones hacia SIGER. El comprobante ciudadano no equivale a un folio COM ni a un ACK. Los reintentos no deben generar duplicados ni confirmaciones ficticias (idempotencia).

**Backend** (`formulario-publico/backend`, paquete `cl.nexbyte.siger`):
- `formulario`, `nna`, `comunicaciones`: recorridos de adultos (con o sin ClaveÚnica) y de NNA, validación y recepción.
- `identidad`, `srcei`: ClaveÚnica (OAuth/OIDC) y consulta PISEE/SRCeI. Los antecedentes registrales **permanecen en el backend** y nunca se envían al navegador. Consultar SRCeI no autentica a la persona.
- `multimedia`: adjuntos privados, hashes e inspección.
- `custodia`, `entrega`, `contrato`: documento completo 1.0/1.1, firmas Ed25519/SHA-256, API PULL/ACK, retención/hold y retirada.
- `robustez`, `config`: límites, seguridad y configuración. La configuración es externa (`application.yml` + variables de entorno o `infra/ubuntu/application.properties.example`); por defecto la recepción/PULL está cerrada y PISEE/mantenimiento/retirada deshabilitados.
- Flyway: `src/main/resources/db/publico/{h2,mysql}/V*.sql`, mantenidas en paralelo por dialecto.
- El perfil Maven `agente-core` genera un JAR clasificado sin controladores ni configuración Spring, que consume `agente-custodia/`.
- Los catálogos `front/src/formulario/territorio.json` y `paises.json` son compartidos: el pom los copia a `catalogos/` dentro del JAR y el Dockerfile verifica que existan junto con `static/index.html`.

**Frontend** (`formulario-publico/front/src`): `prototipo/` (entrada, selección de audiencia, acceso de adultos), `formulario/` (pasos de adulto/NNA, reglas `*Rules.ts`, revisión, API, hash multimedia en un web worker), `nna/`, `ui/`. Las pruebas `*.test.tsx` se ubican junto al código; el setup está en `src/test/setup.ts`.

**Otros componentes:**
- `agente-custodia/`: agente Java nativo para Windows Server (lab 1.1.0), auxiliar y opcional para la recepción; la emisión hacia un Formulario en servicio está bloqueada.
- `contratos/`: especificaciones firmadas y fixtures (inmutables en las versiones 1.0/1.1).
- `infra/ubuntu/`: Compose integrado (MySQL 8.4, aplicación, Caddy, inspector, ClamAV), Caddyfiles, unidades systemd y plantillas. El origen privado publica solo `127.0.0.1:24443`. Los demás `infra/compose.*.yml` y `infra/sandbox` corresponden a laboratorios.
- `scripts/ubuntu/`: `preparar.py` (inicializa **solo** un destino nuevo fuera de Git; no arranca ni migra), `estado.py`, `respaldo*.py`, `transicion.py`, `acceso-temporal.py`, `validar-rc1.py`. `scripts/linux/`: archivo de medios y lanzador del JAR. `scripts/qa/`: regresiones sintéticas.
- Los scripts `.ps1`/`FORMULARIO.cmd` son lanzadores históricos del laboratorio Windows.
- `.local/` (ignorado por Git) contiene material de trabajo local; no versionarlo.
