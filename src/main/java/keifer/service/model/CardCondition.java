package keifer.service.model;

public enum CardCondition {

    NEAR_MINT("Near Mint"),
    LIGHT_PLAY("Lightly Played"),
    MODERATE_PLAY("Moderately Played"),
    HEAVY_PLAY("Heavily Played"),
    DAMAGED("Damaged");

    private String text;

    CardCondition(String text) {
        this.text = text;
    }

    public String getText() {
        return this.text;
    }

    public static CardCondition fromString(String text) {
        for (CardCondition c : CardCondition.values()) {
            if (c.text.equalsIgnoreCase(text)) {
                return c;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        switch (this) {
            case NEAR_MINT:
                return "Near Mint";
            case LIGHT_PLAY:
                return "Lightly Played";
            case MODERATE_PLAY:
                return "Moderately Played";
            case HEAVY_PLAY:
                return "Heavily Played";
            case DAMAGED:
                return "Damaged";
            default:
                throw new Error("Invalid card condition.");
        }
    }
}
