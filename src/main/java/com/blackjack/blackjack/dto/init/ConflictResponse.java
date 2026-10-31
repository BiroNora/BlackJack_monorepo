package com.blackjack.blackjack.dto.init;

public record ConflictResponse(String status,
                               Object game_state,
                               ConflictData conflict_data
) {
    public record ConflictData(
        Balance existing_user,
        Balance current_session
    ) {
    }

    public record Balance(
        int balance
    ) {
    }
}
