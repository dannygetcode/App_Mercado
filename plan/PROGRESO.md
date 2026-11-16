# Progreso

Lo mantiene el agente. Una sesión nueva empieza leyendo este archivo.

**Loop actual:** L4
**Última actualización:** 2026-11-16
**Pasos `[HUMANO]` pendientes:** L2 revisar la semilla (no bloqueante)

## Tablero

| Loop | Estado | Puerta de salida |
| --- | --- | --- |
| L0 Base del proyecto | cerrado | verde 2026-11-16 (humano no bloqueante pendiente) |
| L1 Dominio | cerrado | verde 2026-11-16 |
| L2 Datos cifrados | cerrado | verde 2026-11-16 (2 humanos no bloqueantes pendientes) |
| L3 Escáner | cerrado | verde 2026-11-16; prueba en modo avión 10/10 |
| L4 Compra en curso | en curso | — |
| L5 Resto de pantallas | pendiente | — |
| L6 Seguridad y respaldo | pendiente | — |
| L7 Lista, resúmenes, recordatorios | pendiente | — |
| L8 Release | pendiente | — |
| L9 Prueba de campo | pendiente | — |

Estados: `pendiente` · `en curso` · `esperando humano` · `cerrado`.

## Bitácora

Formato por loop (copiar el bloque al empezar cada uno):

```
### L<n> · <nombre>
- [x] T1 — `BarcodeDecoder` (interfaz de `02-arquitectura.md`) y `MlKitBarcodeDecoder` (ML Kit bundled 17.3.0, solo EAN-13, EAN-8, UPC-A, UPC-E y Code 128; cierra cada `ImageProxy`; ignora resultados tras `close()`). `ScanFilter.firstValid` acepta la primera lectura del cuadro con dígito de control válido (vía `Ean.normalize`). CameraX 1.6.2 agregado. 4 pruebas JVM.
- [x] T2 — `ScannerView`: `PreviewView` (modo PERFORMANCE) en Compose + `ImageAnalysis` (`KEEP_ONLY_LATEST`, 1280×720, cámara trasera, autoenfoque continuo por defecto) con un hilo de análisis propio; sin `ImageCapture` (S-07). Pausa (suelta la cámara y muestra "toca para reanudar"), linterna, aviso de cámara arrancada (`STREAMING`), error de cámara visible; se suelta al salir de la composición. `CameraPermissionGate`: pide el permiso al entrar; negado → panel "Sin acceso a la cámara" + "Permitir" (abre ajustes si ya no se puede pedir) sin bloquear la pantalla; revisa al volver. `CAMERA` en el manifiesto (`camera.any` no requerida). 3 pruebas Robolectric.
...
**Informe de cierre**
- Verificación: <salida resumida de verificar.sh completo>
- Medido: <tiempos, tamaños, conteos de toques, lo que aplique>
- Desviaciones de la especificación: <ninguna | cuáles y por qué (también en DECISIONES)>
- Pendiente para Daniel: <pasos [HUMANO] con instrucciones exactas>
```

