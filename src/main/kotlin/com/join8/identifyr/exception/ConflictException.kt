package com.join8.identifyr.exception

import java.util.*

/**
 * El recurso ya existe o choca con otro (duplicado). Se traduce a 409 en [GlobalExceptionHandler].
 * [idPrestamo] permite a la app saber que un registro anterior sí se guardó (reintento tras un corte de red).
 */
class ConflictException(message: String, val idPrestamo: UUID? = null) : RuntimeException(message)
