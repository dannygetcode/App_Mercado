# Changelog

Formato: una entrada por versión (`versionName` semántico). Las fechas están en hora de Colombia.

## [Sin publicar]

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
