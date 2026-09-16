package keifer.persistence.model;

import keifer.service.model.CardCondition;
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
    private Integer groupId;

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
    private Integer quantity;

    private String url;

    private String productConditionId;

    private Double marketPrice;

    @ManyToOne()
    @JoinColumn(name = "deck_entity_id")
    private DeckEntity deckEntity;

    /**
     * Names this card the way the screens do, for logs about it.
     *
     * A card is only identified by all four of these: two printings of one name price
     * differently, and so do two conditions of one printing. A log line carrying only the
     * product-condition id behind them names nothing anyone can go and look at.
     */
    public String describe() {
        return describe(name, version, String.valueOf(cardCondition), isFoil);
    }

    /** The same, for a card being saved that has no entity yet. */
    public static String describe(String name, String set, String condition, Boolean isFoil) {
        return name + " (" + set + ", " + condition + (Boolean.TRUE.equals(isFoil) ? ", foil" : "") + ")";
    }

}
