package keifer.service;

import keifer.converter.CardConverter;
import keifer.persistence.DeckRepository;
import keifer.persistence.model.CardEntity;
import keifer.persistence.model.DeckEntity;
import keifer.persistence.model.DeckSnapshotEntity;
import keifer.service.model.CardCondition;
import keifer.service.model.DeckFormat;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class DataMigrationServiceImpl implements DataMigrationService {

    private final DeckRepository deckRepository;
    private final TcgService tcgService;
    private final CardConverter cardConverter;
    private List<List<String>> data;

    // TODO move to configs
    private String folderPath = "C:\\Users\\Keifer\\Desktop\\MTG\\main\\input";

    public DataMigrationServiceImpl(@NonNull DeckRepository deckRepository,
                                    @NonNull TcgService tcgService,
                                    @NonNull CardConverter cardConverter) {
        this.deckRepository = deckRepository;
        this.tcgService = tcgService;
        this.cardConverter = cardConverter;
        this.data = new ArrayList<>();
    }

    @Override
    public void migrateData() {

        readFolder(folderPath);

        int deckNumber = 1;
        for (List<String> deck : data) {

            String deckName = "error";
            DeckFormat deckFormat = DeckFormat.COMMANDER;

            // "Hard-coded" to match current inventory, will break if anything changes
            switch (deckNumber) {
                case 1:
                    deckName = "Mono Blue";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 2:
                    deckName = "Affinity";
                    deckFormat = DeckFormat.LEGACY;
                    break;
                case 3:
                    deckName = "Esper";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 4:
                    deckName = "Jund";
                    deckFormat = DeckFormat.MODERN;
                    break;
                case 5:
                    deckName = "Azorious";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 6:
                    deckName = "Bant";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 7:
                    deckName = "Cats";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 8:
                    deckName = "Zombies";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 9:
                    deckName = "Aura";
                    deckFormat = DeckFormat.MODERN;
                    break;
                case 10:
                    deckName = "Izzet";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 11:
                    deckName = "Binder Planeswalkers";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 12:
                    deckName = "Binder Colored";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 13:
                    deckName = "Binder Blue";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 14:
                    deckName = "Binder Black";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 15:
                    deckName = "Binder Red";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 16:
                    deckName = "Binder Green";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 17:
                    deckName = "Binder White";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 18:
                    deckName = "Binder Artifacts/Lands";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 19:
                    deckName = "Investments";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 20:
                    deckName = "Investments Inventions";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 21:
                    deckName = "Investments Toppers";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                case 22:
                    deckName = "Unsorted";
                    deckFormat = DeckFormat.CASUAL;
                    break;
                default:
                    throw new java.lang.Error("Invalid deck number.");
            }

            DeckEntity deckEntity = DeckEntity.builder().name(deckName).deckFormat(deckFormat).build();
            deckRepository.save(deckEntity);

            double aggregateValue = 0;

            for (String card : deck) {

                String[] values = card.split(",");

                CardEntity cardEntity = CardEntity.builder().name(values[0])
                        .version(values[1].trim())
                        .purchasePrice(Double.valueOf(values[3].trim()))
                        .quantity(Integer.valueOf(values[4].trim()))
                        .deckEntity(deckEntity)
                        .build();

                switch (values[2].trim()) {
                    case "nm":
                        cardEntity.setCardCondition(CardCondition.NEAR_MINT);
                        cardEntity.setIsFoil(false);
                        break;
                    case "lp":
                        cardEntity.setCardCondition(CardCondition.LIGHT_PLAY);
                        cardEntity.setIsFoil(false);
                        break;
                    case "mp":
                        cardEntity.setCardCondition(CardCondition.MODERATE_PLAY);
                        cardEntity.setIsFoil(false);
                        break;
                    case "nm f":
                        cardEntity.setCardCondition(CardCondition.NEAR_MINT);
                        cardEntity.setIsFoil(true);
                        break;
                    case "lp f":
                        cardEntity.setCardCondition(CardCondition.LIGHT_PLAY);
                        cardEntity.setIsFoil(true);
                        break;
                    case "mp f":
                        cardEntity.setCardCondition(CardCondition.MODERATE_PLAY);
                        cardEntity.setIsFoil(true);
                        break;
                    default:
                        throw new java.lang.Error("Invalid condition.");
                }

                cardEntity.setProductConditionId(tcgService.fetchProductConditionId(cardConverter.convert(cardEntity)));
                cardEntity.setMarketPrice(tcgService.fetchMarketPrice(cardEntity.getProductConditionId()));

                aggregateValue += (cardEntity.getMarketPrice() * cardEntity.getQuantity());

                deckEntity.getCardEntities().add(cardEntity);
            }

            deckEntity.getDeckSnapshotEntities().add(DeckSnapshotEntity.builder()
                    .value(aggregateValue)
                    .timestamp(LocalDateTime.now())
                    .deckEntity(deckEntity)
                    .build());
            deckRepository.save(deckEntity);

            deckNumber++;
        }
    }

    private void readFolder(String path) {

        try (Stream<Path> paths = Files.walk(Paths.get(path))) {
            paths.filter(Files::isRegularFile).forEach(this::readFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Do not allow simultaneous writes to "data"
    private synchronized void readFile(Path path) {

        List<String> records = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(path.toFile()));
            String line;
            while ((line = reader.readLine()) != null) {
                records.add(line);
            }
            reader.close();
            data.add(records);
        } catch (Exception e) {
            System.err.format("Exception occurred trying to read '%s'.", path.toString());
            e.printStackTrace();
        }
    }

}
