package com.blackjack.blackjack.repository;

import com.blackjack.blackjack.model.Player;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {
    Optional<Player> findByClientId(UUID clientId);

    Optional<Player> findByEmailOrUsername(String email, String username);

    boolean existsByEmailOrUsername(String email, String username);

    Optional<Player> findByUsername(String username);

    void delete(@NonNull Player player);
}
