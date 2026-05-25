package hex.onlychiseled.access;

public interface ChiseledBookshelfEnchantingPowerState {
    int chieseled_enchanting$getParticleWeight();

    default boolean chieseled_enchanting$hasEnchantedBookForParticles() {
        return this.chieseled_enchanting$getParticleWeight() > 0;
    }
}
