# App Mercado — instrucciones para el agente implementador

Eres el implementador de **Mercado**, una app Android personal (Kotlin + Jetpack Compose) para
llevar la cuenta del mercado del hogar contra un presupuesto. Todo corre en el teléfono: sin nube,
sin servidor, sin costo. Se instala como APK firmado.

**La vara única del producto:** debe ser más rápida y más fácil que sumar con la calculadora del
teléfono en el supermercado. Ante cualquier duda de diseño, gana la opción con menos toques y
menos espera para el usuario que está en el pasillo con el carrito en una mano.

## Qué leer y en qué orden

1. `plan/PROGRESO.md` — dónde va el trabajo. Léelo SIEMPRE primero.
2. `plan/PLAN.md` — los loops (iteraciones), sus tareas y su puerta de salida.
3. El documento de `docs/` que cite la tarea que vas a hacer:
   - `docs/01-producto.md` — requerimientos (RF/RNF) con criterios de aceptación.
   - `docs/02-arquitectura.md` — módulos, paquetes, dependencias, decisiones cerradas (D-xx).
   - `docs/03-datos.md` — esquema, DAOs, formato de semilla y de respaldo.
   - `docs/04-ux.md` — pantallas, interacciones, textos.
   - `docs/05-seguridad.md` — controles obligatorios y cómo se verifican.
   - `docs/06-pruebas.md` — casos de prueba nombrados que deben existir.
   - `docs/07-release.md` — firma, instalación, particularidades de HyperOS.

Los documentos de `docs/` son la fuente de verdad. Las decisiones marcadas D-xx están cerradas:
no las reabras ni las "mejores". Si una resulta imposible, sigue el protocolo de bloqueo.

## Protocolo de trabajo (el loop)

Trabajas un loop de `plan/PLAN.md` a la vez, tarea por tarea, en orden.

Para cada tarea:

1. **Leer** la tarea y las secciones de `docs/` que cita.
2. **Implementar** solo lo que la tarea pide. Nada de funcionalidades extra.
3. **Verificar** con los comandos de la tarea (como mínimo `./scripts/verificar.sh rapido`).
   Antes de que exista el script (L0-T1 a T5), verifica con `./gradlew` directamente.
4. **Corregir** hasta que pase. Máximo 3 intentos sobre el mismo error; al tercero, bloqueo.
5. **Registrar** en `plan/PROGRESO.md`: marcar la tarea `[x]` y una línea con lo hecho.
6. **Commit** con mensaje `L<loop>-T<tarea>: <qué cambió>`.

Al terminar todas las tareas del loop:

7. Ejecutar la **puerta de salida** completa del loop (`./scripts/verificar.sh completo` más lo
   que liste el loop).
8. Escribir el **informe de cierre** del loop en `plan/PROGRESO.md` (qué quedó, qué se midió,
   desviaciones).
9. Si la puerta tiene pasos `[HUMANO]`, detenerse y pedírselos a Daniel con instrucciones exactas.
   Si no, continuar con el siguiente loop.

### Protocolo de bloqueo

Si algo impide cumplir una tarea o una decisión D-xx (tras 3 intentos, o porque falta algo que
solo Daniel puede dar):

- Anótalo en `plan/BLOQUEOS.md` con: tarea, qué se intentó, error exacto, opciones y tu recomendación.
- Si el bloqueo no impide las demás tareas del loop, continúa con ellas.
- Nunca resuelvas un bloqueo bajando un requisito, desactivando una prueba o un control de
  seguridad. Eso lo decide Daniel.

### Decisiones nuevas

Lo que los documentos no cubran lo decides tú con el criterio de "la vara única", y lo anotas en
`plan/DECISIONES.md` (una línea: fecha, decisión, razón). No preguntes por detalles menores.

## Reglas que no se rompen

- **Sin red:** el manifiesto fusionado de release no puede contener `android.permission.INTERNET`
  ni `ACCESS_NETWORK_STATE`. `verificar.sh` lo comprueba. No agregues dependencias que lo exijan.
- **Dinero:** siempre `Pesos` (Long, pesos enteros). Prohibido `Float`/`Double` para montos.
- **Dominio puro:** el módulo `:domain` no importa nada de `android.*` ni `androidx.*`.
- **Datos cifrados:** la base solo se abre con SQLCipher y la clave del Keystore. Sin atajos en release.
- **Sin datos en logs:** nada de nombres de productos, precios o totales en `Log` en release.
- **Sin secretos en git:** keystore de firma, `keystore.properties` y `*.mercado` están en `.gitignore`.
- **Migraciones:** prohibido `fallbackToDestructiveMigration`. Cada cambio de esquema lleva
  migración y prueba.
- **Pruebas:** no se borra ni se marca `@Ignore` una prueba para pasar la puerta.
- **Idioma:** interfaz en español (es-CO), textos en `strings.xml`. Código e identificadores en
  inglés. Documentos y commits en español.
- **Dependencias:** solo las de `docs/02-arquitectura.md`. Una nueva exige anotarla en DECISIONES.

## Comandos

```bash
./scripts/verificar.sh rapido     # pruebas de :domain + compilación debug
./scripts/verificar.sh completo   # todo: pruebas, lint, assembleDebug, chequeo de manifiesto
./gradlew :domain:test
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk   # con el teléfono conectado
```

Entorno: Windows con Git Bash. Usa rutas relativas y `./gradlew`.
