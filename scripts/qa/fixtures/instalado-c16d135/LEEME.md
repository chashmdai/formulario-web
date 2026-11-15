# Herramientas instaladas en producción (c16d135)

Copia exacta de `scripts/ubuntu/{respaldo,respaldo-programado,productor}.py` del commit
`c16d135690dce07e1e3a52b12d83fd71fa9650fa`, última versión desplegada documentada (07/11/2026).
Estos archivos no cambiaron desde `ee68edc` (04/11/2026), cuando se instaló `release/`.

Sólo se usan como fixture de `scripts/qa/test_actualizacion_desde_anterior.py`: la CI clona sin
historial y necesita probar el procedimiento de actualización contra la versión instalada.
No ejecutar estos archivos fuera de las pruebas ni editarlos.
