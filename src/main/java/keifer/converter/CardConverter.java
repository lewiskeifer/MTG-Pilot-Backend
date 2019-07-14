package keifer.converter;

import keifer.api.model.Card;
import keifer.persistence.VersionRepository;
import keifer.persistence.model.CardEntity;
import keifer.service.model.CardCondition;
import lombok.NonNull;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CardConverter {

    private final VersionRepository versionRepository;

    public CardConverter(@NonNull VersionRepository versionRepository) {
        this.versionRepository = versionRepository;
    }

    public Card convert(CardEntity source) {

        return Card.builder()
                .id(source.getId())
                .groupId(source.getGroupId())
                .name(source.getName())
                .set(source.getVersion())
                .abbreviation(versionRepository.findOneByGroupId(source.getGroupId()).getAbbreviation())
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
