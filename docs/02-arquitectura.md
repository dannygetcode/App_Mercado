# 02 · Arquitectura

## Vista general

Una app, dos módulos Gradle, tres capas. Las dependencias solo apuntan hacia el dominio.

```
:app  (Android)                                   :domain  (Kotlin/JVM puro)
┌──────────────────────────────────────────┐      ┌─────────────────────────────┐
│ ui/        Compose + ViewModels           │ ───▶ │ model/    entidades, Pesos  │
│ data/      Room+SQLCipher, repositorios   │ ───▶ │ rules/    reglas puras      │
│ scanner/   CameraX + decodificador        │      │ usecase/  casos de uso      │
│ security/  Keystore, bloqueo, respaldo    │      │ port/     interfaces        │
│ reminders/ WorkManager, notificaciones    │      └─────────────────────────────┘
│ di/        AppContainer (DI manual)       │
└──────────────────────────────────────────┘
```

- `:domain` no conoce Android. Define los **puertos** (interfaces de repositorio, `Clock`) y los
  casos de uso. Se prueba con JUnit en el PC en segundos.
- `:app/data` implementa los puertos con Room. `:app/ui` solo habla con casos de uso.
- Patrón de UI: MVVM con flujo unidireccional. Cada pantalla tiene `XxxUiState` (data class
  inmutable), `XxxEvent` (sealed interface) y un `XxxViewModel` que expone `StateFlow<XxxUiState>`
  y un único `onEvent(event)`.

## Decisiones cerradas

