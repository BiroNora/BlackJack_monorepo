package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealerHandMasked implements Hand, Serializable {
    private List<Card> hand;
    private int sum;
    private boolean canInsure;

    @JsonProperty("nat_21")
    private int nat21;

    public static DealerHandMasked createEmptyHand() {
        DealerHandMasked maskedHand = new DealerHandMasked();
        maskedHand.setHand(new ArrayList<>());
        maskedHand.setSum(0);
        maskedHand.setCanInsure(false);
        maskedHand.setNat21(0);
        return maskedHand;
    }
}
