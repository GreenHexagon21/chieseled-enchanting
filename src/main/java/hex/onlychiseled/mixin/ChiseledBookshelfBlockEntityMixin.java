package hex.onlychiseled.mixin;

import hex.onlychiseled.ChiseledBookshelfEnchantingPower;
import hex.onlychiseled.access.ChiseledBookshelfEnchantingPowerState;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChiseledBookshelfBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChiseledBookshelfBlockEntity.class)
public abstract class ChiseledBookshelfBlockEntityMixin extends BlockEntity implements ChiseledBookshelfEnchantingPowerState {
    @Unique
    private static final String CHIESELED_ENCHANTING_PARTICLE_WEIGHT_KEY = "chieseled_enchanting_particle_weight";

    @Shadow @Final
    private DefaultedList<ItemStack> heldStacks;

    @Unique
    private int chieseled_enchanting$particleWeight;

    protected ChiseledBookshelfBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Vanilla updates the chiseled-bookshelf block state when its inventory changes, but block-entity update packets
     * are separate. Mark the block entity for update so clients receive the compact particle flag immediately.
     */
    @Inject(method = "updateState", at = @At("TAIL"))
    private void chieseled_enchanting$syncParticleState(int interactedSlot, CallbackInfo ci) {
        if (this.world instanceof ServerWorld serverWorld) {
            serverWorld.getChunkManager().markForUpdate(this.pos);
        }
    }

    /**
     * Sends observable chiseled-bookshelf data to clients.
     */
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    /**
     * Include vanilla componentless shelf data plus a tiny integer that tells the client how often this shelf should
     * emit particles.
     */
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = this.createComponentlessNbt(registries);
        nbt.putInt(
                CHIESELED_ENCHANTING_PARTICLE_WEIGHT_KEY,
                ChiseledBookshelfEnchantingPower.getParticleWeightForStacks(this.heldStacks)
        );
        return nbt;
    }

    /**
     * 1.21.10 block entities read NBT through readData(ReadView), not readNbt(NbtCompound, WrapperLookup).
     */
    @Inject(method = "readData", at = @At("TAIL"))
    private void chieseled_enchanting$readSyncedParticleState(ReadView view, CallbackInfo ci) {
        this.chieseled_enchanting$particleWeight = view.getInt(
                CHIESELED_ENCHANTING_PARTICLE_WEIGHT_KEY,
                ChiseledBookshelfEnchantingPower.getParticleWeightForStacks(this.heldStacks)
        );
    }

    @Override
    public int chieseled_enchanting$getParticleWeight() {
        return this.chieseled_enchanting$particleWeight;
    }
}
