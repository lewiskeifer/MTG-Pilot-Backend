package keifer.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Card {

    private Long id;

    private Integer groupId;

    private String name;

    private String set;

    private String abbreviation;

    private Boolean isFoil;

    private String cardCondition;

    private Double purchasePrice;

    private Integer quantity;

    private String url;

    private Double marketPrice;

    /*
     * What one copy was worth at the start of each range the dashboard offers, keyed by the same
     * day counts its buttons send and "0" for all time. A window with no reading behind it is
     * absent rather than zero: the card cannot be ranked over a period nobody priced it in.
     *
     * Only filled in where a screen plots it - the deck list. Elsewhere it is null and costs
     * nothing to carry.
     */
    private Map<String, Double> baselinePrices;

}
