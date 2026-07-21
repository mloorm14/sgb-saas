# Cómo completar OBSERVACIONES.md

Este archivo documenta el procedimiento para registrar las observaciones reales
recibidas del docente en las Entregas 1A y 1B, antes de crear el tag `v0.7.1`.

## Procedimiento

1. Por cada observación real recibida (retroalimentación escrita, comentario en
   la rúbrica, corrección solicitada en la defensa oral, etc.), asignar un
   código secuencial `OBS-NN` (OBS-01, OBS-02, ...).
2. Implementar la corrección correspondiente en **un commit separado por cada
   observación** — no agrupar varias observaciones en un mismo commit, para
   mantener trazabilidad 1:1.
3. El mensaje de commit debe seguir el formato:

   ```
   fix(módulo): descripción breve (OBS-NN)
   ```

   Ejemplo: `fix(auth): agregar validación de longitud mínima de contraseña (OBS-03)`

4. Una vez creado el commit, tomar su hash corto (`git log --oneline -1`) y
   añadir una fila nueva a la tabla de [OBSERVACIONES.md](OBSERVACIONES.md) con:
   - **Código:** `OBS-NN`
   - **Fuente:** Entrega 1A o Entrega 1B (o "Defensa oral", según corresponda)
   - **Criterio:** el criterio de la rúbrica al que aplica
   - **Observación:** texto de lo que el docente señaló
   - **Decisión:** si se aceptó, se aceptó parcialmente (justificar) o se
     rechazó (justificar por qué no aplica)
   - **Commit:** el hash corto entre backticks, p. ej. `` `abc1234` ``
5. Repetir para cada observación pendiente.
6. Cuando la tabla esté completa con todas las observaciones reales, avisar
   para crear manualmente el tag `v0.7.1` sobre el commit final de esta serie.

## Notas

- No editar retroactivamente un commit ya creado para "corregir" una
  observación adicional; cada observación es un commit nuevo.
- Si una observación afecta a varios módulos, dividirla en sub-commits por
  módulo y registrar cada uno con el mismo código `OBS-NN` pero commits
  distintos (se puede listar más de un hash separado por coma en la celda
  Commit).
