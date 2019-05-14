package keifer.converter;

import keifer.api.model.Card;
import keifer.persistence.model.CardEntity;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CardConverter {

    public Card convert(CardEntity source) {

        return Card.builder()
                .id(source.getId())
                .name(source.getName())
                .version(source.getVersion())
                .isFoil(source.getIsFoil())
                .cardCondition(source.getCardCondition())
                .purchasePrice(source.getPurchasePrice())
                .quantity(source.getQuantity())
                .marketPrice(source.getMarketPrice())
                .build();
    }

}
