package com.blackjack.blackjack.service;

import com.blackjack.blackjack.exception.GameRuleException;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_TOKENS;

@Service
public class PlayerService {
    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    /**
     * Levonja a tétet a játékos tokenjeiből,
     * immutábilis módon létrehozva egy új Player objektumot a frissített állapottal.
     * * @param oldPlayer Az eredeti (régi) Player objektum.
     *
     * @param betAmount A levonandó tét összege.
     * @return Az újonnan mentett Player objektum.
     */
    @Transactional
    public Player deductBet(Player oldPlayer, int betAmount) {
        if (oldPlayer.getTokens() < betAmount) {
            throw new GameRuleException("NOT_ENOUGH_TOKENS_FOR_BET");
        }

        int newTokens = oldPlayer.getTokens() - betAmount;

        // Immutábilis frissítés: új Player objektum létrehozása toBuilder() és build() segítségével
        Player updatedPlayer = oldPlayer.toBuilder()
            .tokens(newTokens)
            .build();

        // Elmentjük az új, frissített objektumot.
        return playerRepository.save(updatedPlayer);
    }

    /**
     * Hozzáadja a nyereményt a játékos tokenjeihez.
     * * @param oldPlayer Az eredeti (régi) Player objektum.
     *
     * @param rewardAmount A hozzáadandó jutalom összege.
     * @return Az újonnan mentett Player objektum.
     */
    @Transactional
    public Player updateTokens(Player oldPlayer, int rewardAmount) {
        int newTokens = oldPlayer.getTokens() + rewardAmount;

        // Immutábilis frissítés
        Player updatedPlayer = oldPlayer.toBuilder()
            .tokens(newTokens)
            .build();

        return playerRepository.save(updatedPlayer);
    }

    /**
     * Visszaállítja a játékos tokenjeit egy kezdő értékre.
     * * @param oldPlayer Az eredeti (régi) Player objektum.
     *
     * @param initialTokens A beállítandó kezdő token mennyiség.
     * @return Az újonnan mentett Player objektum.
     */
    @Transactional
    public Player resetTokens(Player oldPlayer, int initialTokens) {
        // Immutábilis frissítés
        Player updatedPlayer = oldPlayer.toBuilder()
            .tokens(initialTokens)
            .build();

        return playerRepository.save(updatedPlayer);
    }

    @Transactional
    public Player getOrCreatePlayer(UUID clientId) {
        return playerRepository.findByClientId(clientId)
            .orElseGet(() -> {
                // Ez fut le, ha a kliens most jár nálunk először
                Player newPlayer = Player.builder()
                    .clientId(clientId)
                    .tokens(INITIAL_TOKENS)
                    .idempotencyKey(UUID.randomUUID())
                    .currentGameState(null)
                    .build();

                return playerRepository.save(newPlayer);
            });
    }

    public Player getAndValidatePlayer(UUID clientId) {
        if (clientId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Missing Client ID");
        }
        return playerRepository.findByClientId(clientId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Player not found"));
    }

    @Transactional
    public Player savePlayer(Player player) {
        return playerRepository.save(player);
    }

    public boolean isDuplicateRequest(Player player, UUID incomingKey) {
        return player.getIdempotencyKey() != null &&
            player.getIdempotencyKey().equals(incomingKey);
    }
}
