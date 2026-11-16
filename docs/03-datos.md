# 03 · Datos

Una base SQLite cifrada con SQLCipher, archivo `mercado.db`, administrada con Room.
`exportSchema = true` hacia `app/schemas/` (se versiona en git). Versión inicial del esquema: 1.

## Reglas generales

- Dinero: columnas `INTEGER` (pesos enteros), mapeadas a `Pesos` con un `TypeConverter`.
- Fechas: `INTEGER` epoch en milisegundos (UTC). El mes de una compra se calcula en la zona
  `America/Bogota`.
- Claves foráneas activas (`PRAGMA foreign_keys = ON`).
- Las líneas de compra copian nombre y precio en el momento (no dependen del catálogo después).
- Nada se borra en cascada desde `product`: un producto con historial se desactiva.

## Tablas

### `category`
| Columna | Tipo | Notas |
| --- | --- | --- |
| id | INTEGER PK autoincrement | |
| name | TEXT NOT NULL UNIQUE | |
| sort_order | INTEGER NOT NULL DEFAULT 0 | |
| monthly_budget | INTEGER NULL | RF-13 |

Categorías sembradas en la creación de la base: Granos y despensa, Lácteos y huevos, Carnes,
Frutas y verduras, Panadería, Bebidas, Aseo del hogar, Cuidado personal, Mascotas, Otros.

### `product`
| Columna | Tipo | Notas |
| --- | --- | --- |
| id | INTEGER PK autoincrement | |
| name | TEXT NOT NULL | Como se muestra |
| name_norm | TEXT NOT NULL | `NameNormalizer`; índice |
| category_id | INTEGER NOT NULL FK → category(id) | |
| unit_type | TEXT NOT NULL | `UNIT` \| `KG` |
| ref_price | INTEGER NOT NULL | Último precio usado. Para `KG`: último precio de etiqueta |
| favorite | INTEGER NOT NULL DEFAULT 0 | |
| favorite_order | INTEGER NOT NULL DEFAULT 0 | |
| active | INTEGER NOT NULL DEFAULT 1 | |
| needs_review | INTEGER NOT NULL DEFAULT 0 | Creado con nombre provisional (RF-04) |
| created_at | INTEGER NOT NULL | |
| updated_at | INTEGER NOT NULL | |

Índices: `name_norm`, `(favorite, favorite_order)`.

### `barcode`
| Columna | Tipo | Notas |
| --- | --- | --- |
| code | TEXT PK | Normalizado (UPC-A → EAN-13) |
| product_id | INTEGER NOT NULL FK → product(id) | Índice |
| created_at | INTEGER NOT NULL | |

### `trip`
| Columna | Tipo | Notas |
| --- | --- | --- |
| id | INTEGER PK autoincrement | |
| started_at | INTEGER NOT NULL | |
| closed_at | INTEGER NULL | NULL = abierta |
| store | TEXT NULL | |
| budget_limit | INTEGER NULL | Límite de esta compra |
| total_estimated | INTEGER NOT NULL DEFAULT 0 | Se fija al cerrar; abierta se calcula sumando líneas |
| total_paid | INTEGER NULL | Total real del recibo |

Invariante: como máximo una fila con `closed_at IS NULL`. Se garantiza en `StartTrip` dentro de
una transacción y con una prueba.

### `trip_item`
| Columna | Tipo | Notas |
| --- | --- | --- |
| id | INTEGER PK autoincrement | |
| trip_id | INTEGER NOT NULL FK → trip(id) ON DELETE CASCADE | Índice |
| product_id | INTEGER NULL FK → product(id) | NULL = precio suelto (RF-17) |
| name_snapshot | TEXT NOT NULL | |
| quantity | INTEGER NOT NULL | Unidades. Para `KG` siempre 1 por línea |
| unit_price | INTEGER NOT NULL | Para `KG`: el precio de la etiqueta |
| weight_grams | INTEGER NULL | Opcional |
| added_at | INTEGER NOT NULL | |
| position | INTEGER NOT NULL | Orden de inserción; la lista se muestra descendente |

`line_total = unit_price × quantity` se calcula, no se guarda.
Los productos `KG` generan una línea nueva por cada bolsa (no se incrementa cantidad).

### `price_history`
| Columna | Tipo | Notas |
| --- | --- | --- |
| id | INTEGER PK autoincrement | |
| product_id | INTEGER NOT NULL FK → product(id) | Índice `(product_id, at)` |
| price | INTEGER NOT NULL | |
| at | INTEGER NOT NULL | |
| source | TEXT NOT NULL | `SEED` \| `TRIP` \| `MANUAL` |

Solo se inserta cuando el precio cambia respecto al último registrado, y solo para `UNIT`.

### `budget`
| Columna | Tipo | Notas |
| --- | --- | --- |
| year_month | TEXT PK | `2026-11` |
| amount | INTEGER NOT NULL | |