### L0 · Base del proyecto
- [x] T1 — Entorno: JDK 17.0.13, git 2.45.2, SDK en `%LOCALAPPDATA%\Android\Sdk` (plataformas 36/37, build-tools 36), adb 36.0.0 fuera del PATH (se usa por ruta). `local.properties` creado.
- [x] T2 — `gradle/libs.versions.toml` con las últimas estables (AGP 9.5.1, Kotlin 2.4.21, KSP 2.3.14, Compose BOM 2026.09.00…); detalle en DECISIONES.
- [x] T3 — `git init`, `.gitignore` + `.gitattributes` (LF), wrapper Gradle 9.9.1 con SHA-256, `:domain` (kotlin-jvm, JUnit Jupiter) y `:app` (Compose, KSP, serialization, minSdk 26, compile/target 37, `.debug`, R8, firma opcional). Compila.
- [x] T4 — Manifiesto: INTERNET/ACCESS_NETWORK_STATE/ACCESS_WIFI_STATE con `tools:node="remove"`, `allowBackup`/`fullBackupContent` false, `data_extraction_rules.xml` excluye todo, `usesCleartextTraffic=false`; se elimina `ProfileInstallReceiver` (exportado) por S-11. Retrato se fija en la actividad (T5). Manifiesto fusionado de release revisado.
- [x] T5 — `MercadoApp`, `MainActivity` (retrato, edge-to-edge), tema M3 propio claro/oscuro con colores de `04-ux.md` y semáforo en `LocalTrafficLightColors`, estilos con cifras tabulares, `HomeScreen` provisional "Mercado", `AppContainer` vacío, `AppLog` (no-op en release), ícono adaptativo vectorial. `assembleDebug` OK.
- [x] T6 — `scripts/verificar.sh` (`rapido`/`completo`) con todos los chequeos de `06-pruebas.md` más dos de S-11 (release no depurable, solo el lanzador exportado); resumen de una línea por chequeo. Patrones probados en negativo. Pruebas triviales: `DomainSmokeTest` (JUnit Jupiter) y `HomeScreenTest` (Robolectric + Compose). Lint sin hallazgos.
- [x] T7 — `CHANGELOG.md` inicial. El primer commit se hizo en T3 (al hacer `git init`), porque el protocolo pide un commit por tarea; T7 agrega el changelog en su propio commit.
**Informe de cierre**
- Verificación: `verificar.sh completo` → VERDE (gradle pruebas + lint + assembleDebug + manifiesto release; permisos en lista blanca; allowBackup=false; release no depurable; solo el lanzador exportado; sin Log/println; dominio sin android; sin Float/Double; sin migración destructiva). Lint: "No issues found".
- Medido: APK debug 11,9 MB (`app/build/outputs/apk/debug/app-debug.apk`); 2 pruebas (1 dominio, 1 Robolectric) en verde; build completo en frío ≈ 2 min, incremental < 15 s.
- Desviaciones de la especificación: Kotlin 2.4.21 (sobre el 2.2.10 que trae AGP), compile/target SDK 37, Robolectric con sdk=34 por JDK 17, receptor de profileinstaller eliminado, dos chequeos extra en `verificar.sh`. Todo en DECISIONES.
- Pendiente para Daniel: `[HUMANO, no bloqueante]` con el teléfono conectado por USB (depuración USB activa), desde esta carpeta en Git Bash:
  `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk`
  y confirmar que el ícono "Mercado" abre y muestra la palabra "Mercado" centrada. (Al momento del cierre `adb devices` no mostró ningún teléfono.)

### L1 · Dominio
- [x] T1 — Modelo: `Pesos` (aritmética exacta), `UnitType`, `Category`, `Product`, `PricePoint`/`PriceSource`, `Trip`, `TripItem` (con `unitType` y `lineTotal`), `TripWithItems`, `ActiveTrip`, `Budget`, `MonthStatus`, `TrafficLight`, `ScanOutcome`; mes con `java.time.YearMonth`. `LineMath` adelantado porque `TripItem.lineTotal` lo usa.
- [x] T2 — Reglas: `MoneyFormat`, `Ean` (EAN-13/8, UPC-A→EAN-13, UPC-E expandido, Code 128), `ScanDebouncer` (ventana por código desde la última aceptación), `BudgetRules` (semáforo, límite por defecto, porcentaje, cruces), `LineMath`, `NameNormalizer`, `Months` (mes en hora de Bogotá). 24 pruebas; `DomainSmokeTest` de L0 reemplazada por las pruebas reales. `Clock` adelantado (lo usa el antirrebote).
- [x] T3 — Puertos: `ProductRepository` (código, búsqueda, favoritos como `Flow`, códigos, categorías, historial de precios), `TripRepository` (compra abierta como `Flow`, líneas con id conservable para deshacer, gastado por rango), `BudgetRepository` (mes y herencia), `Clock`, `TransactionRunner`.
- [x] T4 — Falsos en `domain/src/test/.../fakes`: `InMemoryProductRepository`, `InMemoryTripRepository` (con `Flow` reactivo vía `MutableStateFlow`), `InMemoryBudgetRepository`, `ImmediateTransactionRunner`, `FakeClock` (con helper de hora de Bogotá).
- [x] T5 — Casos de uso de compra: `StartTrip` (con `TripLimit` Default/None/Custom y `defaultLimit()`), `ObserveActiveTrip`, `HandleScan`, `AddProductToTrip`, `AddUnknownScanned`, `AddLoosePrice`, `AddWeighedItem`, `ChangeQuantity`, `ChangeLinePrice`, `RemoveLine`, `RenameLooseLine`, `UndoLastAction`, `CloseTrip`, `DiscardTrip`, `SetPaidTotal`. `TripSession` serializa con `Mutex` + transacción; `UndoBuffer` de tamaño 1 con pasos de reversión. `GetMonthStatus`/`SetMonthlyBudget` adelantados (los necesita `StartTrip`). 36 pruebas nuevas.
- [x] T6 — Presupuesto y catálogo: `GetMonthStatus` (herencia del mes anterior más reciente), `SetMonthlyBudget`, `SearchProducts`, `SaveProduct` (validación, duplicados por nombre normalizado, favoritos al final, historial `MANUAL`), `DeactivateProduct`, `LinkBarcode` (normaliza; reasigna si era de otro), `ObserveFavorites`. 11 pruebas nuevas.
**Informe de cierre**
- Verificación: `verificar.sh completo` → VERDE (9/9 chequeos; incluye dominio sin android y sin Float/Double). Lint sin hallazgos.
- Medido: 71 pruebas de dominio en verde (reglas 24, compra 36, presupuesto 5, catálogo 6); `:domain:test` completo en ≈ 11 s. Todos los casos obligatorios de dominio de `06-pruebas.md` existen y pasan, salvo `SuggestionRules` (L7, excluido por la puerta).
- Desviaciones de la especificación: ninguna de fondo. Detalles decididos con "la vara única" en DECISIONES (Code 128 solo alfanumérico, `TransactionRunner`, deshacer de varios pasos, reglas de historial al cerrar, reasignación de códigos). `Clock`, `LineMath`, `GetMonthStatus` y `SetMonthlyBudget` se adelantaron a la tarea que los necesitó.
- Pendiente para Daniel: nada de L1. Sigue pendiente el paso no bloqueante de L0 (instalar el APK debug).

