package org.aventyrs.api.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import org.aventyrs.api.image.ImageStorageException;
import org.aventyrs.core.sheet.IllegalOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.aventyrs.api.monster.MonsterRulesViolationException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        warn(request, ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND.getReasonPhrase(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        log.warn("{} {} -> validation failed: {}", request.getMethod(), request.getRequestURI(), details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        "Validation failed", details));
    }

    /**
     * A path or query value didn't fit the parameter's type — e.g. {@code POST
     * /api/scenes/{id}/move/UP}, where {@code UP} is not a {@code Direction}. A malformed request,
     * so {@code 400}; the message names the offending value and what it should have been.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Class<?> required = ex.getRequiredType();
        String expected = required != null && required.isEnum()
                ? "one of " + Arrays.toString(required.getEnumConstants())
                : (required != null ? "a " + required.getSimpleName() : "a different type");
        String message = "'" + ex.getValue() + "' is not a valid value for '" + ex.getName() + "'; expected " + expected;
        log.warn("{} {} -> {}", request.getMethod(), request.getRequestURI(), message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), message));
    }

    /**
     * A monster blueprint that breaks the creation rules — every violation core's {@code
     * MonsterRules#validate} found, one detail line each ({@code CODE:subject:actual:limit}), so the
     * editor can show them all. The request was well-formed but not legal, so {@code 400}.
     */
    @ExceptionHandler(MonsterRulesViolationException.class)
    public ResponseEntity<ApiError> handleMonsterRules(MonsterRulesViolationException ex, HttpServletRequest request) {
        warn(request, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        ex.getMessage(), ex.details()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        warn(request, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), ex.getMessage()));
    }

    /**
     * A core-rules operation was refused because the domain state doesn't allow it right now —
     * e.g. {@code SCENE_ALREADY_IN_COMBAT} from {@code SceneService#startCombat}. The state, not
     * the request shape, is the problem, so it maps to {@code 409} rather than {@code 400}. The
     * message is core's own {@code TranslatableMessages} key, for the client to localize.
     */
    @ExceptionHandler(IllegalOperationException.class)
    public ResponseEntity<ApiError> handleIllegalOperation(IllegalOperationException ex, HttpServletRequest request) {
        warn(request, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(), ex.getMessage()));
    }

    /**
     * Another request changed a {@code @Version}ed document between this one's read and its save,
     * e.g. two GMs starting a Sessão at once ({@code CampaignDocument}). The caller should reload
     * and retry.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLocking(OptimisticLockingFailureException ex, HttpServletRequest request) {
        warn(request, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),
                        "The resource was changed by another request; reload and retry"));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        warn(request, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(), ex.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> handleDuplicateKey(DuplicateKeyException ex, HttpServletRequest request) {
        warn(request, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),
                        "A resource with the same unique key already exists"));
    }

    @ExceptionHandler(ImageStorageException.class)
    public ResponseEntity<ApiError> handleImageStorage(ImageStorageException ex, HttpServletRequest request) {
        log.error("{} {} -> {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiError.of(HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY.getReasonPhrase(),
                        ex.getMessage()));
    }

    /** The multipart body went over {@code spring.servlet.multipart.max-*-size} (10MB). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        warn(request, ex);
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ApiError.of(HttpStatus.CONTENT_TOO_LARGE.value(), HttpStatus.CONTENT_TOO_LARGE.getReasonPhrase(),
                        "Upload exceeds the maximum allowed size"));
    }

    /**
     * Anything no handler above claims. Logged with its stack trace so no failure goes unseen;
     * the response hides the details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) throws Exception {
        // Spring's own web exceptions (404 no handler, 405, 415, missing parameter, access
        // denied, ...) already carry the right status — let the framework render those.
        if (ex instanceof ErrorResponse
                || ex instanceof AccessDeniedException
                || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("{} {} -> unexpected {}", request.getMethod(), request.getRequestURI(), ex.getClass().getName(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "Unexpected server error"));
    }

    /** One line for a refused request: a 4xx is the caller's problem, so no stack trace. */
    private static void warn(HttpServletRequest request, Exception ex) {
        log.warn("{} {} -> {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getClass().getSimpleName(), ex.getMessage());
    }
}
