package keifer.converter;

import keifer.api.model.Card;
import keifer.persistence.model.CardEntity;
import keifer.service.model.CardCondition;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CardConverter {

    public Card convert(CardEntity source) {

        return Card.builder()
                .id(source.getId())
                .groupId(source.getGroupId())
                .name(source.getName())
                .version(source.getVersion())
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
                .version(source.getVersion())
                .isFoil(source.getIsFoil())
                .cardCondition(CardCondition.fromString(source.getCardCondition()))
                .purchasePrice(source.getPurchasePrice())
                .quantity(source.getQuantity())
                .url(source.getUrl())
                .marketPrice(source.getMarketPrice())
                .build();
    }

}