### L2 · Datos cifrados
- [x] T1 — Entidades de las 8 tablas de `03-datos.md` (incluida `shopping_list_item` ya en v1), `Converters` (`Pesos`↔INTEGER, `YearMonth`↔TEXT), DAOs (`CategoryDao`, `ProductDao`, `TripDao`, `BudgetDao`) con las consultas pedidas, compra abierta con líneas y producto en una sola consulta `@Transaction`, `MercadoDatabase` v1 con `exportSchema` a `app/schemas/`, callback que siembra las 10 categorías y activa `foreign_keys`.
- [x] T2 — `DatabaseFactory`: `SqlCipherDatabaseFactory` (SQLCipher con `SupportOpenHelperFactory`, WAL, clave cruda `x'…'`) e `InMemoryDatabaseFactory` (Room en memoria sin cifrar para Robolectric). `System.loadLibrary("sqlcipher")` en `MercadoApp.onCreate`. Robolectric usa `android.app.Application` (no carga la librería nativa).
- [x] T3 — `DbKeyManager` (S-03): 32 bytes de `SecureRandom`, envueltos por `KeystoreKeyWrapper` (AES-256-GCM, alias `mercado_db_kek`, StrongBox si existe y si no TEE, sin autenticación por D-09), archivo `noBackupFilesDir/db.key` con formato `MDK1`, escritura atómica. Nunca regenera la clave si la base ya existe (`DbKeyException`). 4 pruebas JVM con envoltorio por software.
- [x] T4 — Repositorios Room: `RoomProductRepository`, `RoomTripRepository`, `RoomBudgetRepository` y `RoomTransactionRunner` (`withTransaction`), con mapeadores entidad↔modelo; `name_norm` se calcula al mapear; borrar un producto provisional limpia códigos, historial y lista en una transacción. Las operaciones transaccionales de `03-datos.md` corren dentro de `TripSession.mutate` → `withTransaction`. Pruebas en T8.
- [x] T5 — `SeedImporter` + `SeedFileDto`/`SeedProductDto`: límite 2 MB y 5.000 productos, versión 1, nombre 1–80, categoría (se crea si no existe), unidad `UNIT`/`KG`, precio 1–50.000.000, códigos por `Ean` y sin repetirse entre productos; valida todo y escribe en una transacción; error con índice del producto. Idempotente por `name_norm`; punto `SEED` en el historial solo para `UNIT` y si cambió. `InitialCatalogLoader` importa `assets/seed/catalogo_inicial.json` si la base no tiene productos (la bandera `seed_imported` se conecta en T8). Pruebas en T8.
- [x] T6 — Semilla real desde `seed/recibo.pdf` (La Granja, 13/10/2026, 57 renglones, total $497.870): 48 productos en `seed/catalogo_inicial.json`, copiado a `app/src/main/assets/seed/`. Todos `UNIT` con su código (todos pasan `Ean`). Cuadre: exento $99.850 ✓, IVA 5 % $59.270 ✓, IVA 19 % $335.500 + renglón 30 ($3.250) = $338.750 ✓, total $497.870 ✓. Los repetidos (crema dental, jabón Tigre, crema de maní, nueces, avena, queso campesino, filtro) quedaron una sola vez.
  **Renglones dudosos para que Daniel revise** (en el catálogo o en la app):
  1. Renglón 30: no aparece en el PDF (salto de página). Vale $3.250 con IVA 19 %. No se incluyó: ¿qué producto era?
  2. Renglón 25 «Cilantro Jardín Medicinal»: el recibo se lee `7707195760330` (dígito de control inválido); se usó `7707195750330`, que sí valida y sigue la serie de Jardín Medicinal. Confirmar escaneándolo.
  3. Renglón 44 «Café Tostao selecto 454 g»: precio de lista $24.800 con 10 % de descuento; se tomó lo pagado, $22.320.
  4. Renglón 13 «Pan Emanuel integral»: el tamaño quedó cortado («X 45… G»).
  5. Renglón 24 «Salsa de tomate Fruco»: tamaño ilegible («X8»).
  6. Renglón 41 «Kéfir Alpina Plus»: tamaño ilegible («X 24»).
  7. Renglón 46 «Doritos mega queso»: tamaño ilegible («X 8»).
  8. Renglón 51 «Kumis entero Bachué»: el recibo dice 200 ml pero vale $13.000; ¿es de 2.000 ml?
  9. Renglón 52 «Maní natural Maniksero»: tamaño cortado («X 2…5G», quizá 225 g); confirmar también la marca «Maniksero».
  10. Renglón 14 «Champiñones enteros 250 g»: marca ilegible («CHAMPI Y»); único marcado favorito (verdura).
  11. Renglón 28 «Té Hindú premium chai»: el recibo dice «X2 UND X 32 G» por $12.000; confirmar presentación.
  12. Categorías elegidas a criterio: vinagre y filtros de café en «Granos y despensa»; jabón Tigre y guantes en «Aseo del hogar»; arepas, calados y galletas en «Panadería».
  No hay productos por peso en este recibo, así que ninguno es `KG`.
