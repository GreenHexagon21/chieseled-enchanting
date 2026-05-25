package hex.onlychiseled.mixin;

import hex.onlychiseled.ChiseledBookshelfEnchantingPower;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantingTableBlock.class)
public abstract class EnchantingTableBlockMixin {
    /**
     * Replaces the vanilla enchanting-table particle loop so regular bookshelves can keep vanilla particles while
     * chiseled-bookshelf particle frequency scales with shelf contents.
     *
     * <p>Regular bookshelves use vanilla's 1-in-16 rate. Three enchanted books in a chiseled bookshelf match that
     * baseline; fewer enchanted books emit less often, and more enchanted books emit more often.</p>
     */
    @Inject(method = "randomDisplayTick", at = @At("HEAD"), cancellable = true)
    private void chieseled_enchanting$spawnScaledChiseledBookshelfParticles(
            net.minecraft.block.BlockState state,
            World world,
            BlockPos tablePos,
            Random random,
            CallbackInfo ci
    ) {
        ci.cancel();

        for (BlockPos providerOffset : EnchantingTableBlock.POWER_PROVIDER_OFFSETS) {
            if (ChiseledBookshelfEnchantingPower.isRegularBookshelfPowerProvider(world, tablePos, providerOffset)) {
                if (random.nextInt(16) == 0) {
                    chieseled_enchanting$spawnEnchantParticle(world, tablePos, providerOffset, random);
                }
                continue;
            }

            int particleWeight = ChiseledBookshelfEnchantingPower.getParticleWeight(world, tablePos, providerOffset);
            if (particleWeight <= 0) {
                continue;
            }

            // Scale around vanilla's 1-in-16 rate:
            // 1 enchanted book = 1-in-48, 2 = 2-in-48, 3 = 3-in-48 = 1-in-16, 6 = 6-in-48 = 1-in-8.
            if (random.nextInt(48) < particleWeight) {
                chieseled_enchanting$spawnEnchantParticle(world, tablePos, providerOffset, random);
            }
        }
    }

    private static void chieseled_enchanting$spawnEnchantParticle(
            World world,
            BlockPos tablePos,
            BlockPos providerOffset,
            Random random
    ) {
        world.addParticleClient(
                ParticleTypes.ENCHANT,
                tablePos.getX() + 0.5,
                tablePos.getY() + 2.0,
                tablePos.getZ() + 0.5,
                providerOffset.getX() + random.nextFloat() - 0.5,
                providerOffset.getY() - random.nextFloat() - 1.0F,
                providerOffset.getZ() + random.nextFloat() - 0.5
        );
    }
}
