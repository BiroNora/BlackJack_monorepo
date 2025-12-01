package com.blackjack.blackjack.repository;

import com.blackjack.blackjack.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {
    Optional<Player> findByClientId(UUID clientId);
}
