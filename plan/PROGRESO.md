# Progreso

Lo mantiene el agente. Una sesión nueva empieza leyendo este archivo.

**Loop actual:** L2
**Última actualización:** 2026-11-16
**Pasos `[HUMANO]` pendientes:** L0 instalar APK debug y confirmar que abre (no bloqueante)

## Tablero

| Loop | Estado | Puerta de salida |
| --- | --- | --- |
| L0 Base del proyecto | cerrado | verde 2026-11-16 (humano no bloqueante pendiente) |
| L1 Dominio | cerrado | verde 2026-11-16 |
| L2 Datos cifrados | en curso | — |
| L3 Escáner | pendiente | — |
| L4 Compra en curso | pendiente | — |
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
- [ ] T1 — 
- [ ] T2 — 
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
