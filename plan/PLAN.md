# Plan de trabajo por loops

Diez loops (L0–L9). Cada loop tiene objetivo, tareas en orden, y una **puerta de salida** que debe
cumplirse completa antes de pasar al siguiente. El protocolo de cada tarea está en `CLAUDE.md`.

Los pasos `[HUMANO]` los hace Daniel; el agente prepara todo, da instrucciones exactas y espera.
Si un paso `[HUMANO]` no bloquea técnicamente el loop siguiente, el agente puede continuar y lo
deja pendiente en `PROGRESO.md` (cada loop indica si su paso humano es bloqueante).

```
L0 Base del proyecto ─▶ L1 Dominio ─▶ L2 Datos cifrados ─▶ L3 Escáner ─▶ L4 Compra en curso
                                                                              │
L9 Prueba de campo ◀─ L8 Release ◀─ L7 Lista, resúmenes ◀─ L6 Seguridad ◀─ L5 Resto de pantallas
```

El MVP usable termina en L6. L4 es el loop más importante: si hay que elegir dónde gastar
esfuerzo y pulido, es ahí.

---

## L0 · Base del proyecto

**Objetivo:** repositorio que compila, con dos módulos, verificación automática y endurecimiento
del manifiesto desde el primer commit.

Lee: `docs/02-arquitectura.md` completo, `docs/05-seguridad.md` (S-01, S-04, S-14),
`docs/06-pruebas.md` (sección `verificar.sh`), `docs/07-release.md` (`.gitignore`, firma).

- **T1 · Entorno.** Comprueba `java -version` (≥ 17), `ANDROID_HOME`/`ANDROID_SDK_ROOT` o
  `local.properties`, `adb version`, `git --version`. Si falta el SDK de Android o el JDK,
  regístralo como bloqueo con las instrucciones exactas de instalación para Daniel y detente.
- **T2 · Versiones.** Confirma las últimas versiones estables (tabla de `02-arquitectura.md`),
  crea `gradle/libs.versions.toml` y anota las elegidas en `plan/DECISIONES.md`.
- **T3 · Esqueleto Gradle.** `git init`, `.gitignore`, wrapper de Gradle, `settings.gradle.kts`
  con `:domain` y `:app`, `:domain` como `kotlin("jvm")` con JUnit 5, `:app` con Compose, KSP,
  serialization, minSdk 26, `applicationId co.daniel.mercado`, sufijo `.debug` en debug, release
  con R8 y firma opcional desde `keystore.properties`.
- **T4 · Manifiesto endurecido.** Permisos de red eliminados con `tools:node="remove"`,
  `allowBackup=false`, `dataExtractionRules`, `usesCleartextTraffic=false`, solo retrato.
- **T5 · App mínima.** `MercadoApp`, `MainActivity`, tema (colores de `04-ux.md`), una pantalla
  "Mercado" vacía, `AppContainer` vacío, `AppLog`.
- **T6 · `scripts/verificar.sh`** con todos los chequeos de `06-pruebas.md`. Una prueba trivial
  en cada módulo para que las tareas de prueba existan.
- **T7 · Primer commit** y `CHANGELOG.md` inicial.

**Puerta de salida**
- `./scripts/verificar.sh completo` termina en verde.
- El APK debug se genera.
- `[HUMANO, no bloqueante]` Conectar el teléfono, `adb install -r ...app-debug.apk`, confirmar
  que abre y muestra "Mercado".

---

## L1 · Dominio

**Objetivo:** toda la lógica de negocio del MVP funcionando y probada en JVM, sin Android.

Lee: `docs/02-arquitectura.md` (casos de uso, reglas), `docs/01-producto.md` (RF-01 a RF-08,
RF-17), `docs/06-pruebas.md` (casos de dominio).

- **T1 · Modelo.** `Pesos`, `UnitType`, `Category`, `Product`, `Trip`, `TripItem`, `Budget`,
  `TrafficLight`, `ScanOutcome`, `YearMonth` propio o `java.time.YearMonth`.
