# 01 · Producto y requerimientos

## Visión

Quien hace el mercado hoy va sumando en la calculadora del teléfono. Es lento, ocupa las dos manos,
no deja rastro y no avisa cuando el presupuesto se acaba. **Mercado** hace lo mismo mejor: apuntas
la cámara al código de barras y el producto queda sumado con su precio de referencia; el total y lo
que queda del presupuesto están siempre a la vista.

## Principios de producto (en orden de prioridad)

1. **Nunca más lento que la calculadora.** El peor caso de la app (producto desconocido, sin código,
   cámara fallando) debe ser igual de rápido que teclear el precio en la calculadora: por eso existe
   el modo teclado (RF-17), siempre a un toque.
2. **Cero confirmaciones en el camino feliz.** Lo conocido se suma solo; el error se corrige con
   "Deshacer", no se previene con diálogos.
3. **Una mano, pulgar, tercio inferior.** Todo lo que se toca en el pasillo está abajo y es grande.
4. **El precio de referencia es una estimación, y la app lo dice.** Los precios vienen de compras
   pasadas; corregirlos debe costar un toque + teclear.
5. **Nada sale del teléfono.**

## Usuario y contexto

- Daniel y su familia en Tunja (Colombia). Un solo hogar, un teléfono principal (Poco X8 Pro,
  Android 16, HyperOS 3). Debe funcionar también en Samsung (One UI).
- Moneda: pesos colombianos, sin centavos. Formato `$5.900` (punto como separador de miles).
- Uso típico: de pie, carrito en una mano, iluminación de supermercado, a veces sin señal.

## Requerimientos funcionales

Prioridad: **M** = MVP (loops 1–6), **S** = segunda iteración (loop 7), **C** = pendiente, no se
implementa salvo instrucción de Daniel.

### RF-01 · Catálogo de productos (M)
Producto = nombre, categoría, tipo de unidad (`UNIT` o `KG`), precio de referencia, 0..n códigos de
barras, favorito, activo, pendiente de revisar.
- Crear, editar, desactivar (no se borra si tiene historial), buscar por nombre.
- Búsqueda sin tildes ni mayúsculas ("limon" encuentra "Limón").

### RF-02 · Carga inicial desde el recibo (M)
- Si existe `assets/seed/catalogo_inicial.json` y la base está vacía, se importa al primer arranque.
- También se puede importar un JSON desde Ajustes (selector de archivos del sistema).
- Importación idempotente: un producto con el mismo nombre normalizado se actualiza, no se duplica.
- Formato en `docs/03-datos.md`.

### RF-03 · Compra en curso con escaneo continuo (M)
- La cámara lee sin botón de disparo. Formatos: EAN-13, EAN-8, UPC-A, UPC-E, Code 128.
- Código conocido → se agrega la línea con precio de referencia y cantidad 1. Si el producto ya
  está en la compra → cantidad +1 en esa línea (no línea nueva).
- Retroalimentación inmediata: vibración corta + pitido (silenciable) + aviso con "Deshacer".
- Antirrebote: el mismo código leído otra vez dentro de 1.500 ms se ignora.
- Se valida el dígito de control de EAN/UPC antes de aceptar la lectura.
- Producto `KG` escaneado → se abre el teclado pidiendo el precio de la etiqueta (ver RF-06).
- La pantalla no se apaga durante la compra.
- Solo puede haber una compra abierta. Si la app se cierra, al volver la compra sigue intacta.

### RF-04 · Código desconocido: precio primero, nombre después (M)
- Se abre la hoja "Producto nuevo" con el teclado de precio ya activo.
- Con solo el precio se puede agregar: se crea el producto con nombre provisional
  `Producto ····<últimos 4 dígitos>` y `needsReview = true`, y el código queda asociado.
- En la misma hoja, campo opcional de nombre con autocompletado del catálogo: si elige un
  producto existente, el código se asocia a ese producto (no se crea otro).
- Al cerrar la compra se listan los pendientes para nombrarlos con calma.

### RF-05 · Editar líneas (M)
- `+` / `−` cambian cantidad (en 0 la línea se elimina, con "Deshacer").
- Tocar el precio abre el teclado para corregirlo. El precio corregido vale para esa línea y pasa
  a ser el precio de referencia al cerrar la compra.
- Deslizar a la izquierda elimina la línea (con "Deshacer").
- "Deshacer" revierte exactamente la última acción (agregar, incrementar, eliminar, editar).

### RF-06 · Agregar sin código: favoritos y búsqueda (M)
- Rejilla de favoritos (hasta 12 visibles sin desplazar) con nombre grande.
- Buscador por nombre con resultados al teclear.
- Producto `UNIT` → se agrega directo. Producto `KG` → teclado pidiendo **el precio total de la
  etiqueta de balanza** (lo que dice el sticker). El peso es opcional.

### RF-07 · Presupuesto y semáforo (M)
- Presupuesto mensual (por mes calendario). Si un mes no tiene, hereda el del mes anterior.
- Límite de la compra = el que el usuario fije al iniciar, o por defecto lo que queda del mes
  (presupuesto − gastado en compras cerradas del mes).
