package keifer.api.model;

public enum CardCondition {

    NEAR_MINT,
    LIGHT_PLAY,
    MODERATE_PLAY,
    HEAVY_PLAY,
    DAMAGED;

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
