package keifer.service;

import keifer.converter.CardConverter;
import keifer.converter.DeckSnapshotConverter;
import keifer.persistence.CardRepository;
import keifer.persistence.DeckRepository;
import keifer.persistence.UserRepository;
import keifer.persistence.model.CardEntity;
import keifer.persistence.model.DeckEntity;
import keifer.persistence.model.DeckSnapshotEntity;
import keifer.persistence.model.UserEntity;
import keifer.service.model.CardCondition;
import keifer.service.model.DeckFormat;
import lombok.NonNull;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class DataMigrationServiceImpl implements DataMigrationService {

    private final UserRepository userRepository;
    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final TcgService tcgService;
    private final DeckSnapshotConverter deckSnapshotConverter;
    private final CardConverter cardConverter;
    private List<List<String>> data;

    // TODO move to configs
    private String textFolderPath = "C:\\Users\\Keifer\\Desktop\\MTG\\main\\input";
    private String jsonPath = "C:\\Users\\Keifer\\Desktop\\JSON.json";

    public DataMigrationServiceImpl(@NonNull UserRepository userRepository,
                                    @NonNull DeckRepository deckRepository,
                                    @NonNull CardRepository cardRepository,
                                    @NonNull TcgService tcgService,
                                    @NonNull DeckSnapshotConverter deckSnapshotConverter,
                                    @NonNull CardConverter cardConverter) {
        this.userRepository = userRepository;
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.tcgService = tcgService;
        this.deckSnapshotConverter = deckSnapshotConverter;
        this.cardConverter = cardConverter;
        this.data = new ArrayList<>();
    }

    @Override
    public void migrateTextData() {

        readFolder(textFolderPath);

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

            double aggregatePurchasePrice = 0;
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

                Map<String, String> returnData = tcgService.fetchProductConditionIdAndUrl(cardConverter.convert(cardEntity));
                cardEntity.setProductConditionId(returnData.get("productConditionId"));
                cardEntity.setUrl(returnData.get("image"));
                cardEntity.setMarketPrice(tcgService.fetchMarketPrice(cardEntity.getProductConditionId()));

                aggregatePurchasePrice += cardEntity.getPurchasePrice();
                aggregateValue += (cardEntity.getMarketPrice() * cardEntity.getQuantity());

                deckEntity.getCardEntities().add(cardEntity);
            }

            deckEntity.getDeckSnapshotEntities().add(DeckSnapshotEntity.builder()
                    .purchasePrice(aggregatePurchasePrice)
                    .value(aggregateValue)
                    .timestamp(LocalDateTime.now())
                    .deckEntity(deckEntity)
                    .build());
            deckRepository.save(deckEntity);

            deckNumber++;
        }
    }

    @Override
    public void migrateJsonData() {

        Object obj = null;
        try {
            obj = new JSONParser().parse(new FileReader(ResourceUtils.getFile("classpath:JSON.json")));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        JSONArray ja = (JSONArray) obj;
        ja.forEach(this::parseJson);

        List<CardEntity> cardEntities = cardRepository.findAll();
        for (CardEntity cardEntity : cardEntities) {
            Map<String, String> results = tcgService.fetchProductConditionIdAndUrl(cardConverter.convert(cardEntity));
            cardEntity.setProductConditionId(results.get("productConditionId"));
            cardRepository.save(cardEntity);
        }

    }

    private void parseJson(Object jsonObject) {

        JSONObject jo = (JSONObject) jsonObject;
        if ((long) jo.get("id") == 0L) {
            return;
        }

        DeckEntity deckEntity = DeckEntity.builder()
                .name((String) jo.get("name"))
                .deckFormat(DeckFormat.fromString((String) jo.get("format")))
                .build();

        JSONArray jsonArray = (JSONArray) jo.get("cards");

        Iterator it = jsonArray.iterator();
        while (it.hasNext()) {
            JSONObject jsonObject1 = (JSONObject) it.next();

            Double marketPrice = 0.0;
            try {
                marketPrice = ((Long) (jsonObject1).get("marketPrice")).doubleValue();
            } catch (ClassCastException e) {
                marketPrice = (Double) (jsonObject1).get("marketPrice");
            }

            Double purchasePrice = 0.0;
            try {
                purchasePrice = ((Long) (jsonObject1).get("purchasePrice")).doubleValue();
            } catch (ClassCastException e) {
                purchasePrice = (Double) (jsonObject1).get("purchasePrice");
            }

            CardEntity cardEntity = CardEntity.builder()
                    .marketPrice(marketPrice)
                    .quantity(((Long) (jsonObject1).get("quantity")).intValue())
                    .isFoil((Boolean) (jsonObject1).get("isFoil"))
                    .cardCondition(CardCondition.fromString((String) (jsonObject1).get("cardCondition")))
                    .name((String) (jsonObject1).get("name"))
                    .purchasePrice(purchasePrice)
                    .version((String) (jsonObject1).get("version"))
                    .url((String) (jsonObject1).get("url"))
                    .productConditionId((String) (jsonObject1).get("productConditionId"))
                    .deckEntity(deckEntity)
                    .build();

            deckEntity.getCardEntities().add(cardEntity);
        }

        JSONArray jsonArray2 = (JSONArray) jo.get("deckSnapshots");

        Iterator it2 = jsonArray2.iterator();

        while (it2.hasNext()) {

            JSONObject jsonObject2 = (JSONObject) it2.next();

            Double purchasePrice = 0.0;
            try {
                purchasePrice = ((Long) (jsonObject2).get("purchasePrice")).doubleValue();
            } catch (ClassCastException e) {
                purchasePrice = (Double) (jsonObject2).get("purchasePrice");
            }

            Double value = 0.0;
            try {
                value = ((Long) (jsonObject2).get("value")).doubleValue();
            } catch (ClassCastException e) {
                value = (Double) (jsonObject2).get("value");
            }

            DeckSnapshotEntity deckSnapshotEntity = DeckSnapshotEntity.builder()
                    .purchasePrice(purchasePrice)
                    .value(value)
                    .timestamp(LocalDateTime.parse((String) (jsonObject2).get("timestamp"), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .deckEntity(deckEntity)
                    .build();

            deckEntity.getDeckSnapshotEntities().add(deckSnapshotEntity);
        }

        UserEntity userEntity = userRepository.findOneById(1L);

        deckEntity.setUserEntity(userEntity);

        userEntity.getDeckEntities().add(deckEntity);
        userRepository.save(userEntity);
    }

    @Override
    public void migrateSqlData() {
        List<DeckEntity> deckEntities = deckRepository.findAll();

        for (DeckEntity deckEntity : deckEntities) {
            if (deckEntity.getName().equals("Binder Colorless")) {
                continue;
            }
            List<DeckSnapshotEntity> deckSnapshotEntities = new ArrayList<>();
            for (int i = 3; i < 6; ++i) {
                deckSnapshotEntities.add(deckEntity.getDeckSnapshotEntities().get(i));
            }
            deckEntity.setDeckSnapshotEntities(deckSnapshotEntities);
            deckRepository.save(deckEntity);
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