- En la compra siempre visibles: total, límite, saldo restante y barra de progreso.
- Semáforo sobre total/límite: verde < 80 %, amarillo 80–100 %, rojo > 100 %. Al cruzar a amarillo
  y a rojo: vibración distinta, una vez por cruce.
- Sin presupuesto configurado la app funciona igual y muestra solo el total.

### RF-08 · Cerrar compra (M)
- Resumen: número de artículos, total estimado, comparación con el límite.
- Campo opcional "Total pagado según el recibo" (por defecto vacío; si se llena, ese es el gasto
  real del mes).
- Al cerrar: se actualiza el precio de referencia de cada producto con el precio usado, se guarda
  el historial de precios, se marcan como comprados los ítems de la lista, se muestran los
  productos pendientes de nombrar.
- Una compra cerrada se puede consultar pero no editar, salvo el total pagado.
- Una compra abierta se puede descartar (con confirmación; es la única confirmación de la app
  junto con restaurar respaldo).

### RF-17 · Modo teclado: sumar un precio suelto (M)
- Un botón siempre visible abre el teclado numérico; se teclea el precio y "Sumar".
- Crea una línea "Artículo sin nombre" (sin producto asociado). Se puede nombrar después tocándola.
- Teclado propio de la app (no el del sistema): dígitos, `00`, `000`, borrar. Teclas ≥ 56 dp.
- Este es el piso de velocidad: 1 toque + dígitos + 1 toque.

### RF-09 · Lista sugerida (S)
- Para productos con ≥ 2 compras: intervalo medio entre compras. Entra a la lista cuando
  `días desde la última compra ≥ 0,8 × intervalo medio`. Cantidad sugerida = mediana de cantidades.
- Se pueden agregar y quitar ítems a mano. Al escanear o agregar un producto de la lista en la
  compra, se marca solo.
- En la compra en curso, pestaña "Lista" con lo que falta por echar al carrito.

### RF-10 · Historial y resúmenes (S)
- Lista de compras cerradas (fecha, tienda opcional, total, nº de artículos) y su detalle.
- Resumen del mes: gastado vs presupuesto, gasto por categoría (barras), top 10 productos por
  gasto, y productos cuyo precio subió o bajó más frente a su compra anterior.
- Comparación de los últimos 6 meses (barras).

### RF-11 · Recordatorios locales (S)
- Frecuencia de mercado configurable en días (por defecto 15). Notificación cuando faltan ≤ 1 día
  para cumplirla desde la última compra cerrada: "Se acerca el mercado · N productos sugeridos".
- Una sola notificación por ciclo. Desactivable.

### RF-12 · Respaldo cifrado (M)
- Exportar todo a un archivo `.mercado` cifrado con contraseña; importar restaura todo
  (reemplaza los datos actuales, con confirmación). Formato en `docs/03-datos.md`.
- Recordatorio mensual de hacer respaldo (S).

### RF-13 · Presupuesto por categoría (S)
- Tope mensual opcional por categoría; en el resumen del mes se muestra el avance de cada una.

### Pendientes (C) — no implementar sin instrucción
- RF-14 Etiquetas de balanza con precio embebido (códigos que empiezan por 2): requiere fotos de
  etiquetas reales de los supermercados de Daniel.
- RF-15 Importar recibo por foto con OCR en el dispositivo.
- RF-16 Compartir lista con otro teléfono por archivo o QR.

## Requerimientos no funcionales

| ID | Requisito | Meta medible | Cómo se verifica |
| --- | --- | --- | --- |
| RNF-01 | Velocidad de escaneo | Código conocido sumado en < 1 s desde que entra en cuadro, 0 toques | Prueba de campo (06-pruebas) |
| RNF-02 | Arranque | Ícono → cámara activa en < 2 s en el Poco X8 Pro (con huella) | Cronómetro, 5 intentos |
| RNF-03 | Ergonomía | Objetivos táctiles ≥ 48 dp; acciones de compra en el tercio inferior | Revisión de UI |
| RNF-04 | Offline total | Manifiesto de release sin INTERNET ni ACCESS_NETWORK_STATE | `verificar.sh` |
| RNF-05 | Seguridad | Todos los controles de `05-seguridad.md` | Lista de verificación |
| RNF-06 | Compatibilidad | minSdk 26; probado en HyperOS 3 y en un Samsung | Instalación manual |
| RNF-07 | Costo | $0: sin servicios, sin Play Store | — |
| RNF-08 | Integridad del dinero | Montos en `Pesos` (Long); sin Float/Double | Revisión + pruebas |
| RNF-09 | Robustez | Matar la app en medio de una compra no pierde ninguna línea | Prueba manual + Robolectric |
| RNF-10 | Tamaño | APK release ≤ 40 MB | `ls -l` del APK |
| RNF-11 | Accesibilidad | contentDescription en íconos; contraste AA; respeta tamaño de fuente del sistema | Revisión de UI |

## Fuera de alcance

Precios en línea de supermercados, nube o sincronización, cuentas de usuario, iPhone, Play Store,
publicidad, analítica.
