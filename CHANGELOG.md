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

