package keifer.service.model;

public enum DeckFormat {

    STANDARD("Standard"),
    MODERN("Modern"),
    LEGACY("Legacy"),
    VINTAGE("Vintage"),
    COMMANDER("Commander"),
    CASUAL("Casual");

    private String text;

    DeckFormat(String text) {
        this.text = text;
    }

    public String getText() {
        return this.text;
    }

    public static DeckFormat fromString(String text) {
        for (DeckFormat d : DeckFormat.values()) {
            if (d.text.equalsIgnoreCase(text)) {
                return d;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        switch (this) {
            case STANDARD:
                return "Standard";
            case MODERN:
                return "Modern";
            case LEGACY:
                return "Legacy";
            case VINTAGE:
                return "Vintage";
            case COMMANDER:
                return "Commander";
            case CASUAL:
                return "Casual";
            default:
                throw new Error("Invalid deck format.");
        }
    }
}
