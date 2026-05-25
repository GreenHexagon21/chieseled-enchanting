package hex.onlychiseled;

import hex.onlychiseled.access.ChiseledBookshelfEnchantingPowerState;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.Blocks;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChiseledBookshelfBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class ChiseledBookshelfEnchantingPower {
    public static final int REQUIRED_ENCHANTED_BOOKS_PER_POWER_PROVIDER = 3;
    public static final int ENCHANTED_BOOKS_PER_POWER_LEVEL = 3;
    public static final int EXTENDED_MAX_POWER = 30;
    public static final int OVERCAP_LEVEL_BONUS = 1;
    public static final int OVERCAP_PROMOTION_CHANCE_DENOMINATOR = 4;
    public static final int VANILLA_POOL_GENERATION_LEVEL_CAP = 30;
    public static final int SECOND_REVEAL_ENCHANTMENT_LEVEL_SUM_THRESHOLD = 135;

    private ChiseledBookshelfEnchantingPower() {
    }

    /**
     * Cached result for one enchanting-table room scan.
     *
     * <p>Offer generation needs power, second-clue eligibility, and unlocked over-cap enchantments at the same time.
     * Computing them together avoids scanning the same 15 vanilla bookshelf positions multiple times per UI refresh.</p>
     */
    public record LibraryAnalysis(
            int enchantingPower,
            boolean revealSecondEnchantment,
            Set<RegistryEntry<Enchantment>> unlockedOvercapEnchantments
    ) {
    }

    /**
     * Analyzes all valid power providers around an enchanting table in one pass.
     *
     * <p>Regular bookshelves count exactly like vanilla: one unobstructed bookshelf adds one power. Chiseled
     * bookshelves add power only when they contain at least three enchanted books with stored enchantments; every
     * three qualifying enchanted books add one power. Total power is capped at 30.</p>
     */
    public static LibraryAnalysis analyze(World world, BlockPos tablePos) {
        int regularBookshelfPower = 0;
        int qualifyingEnchantedBooks = 0;
        int storedEnchantmentLevelSum = 0;
        Set<RegistryEntry<Enchantment>> unlockedOvercapEnchantments = new HashSet<>();

        for (BlockPos providerOffset : EnchantingTableBlock.POWER_PROVIDER_OFFSETS) {
            if (!canTransmitPower(world, tablePos, providerOffset)) {
                continue;
            }

            BlockPos shelfPos = tablePos.add(providerOffset);
            if (world.getBlockState(shelfPos).isOf(Blocks.BOOKSHELF)) {
                regularBookshelfPower++;
                continue;
            }

            ChiseledBookshelfBlockEntity bookshelf = getChiseledBookshelf(world, shelfPos);
            if (bookshelf == null) {
                continue;
            }

            Iterable<ItemStack> stacks = bookshelf.getHeldStacks();
            int enchantedBookCount = countEnchantedBooksWithEnchantments(stacks);
            if (enchantedBookCount < REQUIRED_ENCHANTED_BOOKS_PER_POWER_PROVIDER) {
                continue;
            }

            qualifyingEnchantedBooks += enchantedBookCount;
            storedEnchantmentLevelSum += sumStoredEnchantmentLevels(stacks);
            collectUnlockedOvercapEnchantments(stacks, unlockedOvercapEnchantments);
        }

        return new LibraryAnalysis(
                calculateTotalPower(regularBookshelfPower, qualifyingEnchantedBooks),
                storedEnchantmentLevelSum > SECOND_REVEAL_ENCHANTMENT_LEVEL_SUM_THRESHOLD,
                Set.copyOf(unlockedOvercapEnchantments)
        );
    }


    private static int calculateTotalPower(int regularBookshelfPower, int qualifyingEnchantedBooks) {
        int vanillaPower = Math.max(0, regularBookshelfPower);
        int chiseledPower = Math.max(0, qualifyingEnchantedBooks) / ENCHANTED_BOOKS_PER_POWER_LEVEL;
        return Math.min(vanillaPower + chiseledPower, EXTENDED_MAX_POWER);
    }

    /**
     * Vanilla's EnchantmentHelper caps bookshelf count at 15. This copy keeps the vanilla formula but raises the cap
     * to this mod's extended power cap. With 90 qualifying books, the bottom offer can reach level 60.
     */
    public static int calculateRequiredExperienceLevel(Random random, int slotIndex, int enchantingPower, ItemStack stack) {
        if (stack.get(DataComponentTypes.ENCHANTABLE) == null) {
            return 0;
        }

        int power = Math.max(0, Math.min(enchantingPower, EXTENDED_MAX_POWER));
        int base = random.nextInt(8) + 1 + (power >> 1) + random.nextInt(power + 1);

        if (slotIndex == 0) {
            return Math.max(base / 3, 1);
        }

        if (slotIndex == 1) {
            return base * 2 / 3 + 1;
        }

        return Math.max(base, power * 2);
    }

    /**
     * Returns true when the block at {@code tablePos + providerOffset} provides enchanting power.
     */
    public static boolean canProvidePower(World world, BlockPos tablePos, BlockPos providerOffset) {
        if (isRegularBookshelfPowerProvider(world, tablePos, providerOffset)) {
            return true;
        }

        ChiseledBookshelfBlockEntity bookshelf = getValidChiseledBookshelf(world, tablePos, providerOffset);
        if (bookshelf == null) {
            return false;
        }

        int enchantedBookCount = countEnchantedBooksWithEnchantments(bookshelf.getHeldStacks());
        if (enchantedBookCount >= REQUIRED_ENCHANTED_BOOKS_PER_POWER_PROVIDER) {
            return true;
        }

        return world.isClient()
                && bookshelf instanceof ChiseledBookshelfEnchantingPowerState syncedState
                && syncedState.chieseled_enchanting$getParticleWeight() >= REQUIRED_ENCHANTED_BOOKS_PER_POWER_PROVIDER;
    }

    /**
     * Returns true for an unobstructed vanilla bookshelf in a vanilla provider position.
     */
    public static boolean isRegularBookshelfPowerProvider(World world, BlockPos tablePos, BlockPos providerOffset) {
        return canTransmitPower(world, tablePos, providerOffset)
                && world.getBlockState(tablePos.add(providerOffset)).isOf(Blocks.BOOKSHELF);
    }


    /**
     * Generates enchantments with unlocked over-cap levels without duplicating those enchantments in the weighted pool.
     *
     * <p>The displayed XP offer can rise above vanilla, but the candidate pool is capped at vanilla level 30 so finite
     * cost-band enchantments such as Protection remain available. Already-selected unlocked max-level enchantments then
     * get a small chance to promote by one level.</p>
     */
    public static List<EnchantmentLevelEntry> generateEnchantmentsWithOvercapPool(
            Random random,
            ItemStack stack,
            int level,
            Stream<RegistryEntry<Enchantment>> possibleEnchantments,
            Set<RegistryEntry<Enchantment>> unlockedOvercapEnchantments
    ) {
        int vanillaCandidatePoolLevel = Math.max(1, Math.min(level, VANILLA_POOL_GENERATION_LEVEL_CAP));
        List<EnchantmentLevelEntry> generatedEnchantments = EnchantmentHelper.generateEnchantments(
                random,
                stack,
                vanillaCandidatePoolLevel,
                possibleEnchantments
        );

        if (generatedEnchantments.isEmpty() || unlockedOvercapEnchantments.isEmpty()) {
            return generatedEnchantments;
        }

        List<EnchantmentLevelEntry> adjustedEnchantments = new ArrayList<>(generatedEnchantments.size());

        for (EnchantmentLevelEntry entry : generatedEnchantments) {
            RegistryEntry<Enchantment> enchantment = entry.enchantment();
            int vanillaMaxLevel = enchantment.value().getMaxLevel();

            if (entry.level() == vanillaMaxLevel
                    && unlockedOvercapEnchantments.contains(enchantment)
                    && random.nextInt(OVERCAP_PROMOTION_CHANCE_DENOMINATOR) == 0) {
                adjustedEnchantments.add(new EnchantmentLevelEntry(
                        enchantment,
                        vanillaMaxLevel + OVERCAP_LEVEL_BONUS
                ));
            } else {
                adjustedEnchantments.add(entry);
            }
        }

        return adjustedEnchantments;
    }

    /**
     * Returns the maximum level the anvil should allow for an enchantment during a transfer/merge.
     *
     * <p>This deliberately does not let two vanilla max-level books create the over-cap level. The anvil only preserves
     * or transfers an over-cap level that already exists on either input item.</p>
     */
    public static int getAllowedAnvilMaxLevel(ItemStack leftStack, ItemStack rightStack, Enchantment enchantment) {
        int vanillaMaxLevel = enchantment.getMaxLevel();
        int leftLevel = getStoredOrAppliedLevel(leftStack, enchantment);
        int rightLevel = getStoredOrAppliedLevel(rightStack, enchantment);

        if (leftLevel > vanillaMaxLevel || rightLevel > vanillaMaxLevel) {
            return vanillaMaxLevel + OVERCAP_LEVEL_BONUS;
        }

        return vanillaMaxLevel;
    }

    private static int getStoredOrAppliedLevel(ItemStack stack, Enchantment enchantment) {
        int level = 0;

        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : EnchantmentHelper.getEnchantments(stack).getEnchantmentEntries()) {
            if (entry.getKey().value() == enchantment) {
                level = Math.max(level, entry.getIntValue());
            }
        }

        return level;
    }

    /**
     * Returns this shelf's particle weight. Three enchanted books equal the vanilla particle rate.
     */
    public static int getParticleWeight(World world, BlockPos tablePos, BlockPos providerOffset) {
        ChiseledBookshelfBlockEntity bookshelf = getValidChiseledBookshelf(world, tablePos, providerOffset);
        if (bookshelf == null) {
            return 0;
        }

        int particleWeight;
        if (world.isClient() && bookshelf instanceof ChiseledBookshelfEnchantingPowerState syncedState) {
            particleWeight = syncedState.chieseled_enchanting$getParticleWeight();
        } else {
            particleWeight = getParticleWeightForStacks(bookshelf.getHeldStacks());
        }

        return Math.max(0, Math.min(particleWeight, ChiseledBookshelfBlockEntity.MAX_BOOKS));
    }

    private static ChiseledBookshelfBlockEntity getValidChiseledBookshelf(World world, BlockPos tablePos, BlockPos providerOffset) {
        if (!canTransmitPower(world, tablePos, providerOffset)) {
            return null;
        }

        return getChiseledBookshelf(world, tablePos.add(providerOffset));
    }

    private static ChiseledBookshelfBlockEntity getChiseledBookshelf(World world, BlockPos shelfPos) {
        if (!world.getBlockState(shelfPos).isOf(Blocks.CHISELED_BOOKSHELF)) {
            return null;
        }

        BlockEntity blockEntity = world.getBlockEntity(shelfPos);
        return blockEntity instanceof ChiseledBookshelfBlockEntity bookshelf ? bookshelf : null;
    }

    /**
     * Equivalent to the unobstructed-gap half of EnchantingTableBlock#canAccessPowerProvider.
     *
     * <p>Do not call canAccessPowerProvider here: that method also checks the enchantment_power_provider block tag,
     * and chiseled bookshelves are not in that tag unless a datapack adds them.</p>
     */
    private static boolean canTransmitPower(World world, BlockPos tablePos, BlockPos providerOffset) {
        BlockPos transmitterPos = tablePos.add(
                providerOffset.getX() / 2,
                providerOffset.getY(),
                providerOffset.getZ() / 2
        );
        return world.getBlockState(transmitterPos).isIn(BlockTags.ENCHANTMENT_POWER_TRANSMITTER);
    }

    private static void collectUnlockedOvercapEnchantments(
            Iterable<ItemStack> stacks,
            Set<RegistryEntry<Enchantment>> unlocked
    ) {
        Map<RegistryEntry<Enchantment>, Set<Integer>> foundLevelsByEnchantment = new HashMap<>();

        for (ItemStack stack : stacks) {
            ItemEnchantmentsComponent storedEnchantments = getStoredEnchantments(stack);
            if (storedEnchantments == null) {
                continue;
            }

            for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : storedEnchantments.getEnchantmentEntries()) {
                RegistryEntry<Enchantment> enchantment = entry.getKey();
                int level = entry.getIntValue();
                int minLevel = enchantment.value().getMinLevel();
                int maxLevel = enchantment.value().getMaxLevel();

                if (level >= minLevel && level <= maxLevel) {
                    foundLevelsByEnchantment.computeIfAbsent(enchantment, ignored -> new HashSet<>()).add(level);
                }
            }
        }

        for (Map.Entry<RegistryEntry<Enchantment>, Set<Integer>> entry : foundLevelsByEnchantment.entrySet()) {
            RegistryEntry<Enchantment> enchantment = entry.getKey();
            if (containsEveryVanillaLevel(enchantment, entry.getValue())) {
                unlocked.add(enchantment);
            }
        }
    }

    private static boolean containsEveryVanillaLevel(RegistryEntry<Enchantment> enchantment, Set<Integer> foundLevels) {
        int minLevel = enchantment.value().getMinLevel();
        int maxLevel = enchantment.value().getMaxLevel();

        for (int level = minLevel; level <= maxLevel; level++) {
            if (!foundLevels.contains(level)) {
                return false;
            }
        }

        return true;
    }


    public static int getParticleWeightForStacks(Iterable<ItemStack> stacks) {
        return countEnchantedBooksWithEnchantments(stacks);
    }

    public static int countEnchantedBooksWithEnchantments(Iterable<ItemStack> stacks) {
        int count = 0;

        for (ItemStack stack : stacks) {
            if (getStoredEnchantments(stack) != null) {
                count++;
            }
        }

        return count;
    }

    private static int sumStoredEnchantmentLevels(Iterable<ItemStack> stacks) {
        int sum = 0;

        for (ItemStack stack : stacks) {
            ItemEnchantmentsComponent storedEnchantments = getStoredEnchantments(stack);
            if (storedEnchantments == null) {
                continue;
            }

            for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : storedEnchantments.getEnchantmentEntries()) {
                sum += Math.max(0, entry.getIntValue());
            }
        }

        return sum;
    }

    private static ItemEnchantmentsComponent getStoredEnchantments(ItemStack stack) {
        if (!stack.isOf(Items.ENCHANTED_BOOK)) {
            return null;
        }

        ItemEnchantmentsComponent storedEnchantments = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        return storedEnchantments != null && !storedEnchantments.isEmpty() ? storedEnchantments : null;
    }
}
