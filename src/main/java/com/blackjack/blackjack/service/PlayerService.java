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

    @Transactional
    public Player deductBet(Player player, int betAmount) {
        if (player.getTokens() < betAmount) {
            throw new GameRuleException("NOT_ENOUGH_TOKENS_FOR_BET");
        }

        player.setTokens(player.getTokens() - betAmount);
        return playerRepository.save(player);
    }

    @Transactional
    public Player updateTokens(Player player, int rewardAmount) {
        player.setTokens(player.getTokens() + rewardAmount);

        return playerRepository.save(player);
    }

    @Transactional
    public Player resetTokens(Player player, int initialTokens) {
        player.setTokens(initialTokens);

        return playerRepository.save(player);
    }
}
