package keifer.service.model;

public enum Format {

    STANDARD,
    MODERN,
    LEGACY,
    VINTAGE,
    COMMANDER,
    CASUAL;

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
                throw new Error("Invalid format.");
        }
    }
}
