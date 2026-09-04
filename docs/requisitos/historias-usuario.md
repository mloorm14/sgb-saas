# Historias de usuario — Módulo de Préstamos/Devoluciones/Reservaciones/Multas

Formato Connextra + criterios de aceptación en Gherkin, siguiendo la
plantilla de `docs/reparto-entrega-3/cajas-backend/INSTRUCCIONES.md`
(sección 9). Cubre los 5 flujos mínimos exigidos: crear préstamo,
registrar devolución, crear reservación, pagar multa, anular multa.

## HU-01: Registrar un préstamo

**Como** bibliotecario,
**quiero** registrar el préstamo de un libro a un lector,
**para** llevar control de qué ejemplares están fuera de la biblioteca
y cuándo deben devolverse.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Registro de préstamos

  Escenario: Préstamo exitoso con stock disponible
    Dado que el libro "Clean Code" tiene stock disponible mayor a 0
    Y el usuario "juan@correo.com" tiene estado ACTIVO
    Cuando el bibliotecario registra un préstamo de "Clean Code" para "juan@correo.com"
    Entonces el préstamo se crea con estado ACTIVO
    Y el stock disponible del libro se decrementa en 1

  Escenario: Intento de préstamo sin stock disponible
    Dado que el libro "Clean Code" tiene stock disponible igual a 0
    Cuando el bibliotecario intenta registrar un préstamo de "Clean Code"
    Entonces la operación se rechaza con un error 422
    Y el mensaje indica que no hay stock disponible

  Escenario: Intento de préstamo para un usuario bloqueado
    Dado que el usuario "maria@correo.com" tiene estado BLOQUEADO_POR_MULTA
    Cuando el bibliotecario intenta registrar un préstamo para "maria@correo.com"
    Entonces la operación se rechaza con un error 422
    Y el mensaje indica que el usuario tiene multas pendientes
```

## HU-02: Registrar una devolución

**Como** bibliotecario,
**quiero** registrar la devolución de un libro prestado,
**para** liberar el stock del ejemplar y detectar automáticamente si
hubo atraso que amerite una multa.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Registro de devoluciones

  Escenario: Devolución sin atraso
    Dado que el préstamo con id 1 está ACTIVO y su fecha límite aún no venció
    Cuando el bibliotecario registra la devolución del préstamo 1
    Entonces el préstamo cambia a estado DEVUELTO
    Y el stock disponible del libro se incrementa en 1
    Y no se genera ninguna multa

  Escenario: Devolución con atraso
    Dado que el préstamo con id 2 está ACTIVO y su fecha límite ya venció
    Cuando el bibliotecario registra la devolución del préstamo 2
    Entonces el préstamo cambia a estado DEVUELTO
    Y se genera una multa con estado_multa_id PENDIENTE
    Y el usuario del préstamo queda con estado BLOQUEADO_POR_MULTA

  Escenario: Intento de doble devolución del mismo préstamo
    Dado que el préstamo con id 1 ya tiene estado DEVUELTO
    Cuando el bibliotecario intenta registrar la devolución del préstamo 1 de nuevo
    Entonces la operación se rechaza con un error 409
    Y el mensaje indica que el préstamo ya fue devuelto
```

## HU-03: Crear una reservación

**Como** lector,
**quiero** reservar un libro que actualmente no tiene stock disponible,
**para** asegurarme un ejemplar en cuanto se libere.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Creación de reservaciones

  Escenario: Reservación propia exitosa
    Dado que el lector "juan@correo.com" tiene sesión iniciada
    Cuando reserva el libro "Domain-Driven Design" para sí mismo
    Entonces la reservación se crea con estado PENDIENTE
    Y la fecha de reserva es la fecha y hora actuales
    Y se calcula una fecha límite de retiro

  Escenario: Bibliotecario reserva en nombre de otro usuario
    Dado que el bibliotecario tiene sesión iniciada
    Cuando registra una reservación de "Domain-Driven Design" para "maria@correo.com"
    Entonces la reservación se crea a nombre de "maria@correo.com" con estado PENDIENTE

  Escenario: Lector intenta reservar en nombre de otro usuario
    Dado que el lector "juan@correo.com" tiene sesión iniciada
    Cuando intenta enviar en el request el usuarioId de "maria@correo.com"
    Entonces la reservación se crea igualmente a nombre de "juan@correo.com"
    Y se ignora cualquier usuarioId distinto al propio enviado en el request
```

## HU-04: Pagar una multa

**Como** bibliotecario,
**quiero** registrar el pago de una multa pendiente,
**para** que el lector recupere la posibilidad de solicitar préstamos.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Pago de multas

  Escenario: Pago exitoso que desbloquea al usuario
    Dado que la multa con id 5 tiene estado PENDIENTE
    Y el usuario asociado tiene estado BLOQUEADO_POR_MULTA
    Y no tiene ninguna otra multa PENDIENTE
    Cuando el bibliotecario registra el pago de la multa 5
    Entonces la multa cambia a estado PAGADA
    Y el usuario vuelve a estado ACTIVO
    Y el usuario puede iniciar sesión normalmente (ya no responde 423)

  Escenario: Pago que no desbloquea porque quedan otras multas pendientes
    Dado que el usuario tiene dos multas PENDIENTE (id 5 e id 6)
    Cuando el bibliotecario registra el pago de la multa 5
    Entonces la multa 5 cambia a estado PAGADA
    Y el usuario permanece en estado BLOQUEADO_POR_MULTA por la multa 6
```

## HU-05: Anular una multa

**Como** gerente,
**quiero** anular una multa registrada por error o por una excepción
justificada,
**para** corregir el estado del lector sin dejar de auditar quién
tomó la decisión.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Anulación de multas

  Escenario: Anulación exitosa por rol GERENTE
    Dado que el gerente tiene sesión iniciada
    Y la multa con id 7 tiene estado PENDIENTE
    Cuando el gerente anula la multa 7 con un motivo justificado
    Entonces la multa cambia a estado ANULADA
    Y se registra una fila nueva en bitacora_auditoria

  Escenario: Intento de anulación con rol no autorizado
    Dado que un bibliotecario tiene sesión iniciada
    Cuando intenta anular la multa 7
    Entonces la operación se rechaza con un error 403 antes de llegar al procedimiento
    (el @PreAuthorize del controller bloquea el intento; el propio
    procedimiento SQL también rechazaría con LB422 como defensa en
    profundidad si el rol llegara a evaluarse)

  Escenario: El rol ejecutor nunca se toma del cuerpo del request
    Dado que un bibliotecario autenticado envía en el body "rolEjecutor": "GERENTE"
    Cuando intenta anular una multa
    Entonces el sistema ignora por completo ese campo del body
    Y resuelve el rol real únicamente desde la sesión autenticada
    Y la operación se rechaza igual, porque el rol real es BIBLIOTECARIO
