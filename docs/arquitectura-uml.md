# Arquitectura de Mercado en diagramas

Los diagramas están en Mermaid; GitHub los dibuja directamente. La descripción en texto y las
decisiones cerradas (D-01 a D-22) están en [`02-arquitectura.md`](02-arquitectura.md).

## Componentes y capas

La app tiene dos módulos de Gradle. `:domain` es Kotlin puro y no conoce Android; `:app` tiene la
interfaz, los datos cifrados, la cámara y la seguridad. Las dependencias apuntan siempre hacia el
dominio.

```mermaid
flowchart TB
    subgraph app[":app (Android)"]
        direction TB
        subgraph ui["ui · Compose + ViewModels"]
            nav["MercadoNavHost<br/>rutas tipadas"]
            screens["Inicio · Compra · Cierre · Catálogo<br/>Historial · Resumen · Lista · Ajustes · Bloqueo"]
            vms["XxxViewModel<br/>StateFlow&lt;XxxUiState&gt; + onEvent"]
        end
        subgraph scanner["scanner"]
            sv["ScannerView<br/>CameraX Preview + ImageAnalysis"]
            dec["BarcodeDecoder<br/>MlKitBarcodeDecoder"]
            lab["LabelReader<br/>MlKitLabelReader"]
        end
        subgraph data["data"]
            room["MercadoDatabase<br/>Room + SQLCipher"]
            repos["Room*Repository"]
            seed["SeedImporter"]
            ds["SettingsStore<br/>DataStore"]
        end
        subgraph security["security"]
            key["DbKeyManager + KeystoreKeyWrapper<br/>AES-256-GCM, alias mercado_db_kek"]
            lock["AppLock + BiometricPrompt"]
            backup["BackupManager + BackupCrypto<br/>formato MRC1"]
        end
        rem["reminders<br/>ReminderWorker (WorkManager)"]
        di["di · AppContainer / DataGraph"]
    end

    subgraph domain[":domain (Kotlin/JVM puro)"]
        direction TB
        uc["usecase<br/>HandleScan, AddProductToTrip, CloseTrip,<br/>GetMonthSummary, BuildSuggestedList..."]
        rules["rules<br/>Ean, ScanDebouncer, BudgetRules, LineMath,<br/>ShelfPrice, SuggestionRules, MoneyFormat"]
        model["model<br/>Pesos, Product, Trip, TripItem, Budget..."]
        port["port<br/>ProductRepository, TripRepository,<br/>BudgetRepository, ShoppingListRepository, Clock"]
    end

    screens --> vms
    nav --> screens
    vms --> uc
    sv --> dec
    sv --> lab
    dec -. códigos .-> vms
    lab -. etiqueta .-> vms
    repos -. implementa .-> port
    repos --> room
    room --> key
    di --> repos
    di --> uc
    rem --> uc
    backup --> room
    uc --> rules
    uc --> port
    uc --> model
    rules --> model
```

## Modelo del dominio

Los montos son siempre `Pesos` (un `Long` de pesos enteros). Una línea de compra copia el nombre y
el precio del momento, así que el historial no cambia si después se edita el catálogo.

```mermaid
classDiagram
    direction LR
    class Pesos {
        +Long value
        +plus(Pesos) Pesos
        +minus(Pesos) Pesos
        +times(Int) Pesos
    }
    class Category {
        +Long id
        +String name
        +Pesos? monthlyBudget
    }
    class Product {
        +Long id
        +String name
        +UnitType unitType
        +Pesos refPrice
        +Boolean favorite
        +Boolean active
        +Boolean needsReview
    }
    class Barcode {
        +String code
    }
    class PricePoint {
        +Pesos price
        +Long at
        +PriceSource source
    }
    class Trip {
        +Long id
        +Long startedAt
        +Long? closedAt
        +String? store
        +Pesos? budgetLimit
        +Pesos totalEstimated
        +Pesos? totalPaid
        +effectiveTotal() Pesos
    }
    class TripItem {
        +Long id
        +String nameSnapshot
        +Int quantity
        +Pesos unitPrice
        +lineTotal() Pesos
    }
    class Budget {
        +YearMonth yearMonth
        +Pesos amount
    }
    class ShoppingListItem {
        +Int suggestedQty
        +Boolean checked
        +ListSource source
        +Long? dismissedUntil
    }
    class UnitType {
        <<enumeration>>
        UNIT
        KG
    }

    Category "1" --> "*" Product
    Product "1" --> "*" Barcode
    Product "1" --> "*" PricePoint
    Trip "1" *-- "*" TripItem
    TripItem "*" --> "0..1" Product : precio suelto si no hay
    Product "1" --> "0..1" ShoppingListItem
    Product --> UnitType
```

## Flujo de un escaneo

El camino caliente: un código entra en cuadro y queda sumado sin tocar la pantalla. El
antirrebote evita contar dos veces el mismo código mientras sigue en el visor.

```mermaid
sequenceDiagram
    autonumber
    participant Cam as CameraX (ImageAnalysis)
    participant Dec as MlKitBarcodeDecoder
    participant VM as TripViewModel
    participant UC as HandleScan
    participant Rep as Room (SQLCipher)
    participant UI as TripScreen

    Cam->>Dec: cuadro (ImageProxy)
    Dec-->>VM: código crudo con dígito de control válido
    VM->>UC: handleScan(código)
    UC->>UC: Ean.normalize + ScanDebouncer
    UC->>Rep: findByBarcode(código)
    alt producto conocido
        UC->>Rep: insertar o incrementar línea (transacción)
        UC-->>VM: Added / Incremented
        VM-->>UI: vibración, pitido, línea resaltada, aviso con Deshacer
    else código desconocido
        UC-->>VM: Unknown
        VM-->>UI: hoja "Producto nuevo" (precio primero)
    else producto por peso
        UC-->>VM: NeedsLabelPrice
        VM-->>UI: teclado "Precio de la etiqueta"
    end
    Rep-->>UI: Flow de la compra abierta (total y semáforo)
```

## Bloqueo de la app

El bloqueo es una compuerta de interfaz (D-09): la base no depende de la huella. Mientras el
estado no es `UNLOCKED`, la actividad no compone ninguna pantalla con datos.

```mermaid
stateDiagram-v2
    [*] --> CHECKING
    CHECKING --> LOCKED: bloqueo activo y teléfono con PIN o huella
    CHECKING --> UNLOCKED: bloqueo apagado o teléfono sin bloqueo
    LOCKED --> UNLOCKED: BiometricPrompt correcto
    UNLOCKED --> UNLOCKED: vuelve antes de la gracia (1, 5 o 15 min)
    UNLOCKED --> LOCKED: vuelve después de la gracia
```

## Respaldo cifrado

```mermaid
flowchart LR
    tablas["8 tablas<br/>(transacción de lectura)"] --> json["JSON<br/>schemaVersion + exportedAt"]
    json --> gz["GZIP"]
    gz --> enc["AES-256-GCM<br/>clave PBKDF2-SHA256, 600.000 iteraciones"]
    enc --> file["mercado-AAAA-MM-DD.mercado<br/>MRC1 · iteraciones · sal · IV · datos"]
    file -. restaurar .-> val["validar cabecera, autenticar,<br/>descomprimir con tope, validar todo"]
    val -. confirmación .-> tx["reemplazo en una transacción"]
```
