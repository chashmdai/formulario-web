# Integración Formulario / PISEE / SIGER — 03-11-2026

Nexbyte SpA / Defensoría de la Niñez. DDN-UBUNTU, America/Santiago. **Candidato privado probado en Linux. Sin despliegue público ni apertura ciudadana. El nuevo E2E físico Windows está PENDIENTE.**

## Dictamen y límites

| Gate | Resultado |
|---|---|
| Código del candidato, flujo adulto declarado, contrato, laboratorio sintético Linux | GO técnico acotado; pruebas descritas abajo |
| Adaptador Java contra SRCeI productivo con una identidad autorizada para esta campaña | PENDIENTE; no se recibió esa identidad/autorización de finalidad específica |
| Almacenamiento, COMMIT/ACK y visualización de esta campaña en SIGER Windows | PENDIENTE; sin acceso Windows ni ventana coordinada |
| Habilitación pública del Formulario completo | **NO-GO**, no ejecutada |

El manual recibido acredita SRCeI real desde el nodo; esta sesión no convierte esa evidencia en una validación nueva del adaptador Java ni en un E2E Windows. Las pruebas con stub no acreditan tratamiento productivo de identidades. CU final, SIGER definitivo, Graph/correo, respaldo independiente, seguridad/retención y autorización institucional siguen siendo gates separados.

## Fuentes inspeccionadas

