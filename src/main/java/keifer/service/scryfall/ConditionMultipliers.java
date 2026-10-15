package keifer.service.scryfall;

import keifer.service.model.CardCondition;

import java.util.List;

public interface ConditionMultipliers {

    /** What a card in this condition and finish is worth against a near mint one. */
    double multiplierFor(CardCondition condition, boolean foil);

    /**
     * Measures the multipliers afresh from the prices currently held against Scryfall's, and
     * stores what it finds.
     *
     * Only worth anything while the stored prices are still TCGplayer's, since that is the half
     * of the comparison that knows about conditions. Returns what it worked out, measured or
     * fallen back, so a caller can show its workings.
     */
    List<MeasuredMultiplier> calibrate();

    /**
     * Measures, but only if nothing has been measured yet.
     *
     * The comparison needs the TCGplayer prices still held against the cards, and the first
     * Scryfall refresh overwrites them. Running this from the refresh, before it prices
     * anything, is the last moment the evidence exists.
     */
    void calibrateIfUnmeasured();

    /** The current figures and where each one came from. */
    List<MeasuredMultiplier> current();

}