- [x] T7 — `SettingsStore` (DataStore Preferences, archivo `settings`) con las 10 claves y valores por defecto de `03-datos.md`; `secure_screen` por defecto según variante (S-06); valida gracia {1, 5, 15} y frecuencia 1–90 días. 4 pruebas.
- [x] T8 — `AppContainer`: ajustes disponibles al instante; la base se crea y se abre en segundo plano (`Deferred<DataGraph>`, `data()`), y en el primer arranque importa la semilla y marca `seed_imported`. `DataGraph` con repositorios Room y todos los casos de uso de L1. Pruebas JVM: `DaoTest` (8: categorías sembradas, código, búsqueda sin tildes, comodines, total y gastado del mes, cascada, claves foráneas, posición), `RoomTripFlowTest` (4: invariante con 5 inicios simultáneos, flujo completo con deshacer y cierre, KG, deshacer desconocido), `SeedImporterTest` (8: ejemplo, reimportar no duplica, actualización, categoría nueva, todo-o-nada con índice, cada validación, tamaño y cantidad, semilla real 48), `AppContainerTest`, `RawKeyTest`. Instrumentadas `EncryptedDbTest` y `DbKeyManagerTest` escritas y compiladas; no se ejecutaron (sin teléfono).

**Informe de cierre**
- Verificación: `verificar.sh completo` → VERDE (9/9). Lint: "No issues found". `compileDebugAndroidTestKotlin` OK.
- Medido: 71 pruebas de dominio + 31 pruebas de app (JVM) en verde. APK debug 23,7 MB (debug sin R8 y con SQLCipher para todas las ABI; el release filtra ABI y reduce). Semilla real: 48 productos, cuadre exacto con el recibo ($497.870).
- Desviaciones de la especificación: ninguna de fondo. Decisiones en DECISIONES (clave cruda de SQLCipher, `KeyWrapper`, `shopping_list_item` en v1, DataStore en memoria en pruebas por Windows).
- Pendiente para Daniel (no bloqueante):
  1. Con el teléfono conectado por USB y depuración USB activa, desde la carpeta del proyecto en Git Bash: `./gradlew :app:connectedDebugAndroidTest`. Deben pasar `EncryptedDbTest` y `DbKeyManagerTest` (3 + 1 pruebas). Si no tienes tiempo, dímelo y la lanzo yo en cuanto `adb devices` muestre el teléfono.
  2. Revisar `seed/catalogo_inicial.json` con la lista de renglones dudosos de T6 (arriba). Las correcciones me las dictas o las editas en el archivo, y yo actualizo la copia de `assets`.

