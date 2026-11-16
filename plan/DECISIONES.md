# Decisiones tomadas durante la implementación

Las decisiones de diseño cerradas (D-01 a D-22) están en `docs/02-arquitectura.md`. Aquí van solo
las que surjan al implementar: versiones elegidas, detalles no especificados, cambios forzados.

| Fecha | Tarea | Decisión | Razón |
| --- | --- | --- | --- |
| 2026-11-16 | L0-T1 | El SDK está en `%LOCALAPPDATA%\Android\Sdk` (sin `ANDROID_HOME`); se apunta con `local.properties`. `adb` no está en el PATH: se usa `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe` | Entorno de Daniel; no requiere instalar nada |
| 2026-11-16 | L0-T2 | Versiones: AGP 9.5.1, Gradle 9.9.1 (wrapper con SHA-256), Kotlin 2.4.21, KSP 2.3.14, Compose BOM 2026.09.00, Room 2.8.5, sqlite-ktx 2.7.1, SQLCipher 4.20.0, CameraX 1.6.2, ML Kit barcode 17.3.0, biometric 1.1.0, WorkManager 2.12.0, DataStore 1.3.0, Navigation 2.10.2, serialization 1.13.0, coroutines 1.11.0, JUnit 6.1.3 (BOM, Jupiter), JUnit 4.13.2, Robolectric 4.18, Turbine 1.2.1, androidx.test core 1.7.0 / ext-junit 1.3.0 / runner 1.7.0, core-ktx 1.19.1, activity-compose 1.13.0, lifecycle 2.11.0 | Últimas estables en Google Maven / Maven Central el 2026-11-16. JUnit 6 es la línea estable actual de JUnit Jupiter ("JUnit 5" en D-15) |
| 2026-11-16 | L0-T2 | Kotlin 2.4.21 en vez del 2.2.10 que trae AGP 9.5.1: se fija declarando `org.jetbrains.kotlin.jvm`/`plugin.compose` con versión en el `build.gradle.kts` raíz, lo que sube el KGP integrado de AGP | Una sola versión de Kotlin en ambos módulos y plugin de Compose alineado; la doc pide "la que acompaña al AGP (2.2.x/2.3.x)" como línea base, no como tope |
| 2026-11-16 | L0-T3 | compileSdk = targetSdk = 37 (Android 17) | Último estable que AGP 9.5.1 acepta sin advertencias (D-21) |
| 2026-11-16 | L0-T3 | Dependencias auxiliares no listadas explícitamente en la tabla: `androidx.core:core-ktx`, `activity-compose`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`, `compose-ui-tooling(-preview)`, `ui-test-manifest`, `kotlinx-coroutines-android`, `junit-platform-launcher`, `androidx.test:runner`, `androidx.test.ext:junit` | Son las piezas mínimas para Compose + ViewModel y para correr las pruebas descritas en D-15; todas de Google Maven / Maven Central |
