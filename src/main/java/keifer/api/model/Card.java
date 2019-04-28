package keifer.api.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Card {

    private Long id;

    private String name;

    private String version;

    private Boolean isFoil;

    private CardCondition cardCondition;

    private Double purchasePrice;

    private Double value;

    private Integer quantity;

}
