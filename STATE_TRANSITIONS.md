# Estado de Reservas y Pago

## Diagrama de flujo y responsables

```
PENDIENTE_ACEPTACION
      | Paseador: aceptar
      v
    ACEPTADO
      | Dueño: pagar
      v
   CONFIRMADO

PENDIENTE_ACEPTACION --> RECHAZADO     (Paseador)
PENDIENTE_ACEPTACION --> CANCELADO    (Dueño)
ACEPTADO             --> CANCELADO    (Dueño, previo pago)
CONFIRMADO           --> EN_CURSO/COMPLETADO (Servicios posteriores)
```

## Reglas y validaciones por pantalla

- **ReservaActivity**: crea el documento con `ESTADO_PENDIENTE_ACEPTACION` y `ESTADO_PAGO_PENDIENTE`, bloqueando cualquier pago previo a la aceptacion.
- **SolicitudesActivity**: solo muestra las reservas donde `estado == ESTADO_PENDIENTE_ACEPTACION` para que el paseador tome una decision valida.
- **SolicitudDetalleActivity**: utiliza `ReservaEstadoValidator.canTransition` para habilitar/inhabilitar aceptar o rechazar, y actualiza los estados a `ACEPTADO` o `RECHAZADO`.
- **ConfirmarPagoActivity**: guarda `estado`/`estado_pago` en Firebase solo si la reserva esta en `ACEPTADO`, muestra mensajes especificos para cada estado y evita pagos en `RECHAZADO`, `CANCELADO` o si ya se pago.
- **PaseosActivity**: solo cuenta estado de pago como completado cuando `estado_pago == ESTADO_PAGO_CONFIRMADO` y muestra `CONFIRMADO`/`EN_CURSO` como paseos activos.

## Funciones clave refactorizadas

- `ReservaEstadoValidator`: expone los estados canonicos y autoriza unicamente las transiciones validas (`PENDIENTE_ACEPTACION` -> `ACEPTADO`, `ACEPTADO` -> `CONFIRMADO`, etc.) ademas de la validacion de pagos.
- `ConfirmarPagoActivity.validarReserva()` usa `ReservaEstadoValidator.canPay` y `isPagoCompletado`, y `procesarPago()` revalida el estado antes de escribir `CONFIRMADO`.
- `SolicitudDetalleActivity.aceptarSolicitud()`/`rechazarSolicitud()` solo actualizan el estado cuando `canTransition` lo permite.
- `PaseosActivity` filtra los paseos activos con `isPagoCompletado` para evitar cargas duplicadas.

## Pruebas automaticas

- `ReservaEstadoValidatorTest` comprueba que las transiciones validas estan permitidas (`PENDIENTE_ACEPTACION` -> `ACEPTADO`), que solo `ACEPTADO` puede pagar y que los estados terminales (`RECHAZADO`, `CANCELADO`) no admiten nuevos cambios.
- También verifica que `isPagoCompletado` distingue entre `ESTADO_PAGO_PENDIENTE` y `ESTADO_PAGO_CONFIRMADO`.

Documentar cada cambio con esta referencia ayuda a mantener la trazabilidad y evita inconsistencias de estado en futuras actualizaciones.
