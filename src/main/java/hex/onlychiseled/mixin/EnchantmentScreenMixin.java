package hex.onlychiseled.mixin;

import hex.onlychiseled.access.EnchantmentScreenHandlerSecondReveal;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Environment(EnvType.CLIENT)
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin extends HandledScreen<EnchantmentScreenHandler> {
    protected EnchantmentScreenMixin(EnchantmentScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    /**
     * Vanilla builds a hover tooltip with one revealed clue. Append the second synced clue when the library is strong
     * enough, without changing the actual generated enchantments or click result.
     */
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;II)V"
            )
    )
    private void chieseled_enchanting$drawTooltipWithSecondReveal(
            DrawContext context,
            TextRenderer textRenderer,
            List<Text> tooltip,
            int mouseX,
            int mouseY
    ) {
        List<Text> adjustedTooltip = tooltip;
        int hoveredSlot = this.chieseled_enchanting$getHoveredEnchantmentSlot(mouseX, mouseY);

        if (hoveredSlot >= 0 && this.handler instanceof EnchantmentScreenHandlerSecondReveal secondReveal) {
            int secondEnchantmentId = secondReveal.chieseled_enchanting$getSecondEnchantmentId(hoveredSlot);
            int secondEnchantmentLevel = secondReveal.chieseled_enchanting$getSecondEnchantmentLevel(hoveredSlot);

            if (secondEnchantmentId >= 0 && secondEnchantmentLevel >= 0 && this.client != null && this.client.world != null) {
                Optional<RegistryEntry.Reference<Enchantment>> secondEnchantment = this.client.world
                        .getRegistryManager()
                        .getOrThrow(RegistryKeys.ENCHANTMENT)
                        .getEntry(secondEnchantmentId);

                if (secondEnchantment.isPresent()) {
                    adjustedTooltip = new ArrayList<>(tooltip);
                    adjustedTooltip.add(
                            Math.min(1, adjustedTooltip.size()),
                            Text.translatable(
                                    "container.enchant.clue",
                                    Enchantment.getName(secondEnchantment.get(), secondEnchantmentLevel)
                            ).formatted(Formatting.WHITE)
                    );
                }
            }
        }

        context.drawTooltip(textRenderer, adjustedTooltip, mouseX, mouseY);
    }

    private int chieseled_enchanting$getHoveredEnchantmentSlot(int mouseX, int mouseY) {
        int relativeX = mouseX - (this.x + 60);
        if (relativeX < 0 || relativeX >= 108) {
            return -1;
        }

        for (int slot = 0; slot < 3; slot++) {
            int relativeY = mouseY - (this.y + 14 + 19 * slot);
            if (relativeY >= 0 && relativeY < 17) {
                return slot;
            }
        }

        return -1;
    }
}
