package keifer.service;

import keifer.api.model.Deck;
import keifer.converter.DeckConverter;
import keifer.persistence.DeckRepository;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ManagerServiceImpl implements ManagerService {

    private DeckRepository deckRepository;
    private DeckConverter deckConverter;

    public ManagerServiceImpl(@NonNull DeckRepository deckRepository, @NonNull DeckConverter deckConverter) {
        this.deckRepository = deckRepository;
        this.deckConverter = deckConverter;
    }

    @Override
    public List<Deck> returnData() {

        return deckRepository.findAll().stream().map(deckConverter::convert).collect(Collectors.toList());
    }
}
