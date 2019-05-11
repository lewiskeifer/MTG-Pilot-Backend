package keifer.api.model;

import lombok.*;

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
