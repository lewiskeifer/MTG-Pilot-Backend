package keifer.converter;

import keifer.api.model.Card;
import keifer.persistence.VersionRepository;
import keifer.persistence.model.CardEntity;
import keifer.service.model.CardCondition;
import lombok.NonNull;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.Map;

@Configuration
public class CardConverter {

    private final VersionRepository versionRepository;

    public CardConverter(@NonNull VersionRepository versionRepository) {
        this.versionRepository = versionRepository;
    }

    public Card convert(CardEntity source) {
        return convert(source, Collections.emptyMap());
    }

    /**
     * Converts a card and hangs its recorded prices off it.
     *
     * The baselines are looked up by product-condition id, which stays inside the server: one
     * price series is shared by every copy of a printing, in whatever deck and whoever's.
     */
    public Card convert(CardEntity source, Map<String, Map<String, Double>> baselines) {

        return Card.builder()
                .baselinePrices(baselines.get(source.getProductConditionId()))
                .id(source.getId())
                .groupId(source.getGroupId())
                .name(source.getName())
                .set(source.getVersion())
                .abbreviation(versionRepository.findTopByGroupId(source.getGroupId()).getAbbreviation())
                .isFoil(source.getIsFoil())
                .cardCondition(source.getCardCondition().toString())
                .purchasePrice(source.getPurchasePrice())
                .quantity(source.getQuantity())
                .url(source.getUrl())
                .marketPrice(source.getMarketPrice())
                .build();
    }

    public CardEntity convert(Card source) {

        return CardEntity.builder()
                .name(source.getName())
                .version(source.getSet())
                .isFoil(source.getIsFoil())
                .cardCondition(CardCondition.fromString(source.getCardCondition()))
                .purchasePrice(source.getPurchasePrice())
                .quantity(source.getQuantity())
                .url(source.getUrl())
                .marketPrice(source.getMarketPrice())
                .build();
    }

}