### L3 · Escáner
- [x] T1 — Componentes en `ui/components`: `PriceKeypad` + `KeypadState` (9 dígitos, sin ceros a la izquierda, `00`/`000`, borrar, valor prellenado que se reemplaza con la primera tecla, teclas de 64 dp, "Sumar" deshabilitado en 0), `TotalCard` (total 46 sp tabular que se achica antes de cortarse, artículos, barra y saldo con color del semáforo, "Te pasaste por", sin límite solo total), `LineRow` (nombre tocable, precio tocable con punto "precio viejo", `−`/`+` de 48 dp, línea KG sin cantidad, resaltado), `UndoBar`. Todos con `@Preview`. 5 pruebas de `KeypadState` + 5 de UI (teclado 1·2·000 → $12.000, borrar, semáforo verde→amarillo→rojo, sin límite, línea suelta).
- [ ] T2 — 
- [x] T3 — `ScanFeedback` / `DefaultScanFeedback`: aceptada = vibración 30 ms + pitido corto (`ToneGenerator`, canal de notificaciones: respeta el modo silencio); desconocida = vibración doble sin pitido. Lee los ajustes `scanSound`/`scanVibration` en cada lectura desde un `StateFlow` del `AppContainer`. Permiso `VIBRATE`. `AppContainer.newBarcodeDecoder()` entrega ML Kit. 5 pruebas JVM.
- [x] T4 — Pantalla de diagnóstico (solo debug; botón "Diagnóstico del escáner" en el Inicio provisional, nulo en release): visor al 32 %, linterna, pausa/reanudar, pantalla encendida; muestra cámara → primera lectura (se reinicia al reanudar), lecturas en el último minuto, leídas y rebotes ignorados (mismo `ScanDebouncer` de 1.500 ms), y la lista de códigos con el tiempo desde la lectura anterior. Cada lectura aceptada vibra/pita. Estado inmutable `ScanDiagnostics` con 7 pruebas JVM. `BarcodeSource`/`SharedBarcodeSource`: la cámara solo hace `offer(code)`.
- [x] T5 — `FakeBarcodeSource` en `src/debug` (compartida por `test` y `androidTest`, fuera de release): `emit` espera a que haya quien escuche. 2 pruebas Robolectric de la pantalla de diagnóstico sin permiso (códigos falsos llegan a la lista, rebote contado). `ScannerSmokeTest` instrumentada: concede `CAMERA`, abre el diagnóstico desde `MainActivity`, espera la cámara, pausa y reanuda sin caerse. Compilada; sin teléfono conectado no se ejecutó (paso humano).
- [x] T6 — Manifiesto fusionado de release con ML Kit incluido: sin `INTERNET`, `ACCESS_NETWORK_STATE` ni `ACCESS_WIFI_STATE` (el informe del merger muestra `REJECTED` para los que traen `datatransport` de ML Kit y `media3-common` de CameraX). Permisos finales: `CAMERA`, `VIBRATE` y el interno `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Los componentes de ML Kit, CameraX y `datatransport` quedan `exported="false"`. Nueva comprobación en `verificar.sh`: "ML Kit sin permisos de red (D-07)" (falla si ML Kit no está en el manifiesto o si aparece un permiso de red).
**Informe de cierre**
- Verificación: `./scripts/verificar.sh completo` → VERDE, 10/10 (gradle pruebas + lint + assembleDebug + manifiesto release; permisos en lista blanca; ML Kit sin permisos de red; allowBackup=false; release no depurable; solo el lanzador exportado; sin Log/println; dominio puro; sin Float/Double; sin migración destructiva). Lint: "No issues found". Pruebas: 71 de dominio + 52 JVM de la app, 0 fallas. `ScannerSmokeTest` compilada, no ejecutada (sin teléfono).
- Medido: APK debug 48,2 MB (antes 23,7 MB; ML Kit bundled y CameraX, todas las ABI en debug; release filtra a arm64-v8a/armeabi-v7a y R8 llega en L8). Tiempos de cámara y lectura: pendientes de la prueba humana.
- Desviaciones de la especificación: ninguna. Decisiones menores en DECISIONES (pitido por canal de notificaciones, permiso pedido al entrar, `FakeBarcodeSource` en `src/debug`, humo sobre el diagnóstico hasta L4).
- Pendiente para Daniel: `[HUMANO, BLOQUEANTE]` prueba en modo avión con la pantalla de diagnóstico (instrucciones en el mensaje de cierre de la sesión y abajo).
  1. Conectar el teléfono por USB con depuración USB activa e instalar: `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk` (o pedirle al agente que lo instale).
  2. Desconectar el cable y activar **modo avión** (Wi-Fi y datos apagados).
  3. Abrir Mercado → "Diagnóstico del escáner" → aceptar el permiso de cámara.
  4. Leer 10 productos de la casa, uno tras otro, apuntando a unos 15–25 cm.
  5. Reportar: ¿se cerró la app en algún momento?, ¿cuántos de los 10 leyó al primer intento?, ¿se sintió instantáneo?, y el valor de "Cámara → primera lectura" y los tiempos "+x,xx s" de la lista.
  - Revisión de código posterior (skill engineering:code-review): el hilo de análisis se apagaba antes de soltar la cámara (posible `RejectedExecutionException` al salir) y un fallo de `ProcessCameraProvider` no se capturaba (cierre de la app). Corregido: un ejecutor por enlace apagado tras `unbind`, y cualquier fallo al abrir la cámara muestra "No se pudo abrir la cámara". `verificar.sh completo` VERDE de nuevo.
  - 2026-11-16, en el Poco X8 Pro (HyperOS, Android 16): APK debug instalado y abre (cierra el humano de L0). `EncryptedDbTest` 1/1 y `DbKeyManagerTest` 3/3 pasan en el teléfono (cierra el humano de L2). `ScannerSmokeTest` falló al conceder el permiso por shell (HyperOS lo niega: `GRANT_RUNTIME_PERMISSIONS`); ahora usa el permiso si ya fue concedido desde la app; con el permiso dado a mano, `ScannerSmokeTest` 1/1 pasa en el teléfono (abre el visor, arranca la cámara con ML Kit, pausa y reanuda sin caerse). Instalación: `adb install` y `connectedDebugAndroidTest` fallan en HyperOS (`INSTALL_FAILED_USER_RESTRICTED` / flag `-g`); funciona `adb push` a `/data/local/tmp` + `adb shell pm install -r -t` y `am instrument -w`. En Git Bash hace falta `MSYS_NO_PATHCONV=1` para rutas del teléfono.
  - Fallo encontrado en el teléfono: con `PreviewView` en modo PERFORMANCE (SurfaceView) + FILL_CENTER, Compose no recortaba el visor y tapaba la barra superior y el resto de la pantalla (Daniel lo vio como "cámara apagada / pantalla de carga"). Corregido con modo COMPATIBLE (TextureView) + `clipToBounds`. No hubo ningún cierre por error: los cierres en el registro son `SwipeUpClean` (deslizar en recientes). Verificado por captura: visor en su 32 %, botones visibles, UPC-A leído con cámara → primera lectura 0,23 s. Botón del Inicio provisional ahora es un botón lleno de 56 dp. Debug: `am start ... --ez open_diagnostics true` abre el diagnóstico sin tocar la pantalla.
  - Si se cae o no lee sin red → `ZxingCppBarcodeDecoder` y repetir. Si lee < 8 de 10 → ajustar resolución/enfoque y repetir.
- **Resultado de la prueba humana (2026-11-16, Poco X8 Pro, HyperOS / Android 16):** modo avión activo (verificado por adb: `airplane_mode_on=1`), 10 de 10 productos leídos al primer intento, sin cierres (sin entradas en el búfer de fallos ni `am_crash`/`am_anr`). Daniel: "lee muy rápido". Primera lectura medida antes: 0,23 s. **D-07 se mantiene: ML Kit bundled; zxing-cpp no hace falta.** L3 cerrado.

### L4 · Compra en curso
- Preparación de dominio: `AddUnknownScanned` acepta `name` (nombre tecleado sin elegir del autocompletado: reutiliza el producto de ese nombre o crea uno ya nombrado sin revisión pendiente) y `code` nulo ("Crear «texto»" desde la búsqueda, sin asociar código). `TripRepository.recentStores` (Room: agrupado por tienda, la más reciente primero) para los chips del cierre. 3 pruebas de dominio + 1 Room.
- [ ] T1 — 
- [ ] T2 — 
- [ ] T3 — 
- [ ] T4 — 
- [ ] T5 — 
- [ ] T6 — 
- [ ] T7 — 
- [ ] T8 — 
- [ ] T9 — 
- [ ] T10 — 
- [ ] T11 — 
**Informe de cierre**