- **T2 · Reglas.** `MoneyFormat`, `Ean`, `ScanDebouncer`, `BudgetRules`, `LineMath`,
  `NameNormalizer`, cada una con sus pruebas.
- **T3 · Puertos.** `ProductRepository`, `TripRepository`, `BudgetRepository`, `Clock`. Diseña
  los métodos a partir de lo que necesitan los casos de uso; usa `Flow` para lo observable.
- **T4 · Repositorios falsos en memoria** en `domain/src/test` (reutilizables: se moverán a un
  `testFixtures` o se duplicarán en `:app` cuando hagan falta).
- **T5 · Casos de uso de compra.** `StartTrip`, `ObserveActiveTrip`, `HandleScan`,
  `AddProductToTrip`, `AddUnknownScanned`, `AddLoosePrice`, `AddWeighedItem`, `ChangeQuantity`,
  `ChangeLinePrice`, `RemoveLine`, `RenameLooseLine`, `UndoLastAction`, `CloseTrip`,
  `DiscardTrip`, `SetPaidTotal`.
- **T6 · Casos de uso de presupuesto y catálogo.** `GetMonthStatus`, `SetMonthlyBudget`,
  `SearchProducts`, `SaveProduct`, `DeactivateProduct`, `LinkBarcode`, `ObserveFavorites`.

**Puerta de salida**
- Todos los casos obligatorios de dominio de `06-pruebas.md` (menos `SuggestionRules`) existen y
  pasan.
- `verificar.sh completo` en verde (incluye: sin `android.*` ni `Float/Double` en el dominio).

---

## L2 · Datos cifrados

**Objetivo:** persistencia real con Room + SQLCipher, clave en Keystore, semilla importable.

Lee: `docs/03-datos.md` completo, `docs/05-seguridad.md` (S-02, S-03, S-09, S-13).

- **T1 · Entidades, convertidores y DAOs** según `03-datos.md`. `exportSchema` a `app/schemas/`.
  Callback de creación que siembra las categorías.
- **T2 · `DatabaseFactory`**: implementación real con SQLCipher y otra en memoria para pruebas.
- **T3 · `DbKeyManager`** (S-03): genera, envuelve con Keystore, guarda en `noBackupFilesDir`.
- **T4 · Repositorios Room** que implementan los puertos del dominio, con mapeadores
  entidad ↔ modelo. Operaciones transaccionales de `03-datos.md`.
- **T5 · `SeedImporter`** + DTOs + validación. Importación automática en el primer arranque.
- **T6 · Semilla real.** Si existe `seed/recibo.*` (imagen o PDF): transcríbelo a
  `seed/catalogo_inicial.json` siguiendo `seed/README.md`, cópialo a
  `app/src/main/assets/seed/`, y deja en `PROGRESO.md` la lista de líneas dudosas para que Daniel
  las revise. Si no existe, usa `seed/catalogo_inicial.ejemplo.json` y deja el paso pendiente.
- **T7 · `SettingsStore`** (DataStore) con las claves de `03-datos.md`.
- **T8 · Cableado en `AppContainer`** y pruebas: DAOs, repositorios, importador (Robolectric),
  más las instrumentadas `EncryptedDbTest` y `DbKeyManagerTest`.

**Puerta de salida**
- Casos de app (JVM) de DAO, invariante de compra abierta y `SeedImporter` pasan.
- `verificar.sh completo` en verde.
- `[HUMANO, no bloqueante]` Con el teléfono conectado: `./gradlew :app:connectedDebugAndroidTest`
  (el agente lo lanza si `adb devices` muestra un dispositivo).
- `[HUMANO, no bloqueante]` Revisar `seed/catalogo_inicial.json`: nombres, precios, categorías.

---

## L3 · Escáner

**Objetivo:** leer códigos en tiempo real, sin red, y decidir definitivamente el decodificador.

