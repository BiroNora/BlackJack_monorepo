package com.blackjack.blackjack.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ActionRequest {
    private UUID clientId;
}
