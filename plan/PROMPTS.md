# Prompts para el agente

Abre Claude Code en esta carpeta (`App_Mercado`), selecciona el modelo Opus 5.5 y pega el prompt
que corresponda. `CLAUDE.md` se carga solo.

Recomendación: **una sesión por loop**. Al cerrar un loop, empieza una sesión nueva con el prompt
de continuación; el estado está en `plan/PROGRESO.md`, no en la memoria de la conversación.

## 1 · Arranque (primera vez)

```
Vas a implementar la app Mercado completa siguiendo el paquete de trabajo de esta carpeta.

1. Lee CLAUDE.md, plan/PROGRESO.md y plan/PLAN.md completos, y docs/02-arquitectura.md.
2. Ejecuta el loop L0 tarea por tarea con el protocolo de CLAUDE.md: implementar, verificar,
   corregir, registrar en plan/PROGRESO.md y hacer commit.
3. Al terminar las tareas, ejecuta la puerta de salida de L0 y escribe el informe de cierre.
4. Si la puerta no tiene pasos [HUMANO] bloqueantes, continúa con L1 de la misma forma.

No me preguntes por detalles que los documentos ya deciden o que puedas decidir con el criterio
de "la vara única"; anótalos en plan/DECISIONES.md. Detente solo ante un paso [HUMANO] bloqueante
o un bloqueo registrado en plan/BLOQUEOS.md, y dime exactamente qué necesitas de mí.
```

## 2 · Continuación (cada sesión nueva)

```
Continúa la implementación de Mercado.

1. Lee plan/PROGRESO.md y plan/BLOQUEOS.md para ver dónde quedó el trabajo y qué respondí.
2. Lee en plan/PLAN.md el loop actual y los documentos de docs/ que ese loop indica.
3. Ejecuta ./scripts/verificar.sh completo para confirmar que partes de un estado sano; si falla,
   arréglalo antes de avanzar.
4. Sigue con la siguiente tarea pendiente usando el protocolo de CLAUDE.md, hasta cerrar el loop
   con su puerta de salida e informe de cierre. Si no hay pasos [HUMANO] bloqueantes, continúa
   con el siguiente loop.
```

## 3 · Después de un paso [HUMANO]

```
Hice el paso [HUMANO] de L<n>. Resultado:
- <lo que observé, con números si aplica>
- <qué se sintió lento o confuso>

Registra el resultado en plan/PROGRESO.md, corrige lo que haga falta según la puerta de salida
de ese loop y continúa.
```

## 4 · Reporte de un problema en uso real

```
Problema usando la app: <qué hice, qué esperaba, qué pasó>.

Reprodúcelo con una prueba que falle, corrígelo, verifica con ./scripts/verificar.sh completo,
anótalo en CHANGELOG.md y deja listo el APK.
```

## 5 · Revisión independiente (opcional, al cerrar L4 y L6)

Ábrelo en una sesión nueva para que quien revisa no sea quien escribió el código.

```
Actúa como revisor. No modifiques código. Lee docs/01-producto.md, docs/04-ux.md y
docs/05-seguridad.md, y luego el código de :domain y :app.

Entrega una lista priorizada de: (1) requisitos M no cumplidos o cumplidos a medias,
(2) controles de seguridad S-xx sin evidencia real, (3) cualquier interacción de la compra en
curso que cueste más toques o más espera de lo especificado. Para cada hallazgo: archivo, línea
y qué dice el documento. Escríbela en plan/REVISION_L<n>.md.
```