Lee: `docs/02-arquitectura.md` (El escáner, D-06, D-07), `docs/05-seguridad.md` (ML Kit sin red,
S-07).

- **T1 · `BarcodeDecoder` + `MlKitBarcodeDecoder`** con los formatos limitados.
- **T2 · `ScannerView`**: `PreviewView` en Compose, `ImageAnalysis`, ciclo de vida, linterna,
  pausa, permiso de cámara con estado denegado usable.
- **T3 · `ScanFeedback`**: vibración y pitido (respetando ajustes).
- **T4 · Pantalla de diagnóstico** (solo debug, accesible desde Inicio): visor + lista de los
  últimos códigos leídos con el tiempo entre que la cámara arrancó y la primera lectura, y un
  contador de lecturas por minuto. Sirve para la prueba humana y se conserva en debug.
- **T5 · `FakeBarcodeSource`** para pruebas y la prueba instrumentada de humo.
- **T6 · Verificación sin red:** confirma en el manifiesto fusionado que no hay permisos de red
  con ML Kit incluido.

**Puerta de salida**
- `verificar.sh completo` en verde.
- `[HUMANO, BLOQUEANTE]` En el teléfono, en **modo avión**, pantalla de diagnóstico: leer 10
  productos de la casa. Reportar: ¿se cayó?, ¿cuántos leyó al primer intento?, ¿se siente
  instantáneo? El agente da estas instrucciones y espera el resultado.
  - Si se cae o no lee sin red → implementar `ZxingCppBarcodeDecoder`, repetir la prueba y anotar
    el cambio en DECISIONES.
  - Si lee menos de 8 de 10 → ajustar resolución/enfoque y repetir; si persiste, probar zxing-cpp
    y quedarse con el mejor.

---

## L4 · Compra en curso (el loop que define el producto)

**Objetivo:** hacer una compra completa, de principio a fin, más rápido que con la calculadora.

Lee: `docs/04-ux.md` (Compra en curso, hojas, Cierre), `docs/01-producto.md` (RF-03 a RF-08,
RF-17, principios).

- **T1 · Componentes.** `PriceKeypad`, `TotalCard` (con semáforo), `LineRow`, `UndoBar`. Cada
  uno con `@Preview` y prueba de UI donde aplique.
- **T2 · `TripViewModel`** con `TripUiState`/`TripEvent`; conecta escáner → `HandleScan` →
  estado; avisos; deshacer; cruces de semáforo.
- **T3 · Pantalla Compra en curso** según el esquema de `04-ux.md`; pantalla encendida; pausa
  automática de cámara.
- **T4 · Hoja Teclado** (precio suelto, corregir precio, precio de etiqueta KG).
- **T5 · Hoja Producto nuevo** (precio primero; asociar a existente).
- **T6 · Hoja Favoritos / Buscar** (la pestaña Lista llega en L7).
- **T7 · Hoja Editar línea** y deslizar para eliminar.
- **T8 · Cierre de compra**: resumen, total pagado, tienda, productos por nombrar, descartar.
- **T9 · Inicio provisional**: botón "Ir a mercar" / "Continuar compra" con la hoja de límite, lo
  mínimo para entrar y salir del flujo.
- **T10 · Robustez (RNF-09):** prueba que mata y recrea el ViewModel/actividad en medio de una
  compra y verifica que no se pierde nada.
- **T11 · Pasada de pulido de velocidad:** revisa cada interacción contra "la vara única": cuenta
  toques, elimina animaciones que retrasen, confirma que nada pide confirmación en el camino
  feliz. Anota en PROGRESO el conteo de toques de cada flujo.

**Puerta de salida**
- Casos de `TripViewModel`, `PriceKeypad` y UI de `06-pruebas.md` pasan.
- `verificar.sh completo` en verde.
- Conteo de toques documentado: conocido = 0; precio suelto = 2 + dígitos; desconocido = 1 +
  dígitos; favorito = 2.
