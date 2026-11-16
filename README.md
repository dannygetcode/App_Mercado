# Mercado

App Android personal para llevar la cuenta del mercado contra el presupuesto: escanear, sumar,
listo. Todo corre en el teléfono: sin internet, sin cuenta, sin servidor. La base de datos va
cifrada y la app se protege con la huella o el PIN del teléfono.

**Qué hace (v1.0.0):** compra en curso con escáner continuo y total con semáforo; precio suelto
con teclado propio; lectura del precio de la etiqueta del estante; catálogo con favoritos y
códigos; historial y resumen del mes por categoría, productos y variaciones de precio; lista de
mercado sugerida a partir de las compras; recordatorios; respaldo cifrado con contraseña.

## Compilar

Requisitos: JDK 17 y el Android SDK (plataforma 37). En Windows usar Git Bash.

```bash
./scripts/verificar.sh rapido      # pruebas de :domain + compilación debug
./scripts/verificar.sh completo    # todo: pruebas, lint, APK debug y chequeos de seguridad
./gradlew :app:assembleDebug       # app-debug.apk  (paquete co.daniel.mercado.debug)
./gradlew :app:assembleQa          # app-qa.apk     (optimizada, firmada con la llave de debug)
./gradlew :app:assembleRelease     # app-release.apk (necesita keystore.properties)
```

La versión release se firma con la llave de Daniel. Cómo crearla y el formato de
`keystore.properties` (nunca va a git) están en `docs/07-release.md`.

## Instalar

Con el teléfono por USB y la depuración USB activa:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

En HyperOS (Poco) `adb install` está bloqueado; usar:

```bash
export MSYS_NO_PATHCONV=1
adb push app/build/outputs/apk/release/app-release.apk /data/local/tmp/mercado.apk
adb shell pm install -r /data/local/tmp/mercado.apk
```

Sin cable: copiar el APK al teléfono y abrirlo desde el gestor de archivos. **Actualizar siempre
encima, con la misma llave y sin desinstalar**: desinstalar borra los datos.

## Respaldar

Ajustes → Datos → **Exportar respaldo**: un archivo `mercado-AAAA-MM-DD.mercado` cifrado con una
contraseña que eliges (mínimo 8 caracteres; no se guarda en ningún lado). Para volver:
**Restaurar respaldo**, elegir el archivo, escribir la contraseña y confirmar; reemplaza todos
los datos. La app recuerda hacer un respaldo cada mes. Haz uno antes de cambiar de teléfono o
de instalar una versión nueva.

Para pasar los datos de la app de prueba (debug) a la final: exportar en la de prueba y restaurar
en la final.

## Documentación

| Ruta | Contenido |
| --- | --- |
| `docs/` | Especificación: producto, arquitectura, datos, UX, seguridad, pruebas, release |
| `plan/PLAN.md`, `plan/PROGRESO.md` | Loops de trabajo, avance, evidencia de seguridad S-01..S-15 |
| `plan/DECISIONES.md` | Decisiones tomadas durante la implementación |
| `CHANGELOG.md` | Cambios por versión |
| `CLAUDE.md`, `plan/PROMPTS.md` | Reglas y prompts para el agente que implementa la app |
| `seed/` | Recibo y catálogo inicial |
