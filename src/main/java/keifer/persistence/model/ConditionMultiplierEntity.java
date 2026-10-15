package keifer.persistence.model;

import keifer.service.model.CardCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;

/**
 * What a card in one condition is worth against a near mint one, measured rather than assumed.
 *
 * Scryfall prices near mint alone, so something has to carry that figure down to the condition
 * actually held. Rather than invent the discount, it is read off the prices already in this
 * database: every held card carries the TCGplayer price for its own condition, and comparing
 * those against Scryfall's near mint price for the same printing gives the real ratio.
 *
 * The sample size is kept alongside, because a multiplier drawn from four damaged cards is a
 * number with no weight behind it and should be read as one.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_condition_multiplier",
        columnNames = {"card_condition", "foil"}))
public class ConditionMultiplierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    // Short lengths and explicit names: this key would not fit a MyISAM index otherwise
    @Enumerated(EnumType.STRING)
    @Column(name = "card_condition", nullable = false, length = 20)
    private CardCondition cardCondition;

    /** Foils wear differently from the same card in plain, so they are measured apart. */
    @Column(name = "foil", nullable = false)
    private Boolean foil;

    @Column(name = "multiplier", nullable = false)
    private Double multiplier;

    /** How many cards the figure was drawn from. */
    @Column(name = "sample_size", nullable = false)
    private Integer sampleSize;

    @Column(name = "calculated_on", nullable = false)
    private LocalDate calculatedOn;

}
