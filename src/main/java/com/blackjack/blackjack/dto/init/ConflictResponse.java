package com.blackjack.blackjack.dto.init;

import java.util.UUID;

public record ConflictResponse(String status,
                               Object game_state,
                               ConflictData conflict_data
) {
    public record ConflictData(
        Balance existing_user,
        Balance current_session,
        UUID target_user_id
    ) {
    }

    public record Balance(
        int balance
    ) {
    }
}
