package keifer.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

}