- `[HUMANO, BLOQUEANTE]` Simulacro en casa con 10–15 productos: iniciar compra, escanear,
  un producto nuevo, uno por peso, un precio suelto, un deshacer, corregir un precio, cerrar.
  Daniel reporta qué se sintió lento o confuso; el agente corrige antes de seguir.

---

## L5 · Resto de pantallas del MVP

**Objetivo:** app navegable completa: inicio real, catálogo, historial básico, ajustes.

Lee: `docs/04-ux.md` (Inicio, Catálogo, Historial, Ajustes, Estados vacíos, Tema).

- **T1 · Navegación** (`MercadoNavHost`, barra inferior, rutas tipadas, acceso directo "Nueva
  compra" del lanzador).
- **T2 · Inicio** con estado del mes, tarjetas y primer uso.
- **T3 · Presupuesto mensual** (definir/editar con `PriceKeypad`), herencia entre meses.
- **T4 · Catálogo**: lista, búsqueda, filtros, editar/crear producto, asociar código escaneando,
  favoritos y su orden, historial de precios, desactivar.
- **T5 · Historial**: lista de compras, detalle, editar total pagado.
- **T6 · Ajustes**: todas las secciones; las de seguridad y datos quedan visibles pero se
  conectan en L6; importar catálogo JSON funcional.
- **T7 · Estados vacíos, errores, tema oscuro, accesibilidad** (RNF-03, RNF-11).

**Puerta de salida**
- `verificar.sh completo` en verde; pruebas de ViewModel para Inicio, Catálogo e Historial.
- Todos los textos en `strings.xml` (lint sin `HardcodedText`).
- `[HUMANO, no bloqueante]` Recorrer todas las pantallas en el teléfono, claro y oscuro.

---

## L6 · Seguridad de la app y respaldo  ← fin del MVP

**Objetivo:** cerrar todos los controles de `05-seguridad.md`.

Lee: `docs/05-seguridad.md` completo, `docs/03-datos.md` (Respaldo cifrado).

- **T1 · `AppLock`** (lógica de gracia con `Clock`) + pantalla de Bloqueo + `BiometricPrompt` +
  manejo de "sin bloqueo de pantalla configurado".
- **T2 · `FLAG_SECURE`** según ajuste y variante.
- **T3 · `BackupCrypto`** (formato `MRC1`) con sus pruebas.
- **T4 · `BackupManager`**: exportar e importar con el selector de documentos; validación;
  restauración transaccional; confirmación antes de reemplazar.
- **T5 · Conectar Ajustes** de seguridad y datos.
- **T6 · Auditoría:** recorre S-01 a S-15 uno por uno; para cada uno escribe en PROGRESO la
  evidencia (prueba, salida de comando o archivo). Completa lo que falte.

**Puerta de salida**
- Pruebas de `AppLock`, `BackupCrypto`, `BackupManager` pasan; `verificar.sh completo` en verde.
- Tabla de evidencia S-01..S-15 completa en PROGRESO.
- `[HUMANO, BLOQUEANTE]` En el teléfono: bloqueo al abrir; gracia; recientes en blanco; exportar
  respaldo → borrar datos de la app → restaurar → todo volvió.

---

## L7 · Lista sugerida, resúmenes y recordatorios

**Objetivo:** las funciones de segunda iteración (RF-09, RF-10, RF-11, RF-13).

Lee: `docs/01-producto.md` (RF-09 a RF-13), `docs/04-ux.md` (Lista, Historial y resumen).

- **T0 · Etiqueta del estante** (pedido de Daniel, 2026-11-16): modo "Código | Etiqueta" en el
  visor de la compra. En modo etiqueta la cámara lee texto (ML Kit text recognition bundled, sin
  red) y código en el mismo cuadro; el precio grande de la etiqueta prellena el teclado (producto
  conocido → "Precio de la etiqueta" con su nombre; código desconocido → "Producto nuevo"; sin
  código → precio suelto). Nunca suma solo: confirma con "Sumar". `[HUMANO, no bloqueante]`
  probarlo en el supermercado.
- **T1 · Dominio:** `SuggestionRules`, `BuildSuggestedList`, `ObserveShoppingList`,
  `ToggleListItem`, `AddListItem`, `GetMonthSummary`, `GetMonthsComparison`, `GetPriceChanges`,
  `SetCategoryBudget`, `ShouldRemindShopping`, con pruebas.
- **T2 · Datos:** tabla `shopping_list_item` (si no se creó en v1, migración + prueba),
  consultas de resumen.
- **T3 · Lista de mercado** (pantalla) y pestaña Lista en la compra con marcado automático.
- **T4 · Resumen del mes** con barras en `Canvas`, topes por categoría, variaciones de precio,
  comparación de 6 meses.
- **T5 · Recordatorios:** `ReminderWorker` diario, canal de notificación, permiso
  `POST_NOTIFICATIONS` pedido en contexto (al activar recordatorios), recordatorio mensual de
  respaldo.

**Puerta de salida**
- Casos de `SuggestionRules` y los nuevos casos de uso pasan; `verificar.sh completo` en verde.
- Con datos de prueba sembrados (solo en debug: botón "Cargar 4 meses de ejemplo" en la pantalla
  de diagnóstico) la lista sugiere y el resumen muestra cifras coherentes.

---

## L8 · Endurecimiento y release

**Objetivo:** APK release firmado, medido y documentado.

Lee: `docs/07-release.md`, `docs/05-seguridad.md` (lista de verificación de release).

- **T1 · R8:** reglas para Room, SQLCipher, ML Kit/zxing, serialization; probar la variante
  release de punta a punta (instalarla si hay teléfono).
- **T2 · Rendimiento:** medir arranque en frío; diferir todo lo que no sea necesario para mostrar
  la cámara; revisar recomposiciones de la pantalla de compra.
- **T3 · Tamaño:** filtros de ABI, recursos; APK ≤ 40 MB.
- **T4 · Lint a cero** advertencias relevantes; limpieza de código muerto.
- **T5 · Documentación:** `README.md` del proyecto (cómo compilar, instalar, respaldar),
  `CHANGELOG.md` v1.0.0, `docs/` actualizados si algo cambió respecto a lo especificado.
- **T6 · Build release** y `apksigner verify`.

**Puerta de salida**
- `verificar.sh completo` en verde sobre release.
- `[HUMANO, BLOQUEANTE]` Crear la llave de firma y `keystore.properties` (instrucciones en
  `07-release.md`). Luego el agente genera el APK firmado.
- `[HUMANO, BLOQUEANTE]` Lista de verificación de release de `05-seguridad.md`, ítem por ítem.
- Etiqueta `v1.0.0`.

---

## L9 · Prueba de campo y ajuste

**Objetivo:** validar la vara única en un supermercado real y corregir lo que falle.

- **T1 ·** Prepara `plan/PRUEBA_DE_CAMPO.md`: la tabla de métricas de `06-pruebas.md` lista para
  llenar y un guion de 10 minutos.
- **T2 · `[HUMANO, BLOQUEANTE]`** Daniel hace un mercado real con la app y llena el archivo.
- **T3 ·** Convierte cada problema reportado en una tarea, priorízalas por impacto en velocidad,
  corrígelas con el protocolo normal, y publica `v1.0.1`.
- **T4 ·** Repite T2–T3 hasta cumplir todas las metas de la prueba de campo.

**Puerta de salida:** todas las metas de la prueba de campo cumplidas en una compra real.

---

## Después de v1

Pendientes que requieren insumos de Daniel antes de planearse: RF-14 (etiquetas de balanza: fotos
de 5 etiquetas reales), RF-15 (OCR de recibos), RF-16 (compartir lista). Cada uno se abre como un
loop nuevo en este archivo.
