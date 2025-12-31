package com.blackjack.blackjack.dto.bet;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record BetRequest(@JsonProperty("clientId")
                         UUID clientId,
                         @JsonProperty("bet")
                         int bet, @JsonProperty("idempotencyKey") UUID idempotencyKey) {
    public UUID getClientId() {
        return clientId;
    }

    public int getBet() {
        return bet;
    }
}
