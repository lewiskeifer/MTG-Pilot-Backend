package keifer.controller;

import io.swagger.annotations.Api;
import keifer.service.scryfall.ConditionMultipliers;
import keifer.service.scryfall.MeasuredMultiplier;
import lombok.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * What a played card is priced at against a near mint one, and where those figures came from.
 *
 * Its own path rather than a branch of the users one: these are a property of the pricing, not
 * of anybody's collection, and "condition-multipliers" sitting beside "{userId}" would have to
 * be told apart from a user id on every request.
 */
@Api
@RequestMapping("/manager/pricing")
@RestController
public class PricingController {

    private final ConditionMultipliers conditionMultipliers;

    public PricingController(@NonNull ConditionMultipliers conditionMultipliers) {
        this.conditionMultipliers = conditionMultipliers;
    }

    /** Every condition, its multiplier, and whether it was measured or assumed. */
    @GetMapping("/condition-multipliers")
    public List<MeasuredMultiplier> getConditionMultipliers() {
        return conditionMultipliers.current();
    }

    /*
     * Measures again, for a collection that has grown enough to say more than it could the first
     * time. Worth less with every passing month: the comparison reads the TCGplayer prices held
     * against the cards, and the nightly refresh replaces those with Scryfall's the first time
     * it runs. After that there is nothing left to measure and this reports the convention.
     */
    @PostMapping("/condition-multipliers/measure")
    public List<MeasuredMultiplier> measureConditionMultipliers() {
        return conditionMultipliers.calibrate();
    }

}
