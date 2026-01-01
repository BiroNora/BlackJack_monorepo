package com.blackjack.blackjack.repository;

import com.blackjack.blackjack.model.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlayerRepositoryTest {

    @Autowired
    private PlayerRepository playerRepository;

    @Test
    @DisplayName("findByClientId: Megtalálja a mentett játékost")
    void testFindByClientId() {
        UUID clientId = UUID.randomUUID();
        Player player = Player.builder()
            .clientId(clientId)
            .tokens(1000)
            .idempotencyKey(UUID.randomUUID())        // nullable = false miatt kell
            .build();
        playerRepository.save(player);

        Optional<Player> found = playerRepository.findByClientId(clientId);

        assertTrue(found.isPresent());
        assertEquals(clientId, found.get().getClientId());
    }
}
