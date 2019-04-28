package keifer.service;

import keifer.api.model.Deck;
import keifer.converter.DeckConverter;
import keifer.persistence.DeckRepository;
import keifer.persistence.model.DeckEntity;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeckServiceImpl implements DeckService {

    private DeckRepository deckRepository;
    private DeckConverter deckConverter;

    public DeckServiceImpl(@NonNull DeckRepository deckRepository, @NonNull DeckConverter deckConverter) {
        this.deckRepository = deckRepository;
        this.deckConverter = deckConverter;
    }

    @Override
    public List<Deck> getDecks() {

        return deckRepository.findAll().stream().map(deckConverter::convert).collect(Collectors.toList());
    }

    @Override
    public Deck getDeck(Long deckId) {

        DeckEntity deckEntity = deckRepository.findOneById(deckId);
        if (deckEntity == null) {
            throw new Error("Deck with id: " + deckId + " not found.");
        }

        return deckConverter.convert(deckEntity);
    }

}
