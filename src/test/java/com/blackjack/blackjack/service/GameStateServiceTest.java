package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.GameState;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A GameStateService unit tesztjei.
 * A teszt a Mockito-t használja a Spring Session és a RequestContextHolder szimulálására,
 * így elszigetelten ellenőrizhető a mentési/lekérdezési logika.
 */
@ExtendWith(MockitoExtension.class)
public class GameStateServiceTest {
}
