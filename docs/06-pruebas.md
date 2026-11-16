# 06 · Pruebas

El agente debe poder verificar casi todo sin teléfono. Pirámide:

| Nivel | Dónde corre | Qué cubre | Comando |
| --- | --- | --- | --- |
| Dominio | JVM | Reglas y casos de uso con repositorios falsos en memoria | `./gradlew :domain:test` |
| App (JVM) | Robolectric | DAOs con Room en memoria (sin cifrar), repositorios, importador de semilla, respaldo, ViewModels, pantallas clave con Compose UI Test | `./gradlew :app:testDebugUnitTest` |
| Instrumentadas | Teléfono | Cifrado real (SQLCipher + Keystore), humo de cámara | `./gradlew :app:connectedDebugAndroidTest` |
| Manuales | Teléfono | Lista `[HUMANO]` de cada loop y prueba de campo | — |

Robolectric no carga la librería nativa de SQLCipher: por eso la fábrica de la base se inyecta
(`DatabaseFactory`) y en JVM se usa Room en memoria sin cifrar. El cifrado se prueba en teléfono.

## Casos obligatorios de dominio

Los nombres son orientativos; el comportamiento es obligatorio.

**Pesos y formato**
- formatea `5900` como `$5.900`, `1250000` como `$1.250.000`, `0` como `$0`, negativos con signo.
- sumar y multiplicar detectan desbordamiento.

**Ean**
- acepta EAN-13 válido (`7702004003508`) y rechaza uno con un dígito cambiado.
- acepta EAN-8, UPC-E; normaliza UPC-A de 12 dígitos a EAN-13 con `0` inicial.
- rechaza vacío, letras en EAN, longitudes raras.

**ScanDebouncer**
- mismo código a los 500 ms → rechazado; a los 1.600 ms → aceptado.
- código distinto inmediatamente → aceptado.

**BudgetRules**
- 79 % → verde; 80 % exacto → amarillo; 100 % exacto → amarillo; 100 % + $1 → rojo; sin límite → NONE.
- límite por defecto = presupuesto − gastado; nunca negativo (mínimo 0).

**HandleScan**
- código conocido UNIT → `Added` con precio de referencia.
- mismo producto otra vez (pasada la ventana) → `Incremented`, una sola línea con cantidad 2.
- dos códigos distintos del mismo producto → incrementan la misma línea.
- producto KG → `NeedsLabelPrice` y no agrega línea.
- código desconocido → `Unknown` y no agrega línea.
- checksum inválido o rebote → `Ignored`.
- sin compra abierta → `NoActiveTrip`.

**Edición y deshacer**
- `ChangeQuantity` a 0 elimina la línea.
- `UndoLastAction` revierte: agregar, incrementar, eliminar (restaura posición y cantidad), cambio
  de precio. Deshacer dos veces seguidas no hace nada la segunda.

**AddUnknownScanned**
- solo precio → producto provisional `needsReview`, código asociado, línea agregada.
- asociando a existente → no crea producto, asocia código, agrega línea con el precio tecleado.
- el mismo código escaneado después → `Added` directo.

**CloseTrip**
- fija total y fecha; actualiza `refPrice` donde cambió; inserta historial solo si cambió.
- con `totalPaid`, el gastado del mes usa el pagado; sin él, el estimado.
- compra vacía no se puede cerrar (se descarta).

**StartTrip**
- falla si ya hay una abierta; el límite por defecto es lo que queda del mes.

**Mes**
- una compra cerrada el 30-nov 23:30 hora de Bogotá cuenta en noviembre (no en diciembre por UTC).
- un mes sin presupuesto hereda el del mes anterior más reciente.

**SuggestionRules** (L7)
- producto con una sola compra no se sugiere.
- compras cada 28 días, última hace 24 → sugerido; hace 20 → no.
- cantidad sugerida = mediana.

## Casos obligatorios de app (JVM)

- DAO: buscar por código devuelve el producto; búsqueda sin tildes; total de compra; gastado del
  mes; cascada al borrar compra.
- Invariante de una sola compra abierta.
- `SeedImporter`: importa el ejemplo; reimportar no duplica; archivo inválido no deja nada.
- `BackupCrypto`: ida y vuelta; contraseña incorrecta; byte alterado; cabecera alterada;
  iteraciones fuera de rango.
- `BackupManager`: exportar → vaciar → importar deja la base idéntica (comparar conteos y sumas).
- `AppLock`: bloquea al inicio; no bloquea dentro de la gracia; bloquea pasada la gracia;
  desactivado nunca bloquea.
- `TripViewModel` con `FakeBarcodeSource`: escanear conocido actualiza total y aviso; desconocido
  abre la hoja; deshacer restaura el total.
- UI (Compose test): flujo teclado → sumar → aparece la línea y el total; `−` hasta 0 elimina;
  semáforo cambia de color al cruzar umbrales.
- `PriceKeypad`: `1`,`2`,`000` → `$12.000`; borrar; tope de 9 dígitos.
- Migraciones: desde que exista una v2, `MigrationTestHelper` para cada salto.

## Instrumentadas (teléfono conectado)

- `EncryptedDbTest`: crea la base, inserta, cierra; el archivo no abre como SQLite plano; con la
  clave sí.
- `DbKeyManagerTest`: genera, guarda, recupera la misma clave tras "reinicio" del objeto.
- `ScannerSmokeTest`: la pantalla de compra abre con permiso concedido y sin caerse.

Si no hay teléfono conectado, el agente lo deja anotado como paso `[HUMANO]` pendiente y continúa.

## Prueba de campo (L9, la que decide)

Con 20 productos reales con código, ya registrados:

| Métrica | Meta |
| --- | --- |
| Tiempo total para sumar los 20 | ≤ 60 s |
| Leídos al primer intento | ≥ 18 de 20 |
| Lecturas duplicadas o equivocadas | 0 |
| Misma tarea con la calculadora (referencia) | Se registra para comparar |
| Arranque en frío hasta cámara activa | ≤ 2 s |

Además: un producto nuevo (código desconocido), uno por peso, uno con precio suelto, un deshacer,
y cruzar el 80 % y el 100 % del límite. Resultados a `plan/PRUEBA_DE_CAMPO.md`.

## `scripts/verificar.sh`

Debe existir desde L0 y hacer:

- `rapido`: `./gradlew :domain:test :app:compileDebugKotlin`
- `completo`: `./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
  :app:processReleaseMainManifest` (o la tarea equivalente que genere el manifiesto fusionado de
  release), y luego:
  - falla si el manifiesto fusionado de release contiene un permiso fuera de la lista blanca de
    `05-seguridad.md`;
  - falla si `allowBackup` no es `false`;
  - falla si hay `Log.` o `println(` en `app/src/main` o `domain/src/main` fuera de `AppLog.kt`;
  - falla si hay `import android` o `import androidx` en `domain/src/main`;
  - falla si aparece `Float` o `Double` en `domain/src/main/.../model` o `rules`;
  - falla si aparece `fallbackToDestructiveMigration`.
- Sale con código distinto de cero ante cualquier fallo e imprime un resumen final de una línea
  por chequeo.