| ID | Decisión | Razón |
| --- | --- | --- |
| D-01 | Android nativo: Kotlin + Jetpack Compose + Material 3 | Escáner y cifrado nativos sin puentes |
| D-02 | Dos módulos: `:domain` (kotlin-jvm) y `:app` | El compilador garantiza el dominio puro; pruebas rápidas |
| D-03 | DI manual con `AppContainer` (sin Hilt/Koin) | Menos plugins y menos riesgo de build; la app es pequeña |
| D-04 | Persistencia: Room + SQLCipher (`net.zetetic:sqlcipher-android`) | SQL tipado, migraciones, cifrado AES-256 de todo el archivo |
| D-05 | Dinero: `@JvmInline value class Pesos(val value: Long)` | Exactitud; COP no usa centavos |
| D-06 | Cámara: CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`) | Absorbe diferencias entre fabricantes |
| D-07 | Decodificador: ML Kit Barcode Scanning **bundled** (`com.google.mlkit:barcode-scanning`) detrás de la interfaz `BarcodeDecoder`. Los permisos de red que arrastra se eliminan del manifiesto con `tools:node="remove"`. **Alternativa predefinida:** `io.github.zxing-cpp:android` si ML Kit falla sin red (ver L3) | Mejor lectura en códigos curvos o borrosos; la interfaz permite cambiarlo sin tocar nada más |
| D-08 | Sin permiso INTERNET en release, verificado en cada build | Control de seguridad principal |
| D-09 | Clave de la base: 32 bytes aleatorios, envuelta con una clave AES-256-GCM del Android Keystore **no ligada a autenticación**; el bloqueo biométrico es una compuerta de UI | Ligar la clave a la biometría la invalida al cambiar huellas o el bloqueo de pantalla y se perdería la base. El riesgo residual (teléfono rooteado y desbloqueado) se acepta para una app de mercado |
| D-10 | Bloqueo: `BiometricPrompt` con `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`, gracia de 5 min | Sin contraseñas propias; no estorba dentro del supermercado |
| D-11 | Ajustes no sensibles en DataStore Preferences; todo lo demás en la base cifrada | Simplicidad |
| D-12 | Navegación: `androidx.navigation:navigation-compose` con rutas tipadas | Estándar |
| D-13 | JSON (semilla y respaldo): `kotlinx-serialization-json` | Sin reflexión, amigable con R8 |
| D-14 | Gráficas: barras dibujadas con `Canvas` de Compose, sin librería | Una dependencia menos |
| D-15 | Pruebas: `kotlin.test` + JUnit 5 en `:domain`; JUnit 4 + Robolectric + Turbine en `:app`; instrumentadas solo para cifrado y humo de cámara | El loop del agente debe poder verificarse sin teléfono |
| D-16 | Teclado numérico propio en Compose para precios | Más rápido que el IME; teclas `00` y `000` |
| D-17 | Código desconocido: precio primero, nombre después (RF-04) | Mantiene el ritmo en el pasillo |
| D-18 | Producto por peso: se pide el precio total de la etiqueta, no el peso | Un dato en vez de dos |
| D-19 | Una sola compra abierta; cada cambio se persiste al instante | RNF-09 |
| D-20 | `applicationId = co.daniel.mercado`, nombre visible "Mercado", solo retrato | — |
| D-21 | minSdk 26, compileSdk/targetSdk = el último estable que soporte el AGP elegido (≥ 36) | Poco X8 Pro usa Android 16 |
| D-22 | Un solo idioma (es-CO), tema claro y oscuro, sin color dinámico | Consistencia del semáforo |

## Versiones

Línea base conocida a noviembre de 2026. **En L0-T2** confirma la última versión estable de cada
una en su página oficial (o Maven Central / Google Maven), fíjalas en `gradle/libs.versions.toml` y
anota las elegidas en `plan/DECISIONES.md`. Nunca uses alfa, beta ni RC.

| Componente | Línea base | Nota |
| --- | --- | --- |
| Android Gradle Plugin | 9.x | Trae Kotlin integrado: **no** se aplica `org.jetbrains.kotlin.android` en `:app` |
| Gradle | la que exija el AGP (≥ 9.1) | Wrapper en el repo |
| JDK | 17 o superior | El que trae Android Studio sirve |
| Kotlin | la que acompaña al AGP (2.2.x/2.3.x) | `:domain` aplica `org.jetbrains.kotlin.jvm` con la misma versión |
| Compose | BOM estable más reciente (≥ 2026.09.00) + plugin `org.jetbrains.kotlin.plugin.compose` | |
| KSP | la que corresponda a la versión de Kotlin | Para Room |
| Room | 2.7+ estable | `room-runtime`, `room-ktx`, `room-compiler`, `room-testing` |
| SQLCipher | `net.zetetic:sqlcipher-android` ≥ 4.6.1 + `androidx.sqlite:sqlite-ktx` | ≥ 4.6.1 soporta páginas de 16 KB |
| CameraX | 1.4+ estable | |
| ML Kit | `com.google.mlkit:barcode-scanning` 17.3.0 o posterior | Bundled, no la variante `play-services-` |
| ML Kit texto | `com.google.mlkit:text-recognition` 16.0.1 | Bundled; solo para RF-18 (etiqueta del estante). Agregado en L7-T0 |
| Biometric | `androidx.biometric:biometric` estable | |
| WorkManager | `androidx.work:work-runtime-ktx` estable | |
| DataStore | `androidx.datastore:datastore-preferences` estable | |
| Navigation | `androidx.navigation:navigation-compose` estable | |
| Serialization | `kotlinx-serialization-json` estable | |
| Coroutines | `kotlinx-coroutines-core/android/test` estable | |
| Pruebas | JUnit 5, JUnit 4, Robolectric, Turbine, `androidx.test:core`, `compose-ui-test-junit4` | |

Si AGP 9 + KSP + Room presentan incompatibilidad real, la salida permitida es bajar al último
AGP 8.13.x con el plugin de Kotlin aplicado de forma clásica. Anótalo en DECISIONES.

## Estructura del repositorio

```
App_Mercado/
├── CLAUDE.md, README.md
├── docs/  plan/  seed/
├── scripts/
│   └── verificar.sh
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradle/libs.versions.toml
├── .gitignore
├── domain/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/co/daniel/mercado/domain/
│       │   ├── model/      Pesos, Product, Category, UnitType, Trip, TripItem, Budget,
│       │   │               ShoppingListItem, PricePoint, TrafficLight, ScanOutcome
│       │   ├── rules/      Ean, ScanDebouncer, BudgetRules, LineMath, NameNormalizer,
│       │   │               SuggestionRules, MoneyFormat
│       │   ├── port/       ProductRepository, TripRepository, BudgetRepository,
│       │   │               ShoppingListRepository, Clock
│       │   └── usecase/    (ver lista abajo)
│       └── test/kotlin/...
└── app/
    ├── build.gradle.kts, proguard-rules.pro
    ├── schemas/                      esquemas exportados de Room (se versionan)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/seed/catalogo_inicial.json
        │   ├── res/ (values/strings.xml, xml/data_extraction_rules.xml, ...)
        │   └── kotlin/co/daniel/mercado/
        │       ├── MercadoApp.kt, MainActivity.kt
        │       ├── di/         AppContainer
        │       ├── data/
        │       │   ├── db/     MercadoDatabase, entities/, dao/, Converters, migrations/
        │       │   ├── repo/   Room*Repository (implementan los puertos)
        │       │   ├── seed/   SeedImporter, SeedDto
        │       │   └── settings/ SettingsStore (DataStore)
        │       ├── scanner/    BarcodeDecoder, MlKitBarcodeDecoder, ScannerView, ScanFeedback
        │       ├── security/   DbKeyManager, AppLock, BackupCrypto, BackupManager
        │       ├── reminders/  ReminderWorker, Notifications
        │       └── ui/
        │           ├── theme/        Color, Type, Theme
        │           ├── components/   PriceKeypad, TotalCard, LineRow, UndoBar, ...
        │           ├── nav/          Routes, MercadoNavHost
        │           ├── home/  trip/  catalog/  history/  summary/  list/  settings/  lock/
        ├── test/                     Robolectric + JVM
        └── androidTest/              cifrado, humo de cámara
