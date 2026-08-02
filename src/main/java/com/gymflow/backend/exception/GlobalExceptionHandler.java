package com.gymflow.backend.exception;

import com.gymflow.backend.client.ChatCompletionException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manejador global de excepciones.
 *
 * Por qué: antes de esto, el proyecto no tenía ningún @ControllerAdvice.
 * Cualquier RuntimeException lanzada desde los servicios (ej. "Usuario no
 * encontrado", "Ya existe un usuario con ese email") caía en el manejo por
 * defecto de Spring Boot y salía como un 500 Internal Server Error genérico
 * — status code incorrecto (debería ser 404/409/400 según el caso) y,
 * dependiendo del perfil activo, con riesgo de filtrar detalles internos en
 * el cuerpo de la respuesta.
 *
 * Este handler no pretende ser exhaustivo para cada excepción de negocio
 * posible (para eso lo ideal a futuro es reemplazar las RuntimeException
 * genéricas de los servicios por excepciones de dominio propias, ej.
 * UsuarioNoEncontradoException, EmailYaRegistradoException). Por ahora,
 * centraliza el manejo de las condiciones de error más comunes y
 * confirmadas durante la auditoría de seguridad.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                errores.put(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Datos inválidos", errores);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, "Datos inválidos", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Datos inválidos", null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        // Mensaje deliberadamente genérico: no distinguir "usuario no
        // encontrado" de "password incorrecta" evita enumeración de cuentas
        // (hallazgo 3.1 del threat model).
        return build(HttpStatus.UNAUTHORIZED, "Email o contraseña incorrectos", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        // Cubre violaciones de constraint única (ej. email duplicado si en
        // el futuro se agrega la constraint a nivel DB, o la unique
        // constraint de suscripción activa por usuario).
        log.warn("Violación de integridad de datos: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "El recurso ya existe o entra en conflicto con datos existentes", null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT,
                "El recurso fue modificado por otra operación mientras tanto. Volvé a intentar.", null);
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<Map<String, Object>> handleRedisDown(RedisConnectionFailureException ex) {
        log.error("Redis no disponible: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Servicio temporalmente no disponible", null);
    }

    @ExceptionHandler(ChatCompletionException.class)
    public ResponseEntity<Map<String, Object>> handleChatCompletionFailure(ChatCompletionException ex) {
        // El proveedor LLM cayó o devolvió un error — no es culpa del
        // usuario ni un bug nuestro, así que 503 (mismo criterio que
        // handleRedisDown) en vez de 500. El detalle real queda solo en el
        // log, nunca en la respuesta.
        log.error("Fallo al llamar al proveedor de chat: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "El asistente no está disponible en este momento. Intenta de nuevo en un momento.", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        // @PreAuthorize falla (ej. CLIENTE llamando a un endpoint de ADMIN).
        // AccessDeniedException extiende RuntimeException, así que sin este
        // handler caía en handleGenericRuntime -> inferirStatus no reconocía
        // el mensaje -> 500 "Error interno inesperado" en vez de 403, y se
        // logueaba como error real en cada intento denegado (ver
        // collab/aplicado/2026-08-01-fix-access-denied-403.md).
        return build(HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta acción", null);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleGenericRuntime(RuntimeException ex) {
        // Red de seguridad para las RuntimeException "planas" que hoy lanzan
        // los servicios (ej. "Usuario no encontrado con id: X"). Mapeo
        // heurístico por contenido del mensaje mientras esas excepciones no
        // se reemplazan por tipos de dominio propios. No es elegante, pero
        // es honesto: es mejor que un 500 con traza filtrada.
        String msg = ex.getMessage() != null ? ex.getMessage() : "Error inesperado";
        HttpStatus status = inferirStatus(msg);
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            // Solo lo que de verdad no reconocemos se loguea como error real
            // y no expone el mensaje interno tal cual al cliente.
            log.error("Error no controlado", ex);
            return build(status, "Error interno inesperado", null);
        }
        return build(status, msg, null);
    }

    private HttpStatus inferirStatus(String msg) {
        String m = msg.toLowerCase();
        if (m.contains("no encontrad")) return HttpStatus.NOT_FOUND;
        if (m.contains("ya existe") || m.contains("ya tiene una suscripción")) return HttpStatus.CONFLICT;
        // Mensaje genérico de AuthService cuando reveal-email-exists-on-register=false
        // (ver collab/aplicado/2026-07-16-decision-reveal-email-false.md). No contiene
        // "ya existe" a propósito (es el punto: no revelar), pero sigue siendo
        // semánticamente un conflicto de recurso -> 409, no 500. Sin este match caía
        // en INTERNAL_SERVER_ERROR: status incorrecto, mensaje pisado, y logueado
        // como error real en cada intento de registro duplicado.
        if (m.contains("si ya tienes una cuenta")) return HttpStatus.CONFLICT;
        if (m.contains("solo se pueden")) return HttpStatus.BAD_REQUEST;
        if (m.contains("propio rol") || m.contains("admin activo")) return HttpStatus.BAD_REQUEST;
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message, Object detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("message", message);
        if (detail != null) {
            body.put("errores", detail);
        }
        return ResponseEntity.status(status).body(body);
    }
}
