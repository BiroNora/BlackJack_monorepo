package com.blackjack.blackjack.model;

import java.util.List;

public interface DealerView {
    List<Card> getHand();
    int getSum();
    WinnerState getNatural21Status();
}
