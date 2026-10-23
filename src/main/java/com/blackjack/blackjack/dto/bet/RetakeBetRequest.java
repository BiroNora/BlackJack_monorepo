package com.blackjack.blackjack.dto.bet;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record RetakeBetRequest(@JsonProperty("clientId")
                               UUID clientId,
                               @JsonProperty("idempotencyKey")
                               UUID idempotencyKey) {
}
