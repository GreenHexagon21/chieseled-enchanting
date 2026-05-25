package hex.onlychiseled.mixin;

import hex.onlychiseled.ChiseledBookshelfEnchantingPower;
import hex.onlychiseled.access.EnchantmentScreenHandlerSecondReveal;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.collection.IndexedIterable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.function.BiConsumer;

@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentScreenHandlerMixin extends ScreenHandler implements EnchantmentScreenHandlerSecondReveal {
    @Shadow @Final private Random random;
    @Shadow @Final private Property seed;
    @Shadow @Final public int[] enchantmentPower;
    @Shadow @Final public int[] enchantmentId;
    @Shadow @Final public int[] enchantmentLevel;

    @Unique
    private final int[] chieseled_enchanting$secondEnchantmentId = new int[]{-1, -1, -1};
    @Unique
    private final int[] chieseled_enchanting$secondEnchantmentLevel = new int[]{-1, -1, -1};

    @Unique
    private Set<RegistryEntry<Enchantment>> chieseled_enchanting$unlockedOvercapEnchantments = Set.of();

    protected EnchantmentScreenHandlerMixin(ScreenHandlerType<?> type, int syncId) {
        super(type, syncId);
    }

    /**
     * Adds three synced enchantment-id properties and three synced level properties for the optional second clue.
     */
    @Inject(
            method = "<init>(ILnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/screen/ScreenHandlerContext;)V",
            at = @At("TAIL")
    )
    private void chieseled_enchanting$addSecondRevealProperties(
            int syncId,
            PlayerInventory playerInventory,
            ScreenHandlerContext context,
            CallbackInfo ci
    ) {
        for (int slot = 0; slot < 3; slot++) {
            this.addProperty(Property.create(this.chieseled_enchanting$secondEnchantmentId, slot));
            this.addProperty(Property.create(this.chieseled_enchanting$secondEnchantmentLevel, slot));
        }
    }

    /**
     * Replaces vanilla's bookshelf count inside EnchantmentScreenHandler#onContentChanged.
     *
     * <p>The rest of the vanilla offer-generation algorithm is intentionally preserved except for the level cap.
     * Regular bookshelves contribute vanilla power up to 15. Chiseled bookshelves add custom power from qualifying
     * enchanted-book count, and the combined room power is capped at the extended power cap of 30.</p>
     */
    @Redirect(
            method = "onContentChanged",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/screen/ScreenHandlerContext;run(Ljava/util/function/BiConsumer;)V"
            )
    )
    private void chieseled_enchanting$useChiseledBookshelvesForPower(
            ScreenHandlerContext context,
            BiConsumer<World, BlockPos> originalAction
    ) {
        context.run((world, tablePos) -> {
            IndexedIterable<RegistryEntry<Enchantment>> enchantmentEntries = world.getRegistryManager()
                    .getOrThrow(RegistryKeys.ENCHANTMENT)
                    .getIndexedEntries();
            ItemStack enchantingStack = this.getSlot(0).getStack();
            ChiseledBookshelfEnchantingPower.LibraryAnalysis library = ChiseledBookshelfEnchantingPower.analyze(world, tablePos);
            int enchantingPower = library.enchantingPower();
            boolean revealSecondEnchantment = library.revealSecondEnchantment();
            this.chieseled_enchanting$unlockedOvercapEnchantments = library.unlockedOvercapEnchantments();
            this.chieseled_enchanting$clearSecondReveals();

            this.random.setSeed(this.seed.get());

            for (int slot = 0; slot < 3; slot++) {
                this.enchantmentPower[slot] = ChiseledBookshelfEnchantingPower.calculateRequiredExperienceLevel(
                        this.random,
                        slot,
                        enchantingPower,
                        enchantingStack
                );
                this.enchantmentId[slot] = -1;
                this.enchantmentLevel[slot] = -1;

                if (this.enchantmentPower[slot] < slot + 1) {
                    this.enchantmentPower[slot] = 0;
                }
            }

            for (int slot = 0; slot < 3; slot++) {
                if (this.enchantmentPower[slot] <= 0) {
                    continue;
                }

                List<EnchantmentLevelEntry> generatedEnchantments = this.chieseled_enchanting$generateEnchantments(
                        world.getRegistryManager(),
                        enchantingStack,
                        slot,
                        this.enchantmentPower[slot]
                );

                if (!generatedEnchantments.isEmpty()) {
                    int displayedIndex = this.random.nextInt(generatedEnchantments.size());
                    EnchantmentLevelEntry displayedEnchantment = generatedEnchantments.get(displayedIndex);
                    this.enchantmentId[slot] = enchantmentEntries.getRawId(displayedEnchantment.enchantment());
                    this.enchantmentLevel[slot] = displayedEnchantment.level();

                    if (revealSecondEnchantment && generatedEnchantments.size() > 1) {
                        EnchantmentLevelEntry secondDisplayedEnchantment = generatedEnchantments.get(
                                this.chieseled_enchanting$chooseSecondRevealIndex(generatedEnchantments.size(), displayedIndex, slot)
                        );
                        this.chieseled_enchanting$secondEnchantmentId[slot] = enchantmentEntries.getRawId(secondDisplayedEnchantment.enchantment());
                        this.chieseled_enchanting$secondEnchantmentLevel[slot] = secondDisplayedEnchantment.level();
                    }
                }
            }

            this.sendContentUpdates();
        });
    }

    /**
     * Clears stale second clues when the enchanting slot is emptied or replaced by a non-enchantable item.
     */
    @Inject(method = "onContentChanged", at = @At("TAIL"))
    private void chieseled_enchanting$clearSecondRevealsForInvalidStacks(Inventory inventory, CallbackInfo ci) {
        ItemStack enchantingStack = this.getSlot(0).getStack();
        if (enchantingStack.isEmpty() || !enchantingStack.isEnchantable()) {
            this.chieseled_enchanting$clearSecondReveals();
            this.chieseled_enchanting$unlockedOvercapEnchantments = Set.of();
            this.sendContentUpdates();
        }
    }

    @Unique
    private void chieseled_enchanting$clearSecondReveals() {
        for (int slot = 0; slot < 3; slot++) {
            this.chieseled_enchanting$secondEnchantmentId[slot] = -1;
            this.chieseled_enchanting$secondEnchantmentLevel[slot] = -1;
        }
    }

    @Unique
    private int chieseled_enchanting$chooseSecondRevealIndex(int generatedEnchantmentCount, int firstRevealIndex, int slot) {
        long mixed = ((long) this.seed.get() * 0x9E3779B97F4A7C15L)
                ^ ((long) slot * 0xBF58476D1CE4E5B9L)
                ^ firstRevealIndex;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;

        int secondIndex = Math.floorMod(mixed, generatedEnchantmentCount - 1);
        if (secondIndex >= firstRevealIndex) {
            secondIndex++;
        }
        return secondIndex;
    }

    @Override
    public int chieseled_enchanting$getSecondEnchantmentId(int slot) {
        return slot >= 0 && slot < this.chieseled_enchanting$secondEnchantmentId.length
                ? this.chieseled_enchanting$secondEnchantmentId[slot]
                : -1;
    }

    @Override
    public int chieseled_enchanting$getSecondEnchantmentLevel(int slot) {
        return slot >= 0 && slot < this.chieseled_enchanting$secondEnchantmentLevel.length
                ? this.chieseled_enchanting$secondEnchantmentLevel[slot]
                : -1;
    }

    /**
     * Makes unlocked over-cap levels possible without duplicating their enchantment weight in the vanilla pool.
     *
     * <p>Vanilla still chooses enchantment types first. If an unlocked enchantment is selected at its vanilla
     * maximum level, the helper can probabilistically promote that selected entry by one level.</p>
     */
    @Redirect(
            method = "generateEnchantments",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/enchantment/EnchantmentHelper;generateEnchantments(Lnet/minecraft/util/math/random/Random;Lnet/minecraft/item/ItemStack;ILjava/util/stream/Stream;)Ljava/util/List;"
            )
    )
    private List<EnchantmentLevelEntry> chieseled_enchanting$addUnlockedOvercapLevelsToCandidatePool(
            Random random,
            ItemStack stack,
            int level,
            Stream<RegistryEntry<Enchantment>> possibleEnchantments
    ) {
        return ChiseledBookshelfEnchantingPower.generateEnchantmentsWithOvercapPool(
                random,
                stack,
                level,
                possibleEnchantments,
                this.chieseled_enchanting$unlockedOvercapEnchantments
        );
    }

    @Invoker("generateEnchantments")
    protected abstract List<EnchantmentLevelEntry> chieseled_enchanting$generateEnchantments(
            DynamicRegistryManager registryManager,
            ItemStack stack,
            int slot,
            int level
    );
}
