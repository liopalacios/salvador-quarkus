package com.join8.identifyr.exception

/** Se traduce a 429 con cabecera Retry-After en [GlobalExceptionHandler]. */
class RateLimitExceededException(val retryAfterSeconds: Long) : RuntimeException("Demasiadas solicitudes")
