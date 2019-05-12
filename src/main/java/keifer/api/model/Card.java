package keifer.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Card {

    private Long id;

    private String name;

    private String version;

    private Boolean isFoil;

    private CardCondition cardCondition;

    private Double purchasePrice;

    private Integer quantity;

    private Double marketPrice;

}
