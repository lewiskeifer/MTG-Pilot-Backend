package keifer.service.model;

/**
 * Which catalogue a recorded price belongs to.
 *
 * Cards are keyed by TCG product-condition id and sealed products by product id. The two are
 * separate id spaces that can hand out the same number, so every price row and every lookup
 * carries this alongside the key.
 */
public enum PriceKind {

    CARD,
    SEALED

}
