package com.blackjack.blackjack.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

@Data
public class ActionRequest {
    @JsonProperty("clientId")
    private UUID clientId;

    @JsonProperty("idempotencyKey")
    private UUID idempotencyKey;
}
