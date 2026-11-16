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
