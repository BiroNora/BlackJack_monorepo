package com.blackjack.blackjack.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class DealerHandMasked implements Hand, Serializable {
    private List<Card> hand;
    private int sum;
    private boolean canInsure;
    private WinnerState natural21State;

    public DealerHandMasked() {
    }

    public DealerHandMasked(List<Card> hand, int sum, boolean canInsure, WinnerState natural21State) {
        this.hand = hand;
        this.sum = sum;
        this.canInsure = canInsure;
        this.natural21State = natural21State;
    }

    public static DealerHandMasked createEmptyHand() {
        DealerHandMasked maskedHand = new DealerHandMasked();
        maskedHand.setHand(new ArrayList<>());
        maskedHand.setSum(0);
        maskedHand.setCanInsure(false);
        maskedHand.setNatural21State(WinnerState.NONE);
        return maskedHand;
    }
}
