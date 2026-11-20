# 07 · Release, instalación y mantenimiento

## Llave de firma (una sola vez, paso `[HUMANO]`)

La crea Daniel, no el agente, porque la contraseña es suya:

```bash
keytool -genkeypair -v -keystore mercado-release.jks -alias mercado \
  -keyalg RSA -keysize 4096 -validity 10000
```

- Guardar `mercado-release.jks` **fuera** del repositorio (por ejemplo `C:\Users\Usuario\Desktop\main\02_Proyectos_Codigo\_keys\`).
- Copias de respaldo en dos lugares sin conexión (USB + otro). Contraseña en un gestor de
  contraseñas. Si se pierde la llave, toca desinstalar y reinstalar la app, lo que borra los datos
  (por eso existe el respaldo cifrado).

`keystore.properties` en la raíz del repo (ignorado por git):

```properties
storeFile=C:/Users/Usuario/Desktop/main/02_Proyectos_Codigo/_keys/mercado-release.jks
storePassword=...
keyAlias=mercado
keyPassword=...
```

`app/build.gradle.kts` lee ese archivo si existe; si no existe, la variante release queda sin
firmar y el build no falla (para que el agente pueda compilar).

## Generar e instalar

```bash
./gradlew :app:assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

Sin cable: copiar el APK al teléfono y abrirlo desde el gestor de archivos. Permitir "instalar
apps desconocidas" solo para esa instalación y volver a desactivarlo.

**Actualizar siempre con la misma llave y sin desinstalar**, para conservar los datos. Las
variantes debug y release usan firmas distintas: debug lleva el sufijo `.debug` en el
`applicationId` para poder convivir con la release sin tocar sus datos.

## HyperOS (Poco X8 Pro)

- `adb install` falla con `INSTALL_FAILED_USER_RESTRICTED`. Lo que funciona (Git Bash):
  `export MSYS_NO_PATHCONV=1`, `adb push <apk> /data/local/tmp/mercado.apk` y
  `adb shell pm install -r -t /data/local/tmp/mercado.apk`. El shell no puede conceder permisos
  ni tocar la pantalla.
- Para probar la variante optimizada sin la llave: `./gradlew :app:assembleQa` (paquete
  `co.daniel.mercado.qa`, firmada con la llave de debug).
- Activar opciones de desarrollador, "Depuración USB" e "Instalar vía USB" (puede pedir iniciar
  sesión con cuenta Xiaomi y tener SIM).
- Al instalar, el escáner de seguridad de Xiaomi puede advertir que la app es desconocida: es lo
  esperado para un APK propio.
- Para que los recordatorios lleguen: Ajustes → Apps → Mercado → Ahorro de batería → "Sin
  restricciones", y activar "Inicio automático".
- Permitir notificaciones al primer aviso.

## Samsung (One UI)

- Ajustes → Batería → Límites de uso en segundo plano → sacar Mercado de "Apps en suspensión".

## Versionado

- `versionName` semántico (`1.0.0`), `versionCode` entero creciente.
- `CHANGELOG.md` en la raíz, una entrada por versión.
- Etiqueta git `v1.0.0` en cada release.

## Mantenimiento

- Respaldo cifrado mensual (la app lo recuerda).
- Antes de instalar una versión con cambio de esquema: exportar respaldo.
- Cada cambio de esquema: migración de Room + prueba + esquema exportado en `app/schemas/`.
- Revisión trimestral de versiones de dependencias (SQLCipher y CameraX primero).

## `.gitignore` mínimo

```
.gradle/
build/
local.properties
*.jks
*.keystore
keystore.properties
*.mercado
*.apk
.idea/
captures/
seed/recibo.*
```
