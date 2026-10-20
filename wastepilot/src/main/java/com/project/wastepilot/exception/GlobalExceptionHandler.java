package com.project.wastepilot.exception;

import com.project.wastepilot.domain.dto.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
    return buildResponse(ex.getStatus(), ex.getCode(), ex.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidationException(
      MethodArgumentNotValidException ex,
      HttpServletRequest request
  ) {
    // Collect all field errors, not just the first, so the client can fix multiple issues at once.
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(FieldError::getDefaultMessage)
        .collect(Collectors.joining("; "));
    if (message.isBlank()) message = "Validation failed.";
    return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message, request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
      ConstraintViolationException ex,
      HttpServletRequest request
  ) {
    String message = ex.getConstraintViolations().stream()
        .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
        .collect(Collectors.joining("; "));
    return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message, request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleMalformedJson(
      HttpMessageNotReadableException ex,
      HttpServletRequest request
  ) {
    LOGGER.debug("Malformed JSON request", ex);
    return buildResponse(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
        "Request body is malformed or missing.", request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
      HttpRequestMethodNotSupportedException ex,
      HttpServletRequest request
  ) {
    return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
        "HTTP method " + ex.getMethod() + " is not supported on this endpoint.", request);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiErrorResponse> handleFileTooLarge(
      MaxUploadSizeExceededException ex,
      HttpServletRequest request
  ) {
    return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
        "Uploaded file exceeds the maximum permitted size.", request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiErrorResponse> handleAccessDenied(
      AccessDeniedException ex,
      HttpServletRequest request
  ) {
    return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN",
        "You do not have permission to perform this action.", request);
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is invalid.", request);
  }

  /**
   * Catch-all for any unhandled Throwable (including Error subclasses, not just Exception).
   * Returns a generic 500 response — never internal details — while logging the full exception.
   */
  @ExceptionHandler(Throwable.class)
  public ResponseEntity<ApiErrorResponse> handleUnknownException(Throwable ex, HttpServletRequest request) {
    LOGGER.error("Unhandled request failure for {} {}", request.getMethod(), request.getRequestURI(), ex);
    return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
        "An unexpected error occurred. Please try again or contact support.", request);
  }

  private ResponseEntity<ApiErrorResponse> buildResponse(
      HttpStatus status,
      String code,
      String message,
      HttpServletRequest request
  ) {
    ApiErrorResponse body = new ApiErrorResponse(
        Instant.now(),
        status.value(),
        code,
        message,
        request.getRequestURI()
    );
    return ResponseEntity.status(status).body(body);
  }
}
