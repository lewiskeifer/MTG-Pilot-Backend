package keifer.service.scryfall;

import keifer.service.model.CardCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One condition's multiplier and the standing behind it.
 *
 * `measured` says whether the figure came from the collection or from the fallback convention,
 * which is the difference between a number with evidence and a number with an opinion. Both are
 * reported rather than only the one in use, so a thin sample is visible instead of implied.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasuredMultiplier {

    private CardCondition condition;

    private boolean foil;

    private double multiplier;

    /** How many held cards the figure was drawn from. Zero where it was not measured. */
    private int sampleSize;

    private boolean measured;

    /** Why it was not measured, where it was not. */
    private String note;

}
