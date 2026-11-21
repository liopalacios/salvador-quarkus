package com.join8.identifyr.exception

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

/**
 * Formato único de error: { timestamp, status, error: "<CODIGO>", message, path? }. Nunca stack traces.
 * Los códigos son el nombre del estado HTTP (BAD_REQUEST, NOT_FOUND, ...) salvo los específicos
 * (VALIDATION_ERROR, ACCOUNT_LOCKED, ...).
 */
@RestControllerAdvice
class GlobalExceptionHandler(private val clock: Clock) {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun error(
        status: HttpStatus,
        message: String,
        code: String = status.name,
        path: String? = null,
        idPrestamo: java.util.UUID? = null,
        headers: Map<String, String> = emptyMap()
    ): ResponseEntity<ErrorResponse> {
        val builder = ResponseEntity.status(status)
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.body(
            ErrorResponse(
                timestamp = LocalDateTime.now(),
                status = status.value(),
                error = code,
                message = message,
                path = path,
                idPrestamo = idPrestamo
            )
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException) =
        error(HttpStatus.BAD_REQUEST, ex.message ?: "Argumento inválido")

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(ex: ResourceNotFoundException) =
        error(HttpStatus.NOT_FOUND, ex.message ?: "Recurso no encontrado")

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(ex: BusinessException) =
        error(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "Regla de negocio incumplida")

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException) =
        error(HttpStatus.CONFLICT, ex.message ?: "Conflicto", idPrestamo = ex.idPrestamo)

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException) =
        error(HttpStatus.FORBIDDEN, ex.message ?: "Acceso denegado", code = ex.code)

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException) =
        error(HttpStatus.UNAUTHORIZED, ex.message!!, code = "INVALID_CREDENTIALS")

    @ExceptionHandler(AccountLockedException::class)
    fun handleLocked(ex: AccountLockedException): ResponseEntity<ErrorResponse> {
        val seconds = Duration.between(clock.instant(), ex.lockedUntil).seconds.coerceAtLeast(1)
        return error(
            HttpStatus.LOCKED, ex.message!!, code = "ACCOUNT_LOCKED",
            headers = mapOf(HttpHeaders.RETRY_AFTER to seconds.toString())
        )
    }

    @ExceptionHandler(NotImplementedException::class)
    fun handleNotImplemented(ex: NotImplementedException) =
        error(HttpStatus.NOT_IMPLEMENTED, ex.message ?: "No disponible")

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = error(
        HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes, intenta nuevamente más tarde",
        headers = mapOf(HttpHeaders.RETRY_AFTER to ex.retryAfterSeconds.toString())
    )

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException) =
        error(HttpStatus.CONFLICT, ex.message ?: "Estado inválido")

    // Solo field + mensaje de la anotación: nunca el valor rechazado (podría ser la contraseña)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.allErrors.joinToString(", ") { error ->
            (if (error is FieldError) "${error.field}: ${error.defaultMessage}" else error.defaultMessage).toString()
        }
        return error(HttpStatus.BAD_REQUEST, errors, code = "VALIDATION_ERROR")
    }

    // JSON mal formado o sin un campo obligatorio (Kotlin no admite null en campos no opcionales)
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        val field = (ex.cause as? MismatchedInputException)?.path?.joinToString(".") { it.fieldName ?: "[${it.index}]" }
        return error(
            HttpStatus.BAD_REQUEST,
            if (field.isNullOrBlank()) "Cuerpo JSON inválido" else "Falta o es inválido el campo: $field"
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException) =
        error(HttpStatus.BAD_REQUEST, "Valor inválido para ${ex.name}: ${ex.value}")

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingPart(ex: MissingServletRequestPartException) =
        error(HttpStatus.BAD_REQUEST, "Falta la parte '${ex.requestPartName}' del formulario")

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUpload(ex: MaxUploadSizeExceededException) =
        error(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el tamaño máximo permitido")

    // Va después de MaxUploadSizeExceededException (que es una subclase): multipart mal formado
    @ExceptionHandler(MultipartException::class)
    fun handleMultipart(ex: MultipartException) =
        error(HttpStatus.BAD_REQUEST, "Formulario multipart inválido")

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaType(ex: HttpMediaTypeNotSupportedException) =
        error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado: ${ex.contentType}")

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(ex: NoResourceFoundException) =
        error(HttpStatus.NOT_FOUND, "Ruta no encontrada", path = "/${ex.resourcePath}")

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(ex: HttpRequestMethodNotSupportedException) =
        error(HttpStatus.METHOD_NOT_ALLOWED, "Método ${ex.method} no permitido")

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Error no controlado", ex)
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado")
    }
}

data class ErrorResponse(
    val timestamp: LocalDateTime,
    val status: Int,
    /** Código de error estable para la app (UNAUTHORIZED, FORBIDDEN, ACCOUNT_LOCKED, BAD_REQUEST, ...). */
    val error: String,
    val message: String,
    val path: String?,
    /** Solo en el 409 de préstamo ya registrado; null se omite del JSON (default-property-inclusion: non_null). */
    val idPrestamo: java.util.UUID? = null
)
