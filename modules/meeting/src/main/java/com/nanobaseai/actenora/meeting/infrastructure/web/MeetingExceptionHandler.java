package com.nanobaseai.actenora.meeting.infrastructure.web;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice(basePackages = "com.nanobaseai.actenora.meeting.infrastructure.web")
public class MeetingExceptionHandler {

    @ExceptionHandler(ActenoraException.class)
    public ResponseEntity<Map<String, Object>> handleActenora(ActenoraException ex) {
        HttpStatus status = switch (ex.code()) {
            case "MEETING_NOT_FOUND", "BUSINESS_CONTEXT_NOT_FOUND", "TENANT_ISOLATION_VIOLATION" -> HttpStatus.NOT_FOUND;
            case "OPTIMISTIC_LOCK_CONFLICT" -> HttpStatus.CONFLICT;
            case "DUPLICATE_GRAPH_IDENTITY", "DUPLICATE_OCCURRENCE_IDENTITY" -> HttpStatus.CONFLICT;
            case "INVALID_MEETING_TRANSITION", "INVALID_DATE_RANGE", "INVALID_PARTICIPANT" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "UNAUTHORIZED_MEETING_ACCESS", "PRIVATE_NOTE_ACCESS_DENIED", "PRIVATE_NOTE_AI_ACCESS_DENIED",
                 "INVALID_MEETING_APP_TOKEN" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of(
                "code", ex.code(),
                "message", ex.getMessage(),
                "correlationId", UUID.randomUUID().toString(),
                "details", Map.of()
        ));
    }
}