```

## Casos de uso del dominio

Cada uno es una clase con `operator fun invoke(...)` (suspend cuando toca repositorio).

| Caso de uso | Qué hace |
| --- | --- |
| `StartTrip` | Abre una compra (falla si ya hay una abierta); calcula el límite por defecto |
| `ObserveActiveTrip` | Flow con la compra abierta, sus líneas, total, límite y semáforo |
| `HandleScan` | Código → `ScanOutcome` (ver abajo). Aplica validación EAN y antirrebote |
| `AddProductToTrip` | Agrega/incrementa por id de producto (favoritos, búsqueda, lista) |
| `AddUnknownScanned` | RF-04: crea producto provisional o asocia el código a uno existente y agrega la línea |
| `AddLoosePrice` | RF-17: línea sin producto |
| `AddWeighedItem` | Línea de producto `KG` con el precio de la etiqueta |
| `ChangeQuantity`, `ChangeLinePrice`, `RemoveLine`, `RenameLooseLine` | Edición de líneas |
| `UndoLastAction` | Revierte la última acción de la compra (pila en memoria del caso de uso, tamaño 1) |
| `CloseTrip`, `DiscardTrip`, `SetPaidTotal` | RF-08 |
| `GetMonthStatus` | Presupuesto, gastado, saldo del mes |
| `SetMonthlyBudget`, `SetCategoryBudget` | RF-07, RF-13 |
| `SearchProducts`, `SaveProduct`, `DeactivateProduct`, `LinkBarcode`, `ObserveFavorites` | Catálogo |
| `BuildSuggestedList`, `ObserveShoppingList`, `ToggleListItem`, `AddListItem` | RF-09 |
| `GetMonthSummary`, `GetMonthsComparison`, `GetPriceChanges` | RF-10 |
| `ShouldRemindShopping` | RF-11: decide si toca notificar |

```kotlin
sealed interface ScanOutcome {
    data class Added(val lineId: Long, val productName: String, val unitPrice: Pesos) : ScanOutcome
    data class Incremented(val lineId: Long, val productName: String, val newQuantity: Int) : ScanOutcome
    data class NeedsLabelPrice(val productId: Long, val productName: String) : ScanOutcome  // producto KG
    data class Unknown(val code: String) : ScanOutcome
    data object Ignored : ScanOutcome            // rebote o checksum inválido
    data object NoActiveTrip : ScanOutcome
}
```

## Reglas puras (módulo `rules`)

- `Ean.isValid(code)`: acepta EAN-13, EAN-8, UPC-A (12), UPC-E (8) con dígito de control correcto;
  Code 128 se acepta si tiene entre 4 y 32 caracteres imprimibles. UPC-A se normaliza a EAN-13
  anteponiendo `0` para que un mismo producto no quede con dos códigos.
- `ScanDebouncer(windowMs = 1500, clock)`: `shouldAccept(code)` es falso si el mismo código se
  aceptó hace menos de la ventana. Un código distinto se acepta de inmediato.
- `BudgetRules.trafficLight(total, limit)`: `GREEN` si total < 80 % del límite, `YELLOW` si está
  entre 80 % y 100 % inclusive, `RED` si lo supera. Sin límite → `NONE`. Aritmética entera
  (`total * 100 < limit * 80`).
- `LineMath.lineTotal(unitPrice, quantity)`: multiplicación con detección de desbordamiento.
- `NameNormalizer.normalize(name)`: minúsculas, sin tildes, espacios colapsados. Base de la
  búsqueda y de la importación idempotente.
- `SuggestionRules`: intervalo medio y mediana de cantidad (RF-09).
- `MoneyFormat.format(pesos)`: `$5.900`, `$1.250.000`, `-$300`. Sin depender de `Locale` del
  sistema (formato fijo).

## El escáner

```kotlin
interface BarcodeDecoder {
    /** Analiza un cuadro y devuelve los códigos crudos encontrados. Debe cerrar el ImageProxy. */
    fun analyze(image: ImageProxy, onResult: (List<String>) -> Unit)
    fun close()
}
```

- `ImageAnalysis` con `STRATEGY_KEEP_ONLY_LATEST`, resolución objetivo 1280×720, cámara trasera,
  autoenfoque continuo.
- ML Kit configurado solo con `FORMAT_EAN_13 | FORMAT_EAN_8 | FORMAT_UPC_A | FORMAT_UPC_E |
  FORMAT_CODE_128`.
- Se acepta la primera lectura con checksum válido (sin exigir cuadros repetidos).
- El `ScannerView` (Composable con `PreviewView`) emite `String` hacia el ViewModel; el ViewModel
  llama a `HandleScan`. La cámara no sabe nada de productos.
- Ningún cuadro se escribe a disco. No se usa `ImageCapture`.
- Linterna con un botón. Botón de pausa de cámara. La cámara se libera al salir de la pantalla.
- En pruebas se inyecta un `FakeBarcodeSource` que emite códigos.
- Modo etiqueta (RF-18): `LabelReader` (`MlKitLabelReader`) lee texto y código del mismo cuadro y
  entrega `LabelRead(code, price)`; el precio sale de la regla pura `ShelfPrice` y se estabiliza con
  `LabelStabilizer` (dos lecturas iguales). Abre el teclado prellenado; nunca suma solo.
- R8: ML Kit busca sus `ComponentRegistrar` por reflexión; `proguard-rules.pro` conserva sus
  constructores (sin eso el escáner falla solo en release).

## Arranque y rendimiento

- `MainActivity` única. Si el bloqueo está activo y venció la gracia → pantalla de bloqueo; si
  no, directo a Inicio o a la compra abierta si existe una.
- Acceso directo estático del lanzador "Nueva compra" (`shortcuts.xml`) que abre la compra.
- La base se abre en un hilo de fondo al crear el `AppContainer`; la UI muestra el esqueleto.
- SQLCipher: cargar la librería nativa una vez en `MercadoApp.onCreate`.
- R8 con `isMinifyEnabled = true` e `isShrinkResources = true` en release.
- ABI: incluir solo `arm64-v8a` y `armeabi-v7a` en release (reduce tamaño).
- Tipos de build: `debug` (`.debug`, depurable, diagnóstico del escáner), `qa` (igual a release
  pero `.qa` y firmado con la llave de debug, para probar R8 en el teléfono) y `release`.
- Recordatorios: `ReminderWorker` diario (WorkManager) que delega en `ReminderCheck`. El
  `SystemJobService` de WorkManager va con `exported="false"` (S-11).

## Convenciones de código

- Kotlin idiomático, funciones cortas, sin comentarios obvios. KDoc solo en puertos y reglas.
- Sin `!!`. Sin `GlobalScope`. Corrutinas con `viewModelScope` y dispatchers inyectados.
- Los Composables de pantalla reciben `state` y `onEvent`; no reciben ViewModels (facilita pruebas
  y `@Preview`).
- Todo texto visible en `strings.xml`.
- Nombres de pruebas en español con comillas invertidas: `` `suma dos unidades del mismo producto` ``.
