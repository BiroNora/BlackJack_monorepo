package com.blackjack.blackjack.service;

import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public Optional<Player> getPlayerByClientId(UUID clientId) {
        return playerRepository.findByClientId(clientId);
    }

    /**
     * Levonja a tétet a játékos tokenjeiből és menti az új egyenleget.
     * Ez a metódus tranzakciós, hogy biztosítsa az adatok integritását!
     */
    @Transactional
    public Player deductBetAndSave(Player player, int betAmount) {
        if (player.getTokens() < betAmount) {
            throw new IllegalArgumentException("Insufficient tokens.");
        }

        player.setTokens(player.getTokens() - betAmount);
        return playerRepository.save(player);
    }
}
