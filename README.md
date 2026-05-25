# Chieseled Enchanting

Fabric mod for Minecraft 1.21.10. Normal bookshelves no longer power enchanting tables. Instead, power comes from nearby chiseled bookshelves that contain enchanted books.

## Power rules

A chiseled bookshelf is considered valid when it is:

- in one of the normal vanilla bookshelf positions around an enchanting table,
- connected through an unobstructed enchantment-power transmitter gap, and
- holding at least three enchanted books with stored enchantments.

Power scales linearly from qualifying enchanted-book count:

| Qualifying enchanted books | Power |
| ---: | ---: |
| 3 | 1 |
| 45 | 15 |
| 90+ | 30 |

Normal bookshelves contribute `0`.

## Particles

Enchanting-table particles use the same chiseled-bookshelf placement and gap rules. Frequency is based on the number of enchanted books in each shelf:

| Enchanted books in shelf | Particle rate | Power from shelf |
| ---: | --- | ---: |
| 1 | `1/48` | 0 |
| 2 | `2/48` | 0 |
| 3 | `3/48`, equal to vanilla `1/16` | 1 |
| 4 | `4/48` | 1 |
| 5 | `5/48` | 1 |
| 6 | `6/48` | 1 |

The client receives a compact integer particle weight from the server through the chiseled-bookshelf block entity update packet.

## Over-cap shelf libraries

A single valid chiseled bookshelf can unlock one level above vanilla maximum for an enchantment when that same shelf contains every vanilla level of that enchantment.

Example: a valid shelf containing `Respiration I`, `Respiration II`, and `Respiration III` unlocks occasional `Respiration IV` table offers.

The unlock is not deterministic. Vanilla chooses enchantments first. If an unlocked enchantment is selected at its vanilla maximum level, it has a `1/4` chance to promote by one level. This keeps the vanilla enchantment distribution from being overloaded with over-cap entries.

## High-power candidate pool

The visible offer level can scale up to the extended cap, but vanilla candidate selection is capped at level 30. This prevents finite-band enchantments such as Protection from disappearing at very high displayed power. Over-cap levels are applied only through the shelf-library rule after vanilla has chosen an enchantment.

## Second clue reveal

If the sum of stored enchantment levels on all valid shelves is greater than `135`, the enchanting-table tooltip reveals a second enchantment clue when that offer contains at least two generated enchantments.

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
