package keifer.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false)
    private Boolean isFoil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardCondition cardCondition;

    @Column(nullable = false)
    private Double purchasePrice;

    @Column(nullable = false)
    private Double value;

    @Column(nullable = false)
    private Integer quantity;

}
