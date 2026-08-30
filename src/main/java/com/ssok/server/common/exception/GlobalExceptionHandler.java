package com.ssok.server.common.exception;

import com.ssok.server.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail(DuplicateEmailException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(HttpStatus.CONFLICT.value(), e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), e.getMessage()));
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthenticated(UnauthenticatedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), e.getMessage()));
    }

    @ExceptionHandler(SpaceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleSpaceNotFound(SpaceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), e.getMessage()));
    }

    @ExceptionHandler(BookmarkNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleBookmarkNotFound(BookmarkNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), e.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), e.getMessage()));
    }

    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleMemberNotFound(MemberNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), e.getMessage()));
    }

    @ExceptionHandler(TagNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTagNotFound(TagNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), e.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(InvalidRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleServerError(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "server error"));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(
            ForbiddenException e
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        HttpStatus.FORBIDDEN.value(),
                        e.getMessage()
                ));
    }
}
