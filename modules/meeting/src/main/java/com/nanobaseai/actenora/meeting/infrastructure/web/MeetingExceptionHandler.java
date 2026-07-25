package com.nanobaseai.actenora.meeting.infrastructure.web;

import com.nanobaseai.actenora.meeting.api.MeetingProblemDetails;
import com.nanobaseai.actenora.meeting.domain.relation.CrossTenantRelationException;
import com.nanobaseai.actenora.meeting.domain.relation.CyclicRelationException;
import com.nanobaseai.actenora.meeting.domain.relation.DuplicateRelationException;
import com.nanobaseai.actenora.meeting.domain.relation.SuggestionNotFoundException;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice(basePackages = "com.nanobaseai.actenora.meeting.infrastructure.web")
public class MeetingExceptionHandler {

    @ExceptionHandler(ActenoraException.class)
    public ResponseEntity<String> handleActenora(ActenoraException ex, HttpServletRequest request) {
        return problem(MeetingProblemDetails.from(ex, URI.create(request.getRequestURI())));
    }

    @ExceptionHandler(DuplicateRelationException.class)
    public ResponseEntity<String> duplicateRelation(DuplicateRelationException ex, HttpServletRequest request) {
        return problem(MeetingProblemDetails.fromCode(
                ex.code(),
                ex.getMessage(),
                URI.create(request.getRequestURI())
        ));
    }

    @ExceptionHandler(CyclicRelationException.class)
    public ResponseEntity<String> cyclicRelation(CyclicRelationException ex, HttpServletRequest request) {
        return problem(MeetingProblemDetails.fromCode(
                ex.code(),
                ex.getMessage(),
                URI.create(request.getRequestURI())
        ));
    }

    @ExceptionHandler(CrossTenantRelationException.class)
    public ResponseEntity<String> crossTenantRelation(CrossTenantRelationException ex, HttpServletRequest request) {
        return problem(MeetingProblemDetails.fromCode(
                ex.code(),
                ex.getMessage(),
                URI.create(request.getRequestURI())
        ));
    }

    @ExceptionHandler(SuggestionNotFoundException.class)
    public ResponseEntity<String> suggestionNotFound(SuggestionNotFoundException ex, HttpServletRequest request) {
        return problem(MeetingProblemDetails.fromCode(
                ex.code(),
                ex.getMessage(),
                URI.create(request.getRequestURI())
        ));
    }

    private static ResponseEntity<String> problem(MeetingProblemDetails problem) {
        return ResponseEntity.status(problem.status())
                .contentType(MediaType.parseMediaType(MeetingProblemDetails.MEDIA_TYPE))
                .body(problem.toJson());
    }
}
