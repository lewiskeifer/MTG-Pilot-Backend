package keifer.service;

import keifer.api.model.CardCondition;
import keifer.api.model.Format;
import keifer.persistence.DeckRepository;
import keifer.persistence.model.CardEntity;
import keifer.persistence.model.DeckEntity;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class DataMigrationServiceImpl implements DataMigrationService {

    private final DeckRepository deckRepository;
    private List<List<String>> data;

    // TODO move to configs
    private String folderPath = "C:\\Users\\Keifer\\Desktop\\MTG\\main\\input";

    public DataMigrationServiceImpl(@NonNull DeckRepository deckRepository) {
        this.deckRepository = deckRepository;
        this.data = new ArrayList<>();
    }

    @Override
    public void migrateData() {

        readFolder(folderPath);

        int deckNumber = 1;
        for (List<String> deck : data) {

            String deckName = "error";
            Format deckFormat = Format.COMMANDER;

            // "Hard-coded" to match current inventory, will break if anything changes
            switch (deckNumber) {
                case 1:
                    deckName = "Mono Blue";
                    deckFormat = Format.CASUAL;
                    break;
                case 2:
                    deckName = "Affinity";
                    deckFormat = Format.LEGACY;
                    break;
                case 3:
                    deckName = "Esper";
                    deckFormat = Format.CASUAL;
                    break;
                case 4:
                    deckName = "Jund";
                    deckFormat = Format.MODERN;
                    break;
                case 5:
                    deckName = "Azorious";
                    deckFormat = Format.CASUAL;
                    break;
                case 6:
                    deckName = "Bant";
                    deckFormat = Format.CASUAL;
                    break;
                case 7:
                    deckName = "Cats";
                    deckFormat = Format.CASUAL;
                    break;
                case 8:
                    deckName = "Zombies";
                    deckFormat = Format.CASUAL;
                    break;
                case 9:
                    deckName = "Aura";
                    deckFormat = Format.MODERN;
                    break;
                case 10:
                    deckName = "Izzet";
                    deckFormat = Format.CASUAL;
                    break;
                case 11:
                    deckName = "B0";
                    deckFormat = Format.CASUAL;
                    break;
                case 12:
                    deckName = "B1";
                    deckFormat = Format.CASUAL;
                    break;
                case 13:
                    deckName = "B2";
                    deckFormat = Format.CASUAL;
                    break;
                case 14:
                    deckName = "B3";
                    deckFormat = Format.CASUAL;
                    break;
                case 15:
                    deckName = "B4";
                    deckFormat = Format.CASUAL;
                    break;
                case 16:
                    deckName = "B5";
                    deckFormat = Format.CASUAL;
                    break;
                case 17:
                    deckName = "B6";
                    deckFormat = Format.CASUAL;
                    break;
                case 18:
                    deckName = "B7";
                    deckFormat = Format.CASUAL;
                    break;
                case 19:
                    deckName = "I0";
                    deckFormat = Format.CASUAL;
                    break;
                case 20:
                    deckName = "I1";
                    deckFormat = Format.CASUAL;
                    break;
                case 21:
                    deckName = "I2";
                    deckFormat = Format.CASUAL;
                    break;
                case 22:
                    deckName = "U0";
                    deckFormat = Format.CASUAL;
                    break;
                default:
                    throw new java.lang.Error("Invalid deck number.");
            }

            DeckEntity deckEntity = DeckEntity.builder().name(deckName).format(deckFormat).build();
            deckRepository.save(deckEntity);

            for (String card : deck) {

                String[] values = card.split(",");

                CardEntity cardEntity = CardEntity.builder().name(values[0])
                        .version(values[1].trim())
                        .purchasePrice(Double.valueOf(values[3].trim()))
                        .quantity(Integer.valueOf(values[4].trim()))
                        .value(0.0) //TODO
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

                deckEntity.getCardEntities().add(cardEntity);
                //cardRepository.save(cardEntity);
            }

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
