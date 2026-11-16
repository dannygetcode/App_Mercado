# Progreso

Lo mantiene el agente. Una sesión nueva empieza leyendo este archivo.

**Loop actual:** L0
**Última actualización:** 2026-11-16
**Pasos `[HUMANO]` pendientes:** ninguno (el recibo ya está en `seed/recibo.pdf`)

## Tablero

| Loop | Estado | Puerta de salida |
| --- | --- | --- |
| L0 Base del proyecto | en curso | — |
| L1 Dominio | pendiente | — |
| L2 Datos cifrados | pendiente | — |
| L3 Escáner | pendiente | — |
| L4 Compra en curso | pendiente | — |
| L5 Resto de pantallas | pendiente | — |
| L6 Seguridad y respaldo | pendiente | — |
| L7 Lista, resúmenes, recordatorios | pendiente | — |
| L8 Release | pendiente | — |
| L9 Prueba de campo | pendiente | — |

Estados: `pendiente` · `en curso` · `esperando humano` · `cerrado`.

## Bitácora

Formato por loop (copiar el bloque al empezar cada uno):

```
### L<n> · <nombre>
- [ ] T1 — 
- [ ] T2 — 
...
**Informe de cierre**
- Verificación: <salida resumida de verificar.sh completo>
- Medido: <tiempos, tamaños, conteos de toques, lo que aplique>
- Desviaciones de la especificación: <ninguna | cuáles y por qué (también en DECISIONES)>
- Pendiente para Daniel: <pasos [HUMANO] con instrucciones exactas>
```

### L0 · Base del proyecto
- [x] T1 — Entorno: JDK 17.0.13, git 2.45.2, SDK en `%LOCALAPPDATA%\Android\Sdk` (plataformas 36/37, build-tools 36), adb 36.0.0 fuera del PATH (se usa por ruta). `local.properties` creado.
- [x] T2 — `gradle/libs.versions.toml` con las últimas estables (AGP 9.5.1, Kotlin 2.4.21, KSP 2.3.14, Compose BOM 2026.09.00…); detalle en DECISIONES.
- [x] T3 — `git init`, `.gitignore` + `.gitattributes` (LF), wrapper Gradle 9.9.1 con SHA-256, `:domain` (kotlin-jvm, JUnit Jupiter) y `:app` (Compose, KSP, serialization, minSdk 26, compile/target 37, `.debug`, R8, firma opcional). Compila.
- [x] T4 — Manifiesto: INTERNET/ACCESS_NETWORK_STATE/ACCESS_WIFI_STATE con `tools:node="remove"`, `allowBackup`/`fullBackupContent` false, `data_extraction_rules.xml` excluye todo, `usesCleartextTraffic=false`; se elimina `ProfileInstallReceiver` (exportado) por S-11. Retrato se fija en la actividad (T5). Manifiesto fusionado de release revisado.
- [x] T5 — `MercadoApp`, `MainActivity` (retrato, edge-to-edge), tema M3 propio claro/oscuro con colores de `04-ux.md` y semáforo en `LocalTrafficLightColors`, estilos con cifras tabulares, `HomeScreen` provisional "Mercado", `AppContainer` vacío, `AppLog` (no-op en release), ícono adaptativo vectorial. `assembleDebug` OK.
- [x] T6 — `scripts/verificar.sh` (`rapido`/`completo`) con todos los chequeos de `06-pruebas.md` más dos de S-11 (release no depurable, solo el lanzador exportado); resumen de una línea por chequeo. Patrones probados en negativo. Pruebas triviales: `DomainSmokeTest` (JUnit Jupiter) y `HomeScreenTest` (Robolectric + Compose). Lint sin hallazgos.
- [x] T7 — `CHANGELOG.md` inicial. El primer commit se hizo en T3 (al hacer `git init`), porque el protocolo pide un commit por tarea; T7 agrega el changelog en su propio commit.
