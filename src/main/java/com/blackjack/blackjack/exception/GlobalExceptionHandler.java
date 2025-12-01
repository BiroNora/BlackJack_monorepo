package com.blackjack.blackjack.exception;

import com.blackjack.blackjack.dto.error.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
public class GlobalExceptionHandler {

    // 400 Bad Request
    @ExceptionHandler(GameRuleException.class)
    public ResponseEntity<ErrorResponse> handleGameRuleException(GameRuleException ex) {

        System.err.println("Game Rule Violation: " + ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
            ex.getMessage()
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {

        System.err.println("Válasz státusz hiba: " + ex.getStatusCode() + " - " + ex.getReason());

        String errorCode;
        if (ex.getReason() != null) {
            errorCode = ex.getReason();
        } else {
            errorCode = "UNKNOWN_CLIENT_ERROR";
        }

        ErrorResponse errorResponse = new ErrorResponse(errorCode);

        return new ResponseEntity<>(errorResponse, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {

        System.err.println("Szerver Kritikus Hiba: " + ex.getMessage());
        ex.printStackTrace();

        ErrorResponse errorResponse = new ErrorResponse(
            "UNEXPECTED_SERVER_ERROR"
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