- Adjunto real `/home/admin-ti/Downloads/MANUAL_PISEE_PRODUCCION_LINUX_2026-11-03.md`, SHA-256 `c2aba50c0679b097bcd8c8d9aef6e9378a666ea2c2e2b3c94562f1538f2bfe2d`. Se usó como evidencia técnica, no como autorización para ejecutar sus recetas históricas.
- `AGENTS.md`, README, traspaso H1, instalación Ubuntu, integraciones, PISEE Linux, arquitectura del receptor transitorio, custodia y `contratos/formulario-completo-multimedia-v1.1.md` del checkout vigente.
- Informes locales de certificación CU y cierre Ubuntu U4.4; documento local `U4_SONDA_CRUZADA_Y_PLAN_2026-11-02(1).md`, que describe un corte anterior al cierre.
- [Informe final completo U4.4 Windows/Ubuntu](https://github.com/chashmdai/sistema-de-requerimientos/blob/main/docs/INFORME_FINAL_U4_4_WINDOWS_UBUNTU_2026-11-02.md), obtenido mediante el conector GitHub porque no estaba depositado localmente con ese nombre. También AGENTS, operación PULL/ACK y fuentes del consumidor SIGER. U4.4 permanece cerrado e intacto.

## Git y base del candidato

Se conservaron los dos checkouts: `/home/admin-ti/proyectos/formulario-web` en `e94225d` y `/home/admin-ti/formulario-ubuntu-h1-20261102` en `deploy/certificacion-claveunica-2026-11-03`, `76d0b97`. El `main` remoto real es `7189a95`; el antiguo checkout no se tomó como base actual.

El worktree privado es `/home/admin-ti/formulario-release-20261103`, rama `release/formulario-produccion-2026-11`, base `76d0b97ca997a8036b6100d65976f76099fcc632`. Esa base contiene main y las mejoras de certificación. No hizo falta merge/cherry-pick/rebase. Se conservó el cierre CU por flag, cuyo valor predeterminado es false; no se trasladó la configuración privada que lo activa en el sitio publicado. No se tocaron main, la rama desplegada, V1–V12 ni documentos/firma históricos.

La CLI carece de credencial de escritura. El conector GitHub autenticado publicó commits sin forzar refs. Sus metadatos generan SHA distintos; se comprobó **igualdad exacta de árboles Git** en cada par:

| Commit local de ejecución | Commit remoto equivalente | Cambio |
|---|---|---|
| `e9c7de4` | `7231970` | Política PISEE, XML, privacidad, procedencia y pruebas |
| `c084d57` | `a3ecd14` | Corrección declarada y cancelación HTTP adulta |
| `8d08fb7` | `11ed6c0` | Copia privada del nodo y overlays/stub |
| `2caa7e4` | `ca3f4bc` | Certificados de pruebas efímeros |
| `0f4fbf3` | `bd89b50` | Envío registral cerrado incluso al apagar PISEE |

La referencia local de ejecución se preserva como `release/formulario-produccion-2026-11-ejecucion-local`; la rama objetivo queda alineada con la rama remota después de publicar esta documentación. Los commits de documentación/plantillas posteriores no reconstruyen el JAR.

## Inventario y separación

Al comienzo había 15 contenedores existentes: cinco activos del despliegue institucional y diez históricos detenidos. Docker/sudo requirieron autenticación gráfica interactiva, autorizada por el operador. Antes de obtenerla se registraron procesos/puertos sin argumentos sensibles.

El operador pidió detener los nodos activos. Se identificó únicamente `NodoV2`, PID 2383451, desde `LAB_PROD_2026-11-03`, y se detuvo con SIGINT; desaparecieron sus listeners globales 8084/8489 y el socket dinámico observado. No se cambió su configuración ni se inició de nuevo ese proceso histórico.

| Laboratorio nuevo | Uso y aislamiento |
|---|---|
| `ddn-release-pisee-lab-a` | React/JAR + MySQL nuevo + almacén/PKI/claves propios + AV/inspector + stub sin egreso. HTTPS solo `127.0.0.1:26443`; hostname TLS `formulario-pisee-lab.defensorianinez.gob.cl`, sin DNS/túnel público |
| `ddn-release-pisee-lab-b` | Nodo institucional copiado, estado propio, redes privada/egreso, sin puertos publicados ni backend ciudadano conectado |
| `ddn-release-regression-mysql` | MySQL descartable propio, volumen nuevo, esquema `robust_public_test`, solo loopback 13306 para la batería existente |

La raíz privada es `/home/admin-ti/operacion-formulario/release-20261103`, fuera de Git/contextos Docker. Ningún contenedor del candidato monta bases, almacenes o claves del sitio publicado o de QA histórica. Al cierre los tres proyectos nuevos quedan **detenidos**, conservando sus datos y volúmenes. No hay nodos ni listeners 8084/8489 del ensayo en el host. El candidato no deja recepción accesible.

## Arquitectura e implementación final

```text
Carril A: navegador privado → Spring/JAR → ClientePisee → stub SRCeI controlado
                                    ↓
                         MySQL/almacén/cola nuevos → PULL/ACK SIGER pendiente

Carril B: ClientePisee (pendiente de identidad autorizada) → Nodo privado → SRCeI real
```

Se reutilizaron `ClientePisee`, `XmlSrcei`, `RecorridosJdbc`, el formulario completo y las firmas existentes. React solo habla con Spring.

- Ruta fija `/srcei/informacionPersonalBasico`, servicio `InformacionPersonalBasicoDDN`; se preservaron `INFORMACION PERSONAL BASICO` / `REGISTRO DE USUARIOS` en el nodo.
- XML literal `entradaSRCel`, último carácter U+006C. Prueba negativa de `entradaSRCeI` U+0049: un 200 vacío nunca se clasifica COMPLETO.
- Parser con namespaces/prefijos variables, límites, rechazo de DTD/XXE/duplicados. Mapea los diez elementos conocidos y subelementos de circunscripción sin inferir domicilio. Códigos desconocidos se conservan; `0000-00-00` se omite, también como fecha truncada.
- Vacío, negativa de negocio, parcial/completo, 401/403, red, timeout, exceso y XML inválido conservan clasificación. Diagnósticos internos de TLS, SOAP Fault criptográfico y “sin información” no amplían el enum del documento firmado. No se atribuye un fallo genérico HTTP a firma sin evidencia.
- `PoliticaPisee` separa DEV, CONTROLLED_TEST y PRODUCTION. Exige autorización del ambiente y archivo acotado de RUN; una lista vacía autoriza cero consultas. Producción exige además finalidad, base legal aprobada y certificado vigente con huella externa coincidente. URL/material siguen externos. El nodo validado no exige autorización HTTP local: no se inventó una credencial.
- Envío de cualquier atributo/discrepancia SRCEI requiere aprobación explícita. El carril sintético tiene su permiso separado. Apagar el cliente no permite enviar datos registrales previamente persistidos. La aprobación real permanece false.
- Se conservan cuotas por recorrido/IP, sesión, CSRF, deduplicación, leases y rechazo de respuestas tardías. La UI tiene debounce, solicitud cancelable y control de generación. No revela valores registrales por conocer un RUN.
- La corrección explícita del adulto sin CU conserva declaraciones y discrepancias registrales; no permite sustituir identidad CU. RUN declarado permanece `DECLARACION_CIUDADANA`, `autenticada=false`.
- Se persisten únicamente los atributos registrales permitidos para el recorrido. Profesión, defunción y circunscripción se normalizan en memoria pero no se incorporan a la Persona/Comunicación. No se guarda el XML/SOAP ni tokens.
- Eventos del cliente contienen solo enums de estado/motivo; contadores internos por estado. `toString` protegido. El nodo usa `error`, `/dev/null` y logging Docker `none`; no se confía solo en `info` para privacidad.
- Sonda Java acotada en `scripts/linux/sonda-adaptador-pisee.sh` / `SondaAdaptadorPisee.java`: extrae un JAR por SHA verificado a destino nuevo, compila contra ese artefacto inmutable, exige un único RUN autorizado y reutiliza su cliente/política. No inicia Spring/BD ni permite persistir una Comunicación. Compilación y rechazo con cliente deshabilitado comprobados; no se ejecutó una consulta real.

## Nodo real, configuración y certificados

Se preparó una copia independiente desde el laboratorio institucional validado. El paquete `DDN_PROD/DDN` y el laboratorio histórico no se modificaron. La comparación verificó 18 archivos originales/configuración/claves sin cambios. El binario productivo SHA-256 es `f09005d9142e10885371ed9045f516e35002655c26e83dd1dddddf1acf77b25b`.

Se cotejaron pares criptográficos y certificado institucional con la huella del manual. La PKI del paquete contiene una clave con rotulado PEM que OpenSSL no interpreta directamente; se comprobó su DER en memoria, sin reescribir ni sustituir la clave que consume Go. Catálogo, MPGA, identidades, seguridad interna y servicios quedaron iguales: solo se adaptaron rutas, estado y registro. Certificados/configuración se montan RO; estado nuevo RW. No se copió SQLite/LevelDB histórico.

Wrapper Ubuntu 26.04 por digest, `xmlsec1 1.3.9`; imagen `sha256:e0245efc9e74b0a3995f0d944e43a008b895deab8c18e715b8c0a11f70fbf842`. El nodo escucha dentro de su namespace; `PortBindings={}`, sin listeners host 8084/8489. Se verificaron redes/egreso real mediante GESCOD, no solo UFW.

Dos preparaciones rechazadas previas quedaron en directorios privados de esta campaña, sin arranque ni publicación, para custodia; no se borraron secretos. Todo ese material queda fuera de Git/CI/artefactos entregables.

## Validaciones ejecutadas y evidencia

| Prueba | Resultado y alcance |
|---|---|
| Frontend ESLint / TypeScript / Vite | Correctos |
| React | 216 pruebas correctas |
| Docker final: H2/verify/JAR | 392 pruebas, cero fallos/errores, 2 omisiones opt-in existentes |
| MySQL 8.4.11 final, esquema nuevo | 392 pruebas, cero fallos/errores, las mismas 2 omisiones |
| Python existente | 46 correctas |
| XML/cliente | Literal/código Unicode, negativa HTTP 200 vacío, 10 elementos, prefijos, parcial/vacío/glosa, fechas/códigos, XXE/DTD, duplicados, límites, 401/403, timeout, saturación, redirect y TLS/firma controlados |
| Reserva/persistencia | Baterías existentes de hashes, firmas, reintentos, ACK ambiguo, deduplicación, retirada/reversibilidad; no se sustituyeron por pruebas nuevas simplificadas |
| AV real nuevo | PNG limpio; EICAR detectado; EICAR al final de ZIP >12 MiB detectado |
| Inspector real | Healthy LANDLOCK3_SECCOMP y PNG admitido por el circuito multimedia real |
| Adulto sin CU / carril A Linux | Un envío, stub explícito, corrección declarada, PNG nuevo, revisión y comprobante; sin OAuth ni autenticación |
| Accesibilidad comprobada | Identificación escritorio: contraste sin fallos detectados. Acceso/privacidad móvil 390×844: sin desborde, contraste sin fallos, pie visible. No equivale a auditoría WCAG integral |
| Validador contractual SIGER | Código real `EntregaCompleta` blob `56eecb2e4c355a4c65015b23a0adb8e313dda0a8`, compilado aisladamente en Ubuntu con sus dependencias y contratos compartidos: acepta documento/firma/huellas; no ejecuta BD/UI Windows |
| Sonda técnica HTTPS privada | Sin Bearer 401, falso 401, válido 404 sobre recurso inexistente. Cero claims/ACK |
| GESCOD real del nodo nuevo | Detalle organismo 200/747 bytes y territorio 200/6269 bytes; ambos JSON válidos |
| SRCeI personal real nuevo | **No ejecutado**. La evidencia real anterior de 881 bytes/10 elementos pertenece al manual |
| E2E físico nuevo SIGER Windows | **PENDIENTE**, cero claims y cero ACK de esta campaña |

Las dos omisiones corresponden a workers reales opt-in y recuperación/reinicio MySQL opt-in; no son éxitos. La comprobación AV/inspector manual es evidencia separada. Los primeros ensayos H2 detectaron permisos de temporales incompatibles con custodia: se ejecutó con `umask 077`, sin relajar permisos de producción. También se corrigió la compatibilidad del campo opcional de corrección del DTO y se actualizaron pruebas de la antigua autorización productiva amplia.

El navegador del carril A recibió respuestas reales a través de un puente Node que verificó CA/SAN en cada HTTPS, sin sustituir APIs por fixtures ni ignorar TLS. Esto acredita el flujo aplicado/persistido; no acredita que Chromium confíe nativamente en la CA privada del laboratorio. La comprobación pública posterior sí usó Chromium con TLS normal, sin ese puente.

## Comunicación sintética conservada

- UUID `89f202c2-cbf6-470d-93b3-4d3f4e6dcff1`; tipo ADULTO_DECLARADO; documento 1.1; canal esperado `FORMULARIO_WEB_PUBLICO`; ACK adulto esperado 1.0.
- Una Comunicación, un recorrido, un adjunto. `PENDIENTE`, attempts=0, sin reserva/ACK. Control revisión 4, false/false, SOLO_CONSULTA.
- PNG nuevo: 99 bytes; SHA-256 `f63af47a5e681a6151eeb0a6f2a95d67770085e2826d59263990abe27940f40a`.
- Huella canónica contenido `c8cd9c7cc48d0b821541bd1feb694ec4b8544feb28b434f65ef6dc7f5062b975`.
- Huella procedencia `161da5b01c2ea4c10d794838373f8ea3793d9175f3f71325048b41a8b7f55689`.
- Sobre conservado: 4150 bytes; SHA-256 de archivo `0efc342a0d0a6b868302c52c306cef93ea1f8231e5c0a775becb33b5cb2c2877`. Firma Ed25519, procedencia/discrepancias y original verificados, idénticos después de recrear app y seleccionar la imagen final. No se reenviaron ni reclamaron datos.

El contrato 1.0/1.1 ya contiene `Persona.atributos`, `discrepancias`, fuente/nivel/fecha/evidencia y `autenticada`; no fue necesaria una versión paralela ni una migración. La aceptación del validador no demuestra aún que Windows almacene y muestre todos los atributos: ese gate debe observarse allí.

## Artefactos

| Artefacto | Identidad |
|---|---|
| Fuente final de runtime | Local `0f4fbf38a8bb2b65648b2390090fd3ebad521dac`, árbol `2323d035e227e437bb882a4b7e05b99678753f38`, remoto equivalente `bd89b501dae524dba929bc26b4d9e539d25e55d2` |
| Imagen final privada | `sha256:a5d808743d56e7fcc9edf2a9a345898fa8dea7bb792f87e464e1ebf3f1d0c69b` |
| JAR final SHA-256 | `8ff36499e0b6e270a4528a3281028642e68a89a9bc75f74a2a4e83a20aac031e` |
| JAR del circuito inicial, fuente 2caa7e4 | `49e61e6455f42709d8c75cea3107c5b14bfec2ae0be95cfba50eb8f3da9961bc`; conservado. El endurecimiento posterior solo cambió el gate de envío registral, se recompiló/verificó y recreó app sin otro envío |
| CA pública exclusiva del laboratorio, SHA-256 de archivo | `d79b09a33a868dc0cd8b654d1b8b957dd84344013eaa9d38cca01d2933ec872f` |

Las imágenes/JAR/PKI y evidencias privadas permanecen locales. En Git solo hay código, fixtures sintéticos, plantillas e informes saneados; no XML personal real, configuración institucional completa, privadas, PFX, tokens ni volcados.

## Preservación del despliegue y QA

Se registraron inventarios privados antes/después de procesos, servicios, puertos, imágenes, contenedores y hashes de 597 archivos del despliegue/configuración/Tunnel. Se comparan IDs, imágenes, instantes de inicio, reinicios, configuración y montajes de los 15 contenedores originales. No se reconstruyó, reinició ni reemplazó ninguno. Las ramas/checkouts originales conservaron HEAD y estado limpio.

La comparación final dio **597 archivos idénticos, cero cambios en los 15 contenedores originales y ocho contenedores nuevos detenidos**. Registro saneado: [comparación certificación/QA](REGISTRO_COMPARACION_CERTIFICACION_QA_2026-11-03.json), con hashes de los inventarios privados y alcance explícito sobre Windows.

El sitio institucional mantiene imagen de certificación original, control revisión 4 false/false SOLO_CONSULTA, una Comunicación/recorrido/adjunto históricos y cuatro auditorías, sin cambios. El verificador TLS del origen pasó **30/30 antes y después**, incluidas rutas ciudadanas/PULL cerradas, CA/SAN, sesión/CSRF y modo CU.

Comprobación pública final, Chromium/TLS normal: HTTP 200, título `Certificación ClaveÚnica | Defensoría de la Niñez`, `certificationMode=true`, environment PRODUCTION, authenticated=false, identidad ausente. HTML SHA-256 `7e8ea5e9fb24c5ee8152a0d716afaebe9a02b44c66914029c34ffd99f942e545`. Los clientes urllib/web recibían 403 de Cloudflare; no se cambió WAF/Tunnel para obtener el resultado de navegador. No hubo login institucional real nuevo.

Las QA históricas locales permanecen detenidas con sus mismos contenedores/configuraciones/montajes; sus bases no se usaron como fixtures. Windows/WSL no fueron accedidos: su conservación actual no se afirma como auditoría física nueva. Se conserva la evidencia U4.4 anterior y se exige comparación Windows antes/después de la próxima campaña.

## Evidencias y próximos pasos

Custodia privada: `antes.json`, `despues.json`, `comparacion.json`, `certificacion-antes/despues.json`, `auditoria-final-runtime.json`, resultados H2/MySQL/frontend/Python/AV, `adulto-ui.json`, verificación del documento, validación SIGER Linux, hashes/JAR/imágenes y logs de cierre, bajo la raíz privada indicada. No adjuntar esa raíz completa: contiene secretos/copias institucionales y estado del ensayo.

Continuidad exacta y límites Windows: [manual separado](MANUAL_OPERACION_REVERSION_FORMULARIO_PISEE_SIGER_2026-11-03.md). Para cerrar los gates pendientes hacen falta identidad de prueba y finalidad autorizadas para el adaptador real, acceso/coordinación Windows, nuevo ejecutor/perfil/base/custodia y claves Windows propias. No se reabre U4.4, no se reutiliza su helper fijado a U4 y no se habilita ciudadanía aunque el laboratorio esté verde.
