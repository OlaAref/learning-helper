package com.olaaref.dbpracticelab.web;

import com.olaaref.dbpracticelab.lab.LabException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Errors reach the UI as {"message": "..."} so it can show them as they are. */
@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(LabException.class)
  ResponseEntity<Map<String, String>> lab(LabException e) {
    return ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", String.valueOf(e.getReason())));
  }
}
