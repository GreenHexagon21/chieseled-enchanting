package hex.onlychiseled;

import hex.onlychiseled.access.ChiseledBookshelfEnchantingPowerState;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.BlockState;
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
    public static final int VANILLA_BOOKSHELF_MAX_POWER = 15;
    public static final int VANILLA_BOOKSHELF_PARTICLE_WEIGHT = 3;
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
     * <p>Regular bookshelves use vanilla placement and obstruction rules and contribute one power each, capped at
     * vanilla power 15. Chiseled bookshelves use the same placement and obstruction rules, but only contribute when
     * they contain at least three enchanted books with stored enchantments. Chiseled power scales linearly by stored
     * book count. The combined room power is capped at this mod's extended power cap.</p>
     */
    public static LibraryAnalysis analyze(World world, BlockPos tablePos) {
        int regularBookshelfProviders = 0;
        int qualifyingEnchantedBooks = 0;
        int storedEnchantmentLevelSum = 0;
        Set<RegistryEntry<Enchantment>> unlockedOvercapEnchantments = new HashSet<>();

        for (BlockPos providerOffset : EnchantingTableBlock.POWER_PROVIDER_OFFSETS) {
            if (!canTransmitPower(world, tablePos, providerOffset)) {
                continue;
            }

            BlockPos shelfPos = tablePos.add(providerOffset);
            BlockState shelfState = world.getBlockState(shelfPos);

            if (shelfState.isOf(Blocks.BOOKSHELF)) {
                regularBookshelfProviders++;
                continue;
            }

            if (!shelfState.isOf(Blocks.CHISELED_BOOKSHELF)) {
                continue;
            }

            BlockEntity blockEntity = world.getBlockEntity(shelfPos);
            if (!(blockEntity instanceof ChiseledBookshelfBlockEntity bookshelf)) {
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
                calculateCombinedPower(regularBookshelfProviders, qualifyingEnchantedBooks),
                storedEnchantmentLevelSum > SECOND_REVEAL_ENCHANTMENT_LEVEL_SUM_THRESHOLD,
                Set.copyOf(unlockedOvercapEnchantments)
        );
    }

    private static int calculateCombinedPower(int regularBookshelfProviders, int qualifyingEnchantedBooks) {
        int regularBookshelfPower = Math.min(Math.max(0, regularBookshelfProviders), VANILLA_BOOKSHELF_MAX_POWER);
        int chiseledBookshelfPower = calculatePowerFromQualifyingEnchantedBooks(qualifyingEnchantedBooks);
        return Math.min(regularBookshelfPower + chiseledBookshelfPower, EXTENDED_MAX_POWER);
    }

    private static int calculatePowerFromQualifyingEnchantedBooks(int qualifyingEnchantedBooks) {
        int books = Math.max(0, qualifyingEnchantedBooks);
        return Math.min(books / ENCHANTED_BOOKS_PER_POWER_LEVEL, EXTENDED_MAX_POWER);
    }

    /**
     * Vanilla's EnchantmentHelper caps bookshelf count at 15. This copy keeps the vanilla formula but raises the cap
     * to this mod's extended power cap. With power 30, the bottom offer can reach level 60.
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
     * Returns true when the block at {@code tablePos + providerOffset} is a valid power provider.
     */
    public static boolean canProvidePower(World world, BlockPos tablePos, BlockPos providerOffset) {
        if (!canTransmitPower(world, tablePos, providerOffset)) {
            return false;
        }

        BlockPos shelfPos = tablePos.add(providerOffset);
        BlockState shelfState = world.getBlockState(shelfPos);

        if (shelfState.isOf(Blocks.BOOKSHELF)) {
            return true;
        }

        if (!shelfState.isOf(Blocks.CHISELED_BOOKSHELF)) {
            return false;
        }

        BlockEntity blockEntity = world.getBlockEntity(shelfPos);
        if (!(blockEntity instanceof ChiseledBookshelfBlockEntity bookshelf)) {
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
     * Returns this provider's particle weight. Regular bookshelves use the vanilla baseline; chiseled bookshelves
     * scale by stored enchanted-book count, where three enchanted books equal the vanilla particle rate.
     */
    public static int getParticleWeight(World world, BlockPos tablePos, BlockPos providerOffset) {
        if (!canTransmitPower(world, tablePos, providerOffset)) {
            return 0;
        }

        BlockPos shelfPos = tablePos.add(providerOffset);
        BlockState shelfState = world.getBlockState(shelfPos);

        if (shelfState.isOf(Blocks.BOOKSHELF)) {
            return VANILLA_BOOKSHELF_PARTICLE_WEIGHT;
        }

        if (!shelfState.isOf(Blocks.CHISELED_BOOKSHELF)) {
            return 0;
        }

        BlockEntity blockEntity = world.getBlockEntity(shelfPos);
        if (!(blockEntity instanceof ChiseledBookshelfBlockEntity bookshelf)) {
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

    /**
     * Equivalent to the unobstructed-gap half of EnchantingTableBlock#canAccessPowerProvider.
     *
     * <p>Do not call canAccessPowerProvider here: that method also checks the enchantment_power_provider block tag.
     * This mod intentionally counts vanilla regular bookshelves and its own chiseled-bookshelf logic directly, so a
     * datapack cannot accidentally make chiseled bookshelves contribute both regular and chiseled power.</p>
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
