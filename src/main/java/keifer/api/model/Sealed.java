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
public class Sealed {

    private Long id;

    private String name;

    private Double purchasePrice;

    private Integer quantity;

    private String url;

    private Double marketPrice;

    /** As on a card: one product's price at the start of each range, keyed by day count. */
    private Map<String, Double> baselinePrices;

}
