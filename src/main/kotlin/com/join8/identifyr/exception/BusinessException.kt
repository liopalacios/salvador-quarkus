package com.join8.identifyr.exception

/** Regla de negocio incumplida con un request bien formado. Se traduce a 422 en [GlobalExceptionHandler]. */
class BusinessException(message: String) : RuntimeException(message)
