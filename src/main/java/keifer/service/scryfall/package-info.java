/**
 * Everything that talks to Scryfall.
 *
 * Kept in its own package, and every type in it named for it, so there is never a question about
 * which price source a class belongs to. The TCGplayer client and its models stay where they
 * were in keifer.service: the two overlap in what they answer - the market price of a card - but
 * agree on nothing else. TCGplayer prices a SKU, which is a printing in a named condition.
 * Scryfall prices a printing, near mint, and knows it by name and set rather than by any id this
 * application stores.
 *
 * Nothing here imports from the TCGplayer side, or the other way about.
 */
package keifer.service.scryfall;
