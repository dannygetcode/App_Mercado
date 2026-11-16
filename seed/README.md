# Semilla del catálogo

Aquí va el recibo del último mercado y el catálogo inicial que sale de él.

## Qué debe hacer Daniel

1. Guardar el recibo en esta carpeta como `recibo.jpg`, `recibo.png` o `recibo.pdf`
   (si son varias fotos: `recibo-1.jpg`, `recibo-2.jpg`, ...). Que se lea bien cada renglón.
2. Después de que el agente genere `catalogo_inicial.json`, revisarlo: nombres, precios,
   categorías y cuáles son por peso.

El recibo está en `.gitignore`; el catálogo generado sí se versiona.

## Qué debe hacer el agente (L2-T6)

Transcribir el recibo a `catalogo_inicial.json` con el formato de `docs/03-datos.md`:

- Un producto por renglón distinto del recibo. Si un producto aparece varias veces, una sola
  entrada.
- `price` = precio **unitario** en pesos enteros (si el recibo trae cantidad × valor, usar el
  unitario; si solo trae el total de la línea y la cantidad, dividir).
- Nombres legibles: expandir abreviaturas del recibo cuando sea evidente
  ("ARROZ DIANA X1000G" → "Arroz Diana 1 kg"). Si no es evidente, dejar el texto del recibo en
  minúsculas con mayúscula inicial y listarlo como dudoso.
- `barcodes`: solo si el recibo imprime el código completo y pasa la validación EAN. Los códigos
  internos de la tienda (PLU cortos) no se incluyen.
- `unit`: `KG` para lo vendido por peso (el recibo muestra kg o g y precio por kilo); en ese caso
  `price` = el valor pagado por esa bolsa. Todo lo demás `UNIT`.
- `category`: una de las diez de `docs/03-datos.md`.
- `favorite: true` para frutas, verduras, carnes por peso y granel.
- No incluir descuentos, bolsas, impuestos ni totales.
- Dejar en `plan/PROGRESO.md` la lista de renglones dudosos (texto ilegible, abreviatura no
  entendida, precio que no cuadra) para revisión de Daniel.
- Verificar: la suma de `precio unitario × cantidad` del recibo debe coincidir con el subtotal
  del recibo; si no cuadra, revisar antes de dar la tarea por hecha.
