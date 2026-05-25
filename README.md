# Chieseled Enchanting

Fabric mod for Minecraft 1.21.10 that expands enchanting-table behavior while keeping regular bookshelves usable.

## Power rules

Regular bookshelves work like vanilla bookshelves again:

- they must be in one of the normal vanilla bookshelf positions around an enchanting table,
- they must have an unobstructed enchantment-power transmitter gap, and
- they contribute one power each, capped at vanilla power `15`.

Chiseled bookshelves use the same placement and gap rules, but only contribute custom power when they contain at least three enchanted books with stored enchantments.

Chiseled power scales linearly from qualifying enchanted-book count:

| Qualifying enchanted books | Chiseled power |
| ---: | ---: |
| 3 | 1 |
| 45 | 15 |
| 90+ | 30 |

Combined room power is capped at `30`. Regular bookshelves alone cannot push enchanting power above `15`; chiseled bookshelf power is what allows the room to go beyond vanilla.

## Particles

Enchanting-table particles use the same placement and gap rules as power.

| Provider | Particle rate |
| --- | --- |
| Regular bookshelf | vanilla `1/16` |
| Chiseled shelf with 1 enchanted book | `1/48` |
| Chiseled shelf with 2 enchanted books | `2/48` |
| Chiseled shelf with 3 enchanted books | `3/48`, equal to vanilla `1/16` |
| Chiseled shelf with 4 enchanted books | `4/48` |
| Chiseled shelf with 5 enchanted books | `5/48` |
| Chiseled shelf with 6 enchanted books | `6/48` |

The client receives a compact integer particle weight from the server through the chiseled-bookshelf block entity update packet.

## Over-cap shelf libraries

A single valid chiseled bookshelf can unlock one level above vanilla maximum for an enchantment when that same shelf contains every vanilla level of that enchantment.

Example: a valid shelf containing `Respiration I`, `Respiration II`, and `Respiration III` unlocks occasional `Respiration IV` table offers.

The unlock is not deterministic. Vanilla chooses enchantments first. If an unlocked enchantment is selected at its vanilla maximum level, it has a `1/4` chance to promote by one level. This keeps the vanilla enchantment distribution from being overloaded with over-cap entries.

## High-power candidate pool

The visible offer level can scale up to the extended cap, but vanilla candidate selection is capped at level 30. This prevents finite-band enchantments such as Protection from disappearing at very high displayed power. Over-cap levels are applied only through the shelf-library rule after vanilla has chosen an enchantment.

## Second clue reveal

If the sum of stored enchantment levels on all valid chiseled shelves is greater than `135`, the enchanting-table tooltip reveals a second enchantment clue when that offer contains at least two generated enchantments.

The second clue is only UI information. It does not change the actual enchantment result.

## Anvil behavior

The anvil preserves and transfers table-created over-cap enchantments. For example, `Respiration IV` can be applied from a book to a helmet without being clamped back to `Respiration III`.

The anvil does not allow two vanilla maximum-level books to create an over-cap level directly. Two `Respiration III` books still produce `Respiration III` unless one input already contains `Respiration IV`.

## Build

```bash
./gradlew build
```

The mod jar will be written to `build/libs/`.

## License

CC0-1.0.
