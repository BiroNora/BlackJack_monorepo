package com.blackjack.blackjack.service;

import com.blackjack.blackjack.exception.GameRuleException;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
