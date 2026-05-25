package hex.onlychiseled.mixin;

import hex.onlychiseled.ChiseledBookshelfEnchantingPower;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {
    /**
     * Vanilla clamps every anvil merge to Enchantment#getMaxLevel. For this mod, allow one over-cap level only when
     * one of the two anvil inputs already contains that over-cap level. This preserves/transfers table-created levels
     * such as Respiration IV without letting two vanilla Respiration III books create Respiration IV directly.
     */
    @Redirect(
            method = "updateResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/enchantment/Enchantment;getMaxLevel()I"
            )
    )
    private int chieseled_enchanting$preserveExistingOvercapLevelsInAnvil(Enchantment enchantment) {
        Inventory input = ((ForgingScreenHandlerInputAccessor) this).chieseled_enchanting$getInput();
        return ChiseledBookshelfEnchantingPower.getAllowedAnvilMaxLevel(
                input.getStack(0),
                input.getStack(1),
                enchantment
        );
    }
}
