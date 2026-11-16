# Changelog

Formato: una entrada por versión (`versionName` semántico). Las fechas están en hora de Colombia.

## [1.0.0] — 2026-11-16

Primera versión completa (MVP + segunda iteración). Resumen por loop abajo.

- Compra en curso con escáner continuo (ML Kit sin red), total con semáforo, teclado de precio
  propio, producto nuevo con precio primero, favoritos, búsqueda, deshacer y cierre de compra.
- Modo "Etiqueta": lee el precio de la etiqueta del estante y prellena el teclado (RF-18).
- Inicio con el estado del mes, presupuesto mensual con herencia, catálogo, historial y ajustes.
- Lista de mercado sugerida, resumen del mes con topes por categoría y variaciones de precio,
  recordatorios de mercado y de respaldo.
- Seguridad: base cifrada (SQLCipher + Keystore), bloqueo con huella/PIN, pantalla segura, sin
  permisos de red, respaldo cifrado `.mercado`, release optimizado con R8.

## Historial de desarrollo

### L0 · Base del proyecto (2026-11-16)
- Proyecto Gradle con dos módulos: `:domain` (Kotlin/JVM puro) y `:app` (Android, Compose).
- Catálogo de versiones con las últimas estables; wrapper de Gradle 9.9.1 con suma SHA-256.
- Manifiesto endurecido: sin permisos de red, sin copias automáticas, sin tráfico en claro,
  solo retrato y sin componentes exportados salvo el lanzador.
- App mínima: tema propio claro/oscuro, pantalla "Mercado", `AppContainer` y `AppLog`.
- `scripts/verificar.sh` con los chequeos de seguridad y pruebas de humo en ambos módulos.

### L1 · Dominio (2026-11-16)
- Modelo con dinero exacto (`Pesos`), compra, catálogo y presupuesto.
- Reglas puras: formato de pesos, validación de códigos EAN/UPC/Code 128, antirrebote,
  semáforo, normalización de nombres y meses en hora de Colombia.
- Casos de uso de compra (escanear, desconocido, suelto, por peso, editar, deshacer, cerrar,
  descartar) y de presupuesto y catálogo, con 71 pruebas en la JVM.

### L2 · Datos cifrados (2026-11-16)
- Base Room v1 cifrada con SQLCipher; clave de 32 bytes envuelta por el Android Keystore.
- Repositorios Room para todos los puertos del dominio, con transacciones.
- Importador de catálogo JSON validado, idempotente y todo-o-nada; semilla real de 48
  productos transcrita del recibo e importada en el primer arranque.
- Ajustes con DataStore.

### L3 · Escáner (2026-11-16)
- CameraX 1.6.2 y ML Kit Barcode Scanning 17.3.0 (bundled) detrás de `BarcodeDecoder`; solo
  EAN-13, EAN-8, UPC-A, UPC-E y Code 128; se acepta la primera lectura con dígito de control válido.
- `ScannerView` en Compose: lectura continua, pausa, linterna, sin `ImageCapture` ni cuadros a disco;
  permiso de cámara negado deja la pantalla usable con "Sin acceso a la cámara" y "Permitir".
- Retroalimentación de lectura (vibración 30 ms y pitido; doble vibración para desconocido) según ajustes.
- Pantalla de diagnóstico del escáner (solo debug) con tiempo a primera lectura y lecturas por minuto.
- `FakeBarcodeSource` y prueba instrumentada de humo de cámara.
- `verificar.sh` comprueba que ML Kit está incluido sin ningún permiso de red.

### L4 · Compra en curso (2026-11-16)
- Pantalla "Compra en curso": cámara arriba, total grande con semáforo, lista con la última línea
  arriba, aviso con "Deshacer" y barra inferior (Teclado, Favoritos, Buscar, Cerrar).
- Teclado de precio propio (precio suelto, etiqueta de balanza, corregir precio, crear producto).
- Producto nuevo con precio primero y nombre opcional con autocompletado ("Asociar y sumar").
- Favoritos y búsqueda en una hoja; editar línea, deslizar para eliminar, `−` hasta 0 elimina.
- Vibración distinta al cruzar el 80 % y el 100 % del límite; pausa automática de la cámara.
- Cierre de compra: total pagado, tienda, productos por nombrar, guardar o descartar.
- Inicio provisional con "Ir a mercar" / "Continuar compra" y límite prellenado.

### L5 · Resto de pantallas (2026-11-16)
- Navegación con rutas tipadas y barra inferior; acceso directo del lanzador "Nueva compra".
- Inicio real: estado del mes, tarjetas, primer uso y presupuesto mensual con herencia.
- Catálogo con búsqueda, filtros y orden de favoritos; editor de producto con códigos escaneados,
  historial de precios y desactivar/reactivar.
- Historial por mes y detalle con total pagado editable; Ajustes con todas las secciones e
  importación de catálogo JSON.
- Estados vacíos, error si la base no abre, tema oscuro y accesibilidad (letra a 1,3×).

### L6 · Seguridad y respaldo (2026-11-16)
- Bloqueo con `BiometricPrompt` (huella o PIN) y gracia de 1, 5 o 15 minutos; pantalla segura.
- Respaldo cifrado `MRC1` (PBKDF2 600.000 + AES-256-GCM), restauración validada y transaccional.
- Auditoría S-01..S-15 con evidencia; solo repositorios Google Maven y Maven Central.

### L7 · Lista, resúmenes y recordatorios (2026-11-16)
- Lectura del precio de la etiqueta del estante (ML Kit texto, sin red).
- Lista de mercado sugerida con motivo, pestaña Lista en la compra.
- Resumen del mes con barras, topes por categoría, top 10, subidas y bajadas, 6 meses.
- Recordatorios con WorkManager (mercado y respaldo mensual).

### L8 · Release (2026-11-16)
- Reglas de R8 (incluida la de ML Kit por reflexión), build `qa`, arranque ~200 ms, APK 35,2 MB.
- Lint a cero; la fábrica de base sin cifrar sale del código de la app.
- README, CHANGELOG y documentos actualizados; versión 1.0.0.

