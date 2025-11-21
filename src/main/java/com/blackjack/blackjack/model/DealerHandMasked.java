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
    private int nat21;

    public DealerHandMasked() {
    }

    public DealerHandMasked(List<Card> hand, int sum, boolean canInsure, int nat21) {
        this.hand = hand;
        this.sum = sum;
        this.canInsure = canInsure;
        this.nat21 = nat21;
    }

    public static DealerHandMasked createEmptyHand() {
        DealerHandMasked maskedHand = new DealerHandMasked();
        maskedHand.setHand(new ArrayList<>());
        maskedHand.setSum(0);
        maskedHand.setCanInsure(false);
        maskedHand.setNat21(0);
        return maskedHand;
    }
}
