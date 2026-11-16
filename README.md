# App Mercado

App Android personal para llevar la cuenta del mercado contra el presupuesto: escanear, sumar, listo.

Esta carpeta contiene el **paquete de trabajo** para implementar la app completa con un agente
(Claude Code con Opus 5.5).

## Cómo ponerlo a trabajar

1. Deja el recibo del último mes en `seed/` (foto o PDF, nombre `recibo.*`). Ver `seed/README.md`.
2. Abre una terminal en esta carpeta e inicia Claude Code con el modelo Opus 5.5.
3. Pega el prompt de arranque de `plan/PROMPTS.md`.
4. El agente avanza loop por loop y se detiene cuando necesita algo de ti (pasos `[HUMANO]`).
5. Para retomar en otra sesión, pega el prompt de continuación. El estado vive en `plan/PROGRESO.md`.

## Mapa

| Ruta | Contenido |
| --- | --- |
| `CLAUDE.md` | Reglas y protocolo del agente (se carga solo) |
| `docs/` | Especificación: producto, arquitectura, datos, UX, seguridad, pruebas, release |
| `plan/PLAN.md` | Los 10 loops con tareas y puertas de salida |
| `plan/PROGRESO.md` | Tablero de avance (lo actualiza el agente) |
| `plan/BLOQUEOS.md` | Lo que el agente no pudo resolver solo |
| `plan/DECISIONES.md` | Decisiones tomadas durante la implementación |
| `plan/PROMPTS.md` | Prompts listos para copiar |
| `seed/` | Recibo y catálogo inicial |
