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
     * Replaces the vanilla enchanting-table particle loop so particle frequency can scale with shelf contents.
     *
     * <p>Vanilla checks each provider offset with a 1-in-16 chance. This mod treats three enchanted books in a
     * chiseled bookshelf as that vanilla baseline. Fewer enchanted books emit less often, and more enchanted books
     * emit more often.</p>
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
            int particleWeight = ChiseledBookshelfEnchantingPower.getParticleWeight(world, tablePos, providerOffset);
            if (particleWeight <= 0) {
                continue;
            }

            // Scale around vanilla's 1-in-16 rate:
            // 1 enchanted book = 1-in-48, 2 = 2-in-48, 3 = 3-in-48 = 1-in-16, 6 = 6-in-48 = 1-in-8.
            if (random.nextInt(48) < particleWeight) {
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
    }
}
