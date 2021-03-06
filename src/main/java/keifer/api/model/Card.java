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

}