### `shopping_list_item`
| Columna | Tipo | Notas |
| --- | --- | --- |
| product_id | INTEGER PK FK → product(id) | |
| suggested_qty | INTEGER NOT NULL DEFAULT 1 | |
| checked | INTEGER NOT NULL DEFAULT 0 | |
| source | TEXT NOT NULL | `AUTO` \| `MANUAL` |
| dismissed_until | INTEGER NULL | Si el usuario la quita, no se vuelve a sugerir hasta esta fecha |

## Consultas que deben existir (DAO)

- Producto por código: `barcode JOIN product` por PK. Es la consulta caliente del escaneo.
- Búsqueda: `name_norm LIKE '%' || :q || '%' AND active = 1 ORDER BY favorite DESC, name LIMIT 30`.
- Compra abierta con líneas como `Flow` (una sola consulta `@Transaction`).
- Total de la compra abierta: `SUM(unit_price * quantity)`.
- Gastado del mes: `SUM(COALESCE(total_paid, total_estimated))` de compras cerradas en el rango.
- Gasto por categoría del mes (para el resumen): `trip_item JOIN product JOIN category`; las
  líneas sin producto van a "Otros".
- Fechas y cantidades de compra por producto (para la lista sugerida).
- Últimos dos precios por producto (para variaciones).

## Operaciones transaccionales

- **Cerrar compra** (una transacción): fijar `total_estimated`, `closed_at`; para cada línea con
  producto `UNIT`: si `unit_price ≠ ref_price` → actualizar `ref_price` e insertar en
  `price_history`; para `KG`: actualizar `ref_price`; marcar `checked` en la lista.
- **Agregar código desconocido**: crear producto + insertar código + insertar línea.
- **Restaurar respaldo**: borrar todo e insertar todo, o no cambiar nada.

## Semilla del catálogo (`catalogo_inicial.json`)

```json
{
  "version": 1,
  "source": "Recibo <tienda> <fecha>",
  "products": [
    {
      "name": "Arroz Diana 1 kg",
      "category": "Granos y despensa",
      "unit": "UNIT",
      "price": 5900,
      "barcodes": ["7702511000014"],
      "favorite": false
    },
    {
      "name": "Tomate chonto",
      "category": "Frutas y verduras",
      "unit": "KG",
      "price": 4200,
      "barcodes": [],
      "favorite": true
    }
  ]
}
```

Reglas de importación:
- `category` desconocida → se crea. `unit` distinto de `UNIT`/`KG` → error de validación.
- `price` entero > 0 y ≤ 50.000.000. `name` 1–80 caracteres. Códigos pasan por `Ean`.
- Máximo 5.000 productos y 2 MB de archivo.
- Producto existente (mismo `name_norm`): se actualiza precio, categoría y se agregan códigos.
- Se inserta un punto en `price_history` con `source = SEED`.
- Todo o nada: un error de validación no deja nada a medias; el mensaje indica el índice del
  producto con problema.
- Las frutas, verduras y productos de granel de la semilla se marcan `favorite = true`.

## Respaldo cifrado (`.mercado`)

Contenido lógico: JSON con todas las tablas + `schemaVersion` + `exportedAt`, comprimido con GZIP.

Formato binario del archivo:

| Bytes | Contenido |
| --- | --- |
| 0–3 | Magia ASCII `MRC1` |
| 4–7 | Iteraciones PBKDF2 (int32 big-endian) |
| 8–23 | Sal (16 bytes aleatorios) |
| 24–35 | IV (12 bytes aleatorios) |
| 36–fin | AES-256-GCM(clave, IV, gzip(json)) con la etiqueta de 16 bytes al final; AAD = bytes 0–23 |

- Clave = PBKDF2WithHmacSHA256(contraseña, sal, 600.000 iteraciones, 256 bits).
- Contraseña mínima de 8 caracteres, tecleada dos veces al exportar. No se guarda en ningún lado.
- Al importar: validar magia, límites (archivo ≤ 20 MB, iteraciones entre 100.000 y 5.000.000),
  autenticar (GCM), validar `schemaVersion ≤` actual y el JSON completo antes de tocar la base.
- Lectura y escritura con el selector de documentos del sistema (`CreateDocument` /
  `OpenDocument`); la app no pide permisos de almacenamiento.
- Nombre sugerido: `mercado-AAAA-MM-DD.mercado`.

## Ajustes (DataStore, no sensibles)

| Clave | Tipo | Por defecto |
| --- | --- | --- |
| `lock_enabled` | Boolean | true |
| `lock_grace_minutes` | Int | 5 |
| `secure_screen` | Boolean | true |
| `scan_sound` | Boolean | true |
| `scan_vibration` | Boolean | true |
| `shopping_frequency_days` | Int | 15 |
| `reminders_enabled` | Boolean | true |
| `last_reminder_cycle` | Long | 0 |
| `last_backup_at` | Long | 0 |
| `seed_imported` | Boolean | false |
