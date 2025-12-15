package com.blackjack.blackjack.factory;

import com.blackjack.blackjack.model.PlayerHand;
import org.springframework.stereotype.Component;

@Component
public class DefaultPlayerHandFactory implements PlayerHandFactory {
    @Override
    public PlayerHand createEmptyHand() {
        return PlayerHand.createEmptyHand();
    }
}
