# 05 · Seguridad

Prioridad alta declarada por el dueño del producto. Cada control tiene una verificación; un control
sin verificación no cuenta como hecho.

## Qué se protege

Hábitos de consumo, gastos y presupuesto del hogar. No hay credenciales, pagos ni datos de terceros.

## Controles obligatorios

| ID | Control | Implementación | Verificación |
| --- | --- | --- | --- |
| S-01 | Sin acceso a red | El manifiesto principal declara `<uses-permission android:name="android.permission.INTERNET" tools:node="remove"/>` y lo mismo para `ACCESS_NETWORK_STATE` y `ACCESS_WIFI_STATE`. `android:usesCleartextTraffic="false"` | `verificar.sh` falla si el manifiesto fusionado de release contiene alguno de esos permisos. Prueba manual: la app funciona en modo avión |
| S-02 | Base cifrada | SQLCipher con clave de 32 bytes de `SecureRandom`; `SupportOpenHelperFactory` | Prueba instrumentada: abrir `mercado.db` con SQLite plano falla; los primeros 16 bytes del archivo no son `SQLite format 3` |
| S-03 | Clave protegida | La clave de la base se cifra con AES-256-GCM usando una clave del Android Keystore (alias `mercado_db_kek`, no exportable, StrongBox si existe y si no TEE). El blob se guarda en `noBackupFilesDir/db.key` | Prueba instrumentada: ida y vuelta de `DbKeyManager`; el blob no contiene la clave en claro |
| S-04 | Sin copias automáticas | `android:allowBackup="false"`, `android:fullBackupContent="false"`, `dataExtractionRules` que excluyen todo (nube y transferencia entre dispositivos) | Revisión del manifiesto fusionado en `verificar.sh` |
| S-05 | Bloqueo de la app | `BiometricPrompt` (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`). Se pide al abrir y al volver tras superar la gracia. Mientras está bloqueada no se compone ninguna pantalla con datos | Prueba Robolectric de `AppLock` (lógica de gracia con `Clock` falso) + prueba manual |
| S-06 | Pantalla segura | `FLAG_SECURE` en la ventana cuando `secure_screen` está activo (por defecto sí en release; desactivado en debug) | Prueba manual: recientes muestra pantalla en blanco; no deja capturar |
| S-07 | Cámara sin rastro | Solo `Preview` + `ImageAnalysis`; sin `ImageCapture`; ningún cuadro a disco | Revisión de código: no hay escritura de imágenes |
| S-08 | Respaldo cifrado | Formato de `03-datos.md` (PBKDF2 600.000 + AES-256-GCM, AAD sobre la cabecera) | Pruebas JVM: ida y vuelta; contraseña incorrecta falla; un byte alterado falla; cabecera alterada falla |
| S-09 | Importaciones validadas | Límites de tamaño, validación completa antes de escribir, transacción todo-o-nada | Pruebas con archivos malformados, gigantes y con valores fuera de rango |
| S-10 | Sin datos en logs | Envoltorio `AppLog` que no hace nada en release; prohibido `Log.*` directo y `println` | `verificar.sh` busca `Log\.` y `println(` fuera de `AppLog` |
| S-11 | Release endurecido | `isDebuggable=false`, R8 activo, sin `android:exported="true"` salvo la actividad del lanzador, sin `ContentProvider` propio exportado | Revisión del manifiesto fusionado |
| S-12 | Firma | APK firmado con la llave de Daniel (esquema v2+). `keystore.properties` y el `.jks` fuera de git | `apksigner verify --print-certs`; `git status` limpio de secretos |
| S-13 | SQL parametrizado | Solo consultas de Room con parámetros; sin `SimpleSQLiteQuery` armado con texto del usuario | Revisión de código |
| S-14 | Permisos mínimos | Solo `CAMERA`, `POST_NOTIFICATIONS`, `VIBRATE`, `USE_BIOMETRIC` (+ los internos de WorkManager: `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE` si los agrega) | `verificar.sh` compara la lista de permisos del manifiesto fusionado contra esta lista blanca |
| S-15 | Dependencias | Versiones fijas en el catálogo; solo artefactos de Google Maven y Maven Central | Revisión de `settings.gradle.kts` |

## Lista blanca de permisos (para `verificar.sh`)

```
android.permission.CAMERA
android.permission.POST_NOTIFICATIONS
android.permission.VIBRATE
android.permission.USE_BIOMETRIC
android.permission.USE_FINGERPRINT
android.permission.RECEIVE_BOOT_COMPLETED
android.permission.WAKE_LOCK
android.permission.FOREGROUND_SERVICE
co.daniel.mercado.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

Cualquier otro permiso en el manifiesto fusionado de release hace fallar la verificación. Si una
dependencia agrega uno, se elimina con `tools:node="remove"` y se anota en DECISIONES.

## ML Kit sin red (D-07)

La variante bundled de ML Kit arrastra el permiso de red para telemetría. Al quitarlo:

- El decodificador debe seguir funcionando (el modelo va dentro del APK).
- Pueden aparecer errores de transporte en logcat; son esperados e inofensivos.
- Si al quitar el permiso la app se cae o no decodifica (se comprueba en L3 en un teléfono real en
  modo avión), se cambia a `io.github.zxing-cpp:android` implementando `BarcodeDecoder`. No se
  devuelve el permiso de red bajo ninguna circunstancia.

## Riesgos aceptados (decisión del dueño, no reabrir)

- La clave de la base no está ligada a la biometría (D-09): un atacante con root y el teléfono
  desbloqueado podría leer la base.
- El APK se instala por fuera de Play Store: la autenticidad depende de que Daniel solo instale
  lo que él mismo compiló y firmó.

## Lista de verificación de release (se ejecuta en L8)

- [ ] `verificar.sh completo` en verde sobre la variante release.
- [ ] Manifiesto fusionado: sin permisos de red, `allowBackup=false`, `debuggable` ausente.
- [ ] Modo avión: flujo completo de compra funciona.
- [ ] `adb backup` / copia del directorio de datos no entrega una base legible.
- [ ] Recientes y capturas bloqueadas con `secure_screen` activo.
- [ ] Bloqueo: pide huella al abrir; no la pide al volver antes de 5 min; la pide después.
- [ ] Respaldo: exportar, borrar datos de la app, restaurar, verificar que todo volvió.
- [ ] `apksigner verify` correcto; el `.jks` no está en el repo.
