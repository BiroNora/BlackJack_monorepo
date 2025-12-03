package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.exception.GameRuleException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/test")
public class TestController {
    // 1. Teszteli a GameRuleException handler-t (400 Bad Request)
    // Cél: JSON válasz: {"errorCode": "TEST_RULE_VIOLATION"}
    @GetMapping("/rule_error")
    public void throwGameRuleError() {
        throw new GameRuleException("TEST_RULE_VIOLATION");
    }

    // 2. Teszteli a ResponseStatusException handler-t (500 Internal Server Error)
    // Cél: JSON válasz: {"errorCode": "TEST_CRITICAL_FAILURE"}
    @GetMapping("/internal_error")
    public void throwInternalError() {
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "TEST_CRITICAL_FAILURE"
        );
    }

    // 3. Teszteli a generikus Exception handler-t (Fallback 500)
    // Cél: JSON válasz: {"errorCode": "UNEXPECTED_SERVER_ERROR"}
    @GetMapping("/generic_error")
    public void throwGenericError() {
        // Ez a kivétel eljut a GlobalExceptionHandler végén lévő
        // @ExceptionHandler(Exception.class) blokkhoz
        throw new RuntimeException("Valami meglepő történt a teszt során.");
    }
}
