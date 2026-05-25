package hex.onlychiseled;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChieseledEnchanting implements ModInitializer {
    public static final String MOD_ID = "chieseled-enchanting";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Chieseled Enchanting loaded: regular bookshelves and chiseled bookshelf libraries can power enchanting tables.");
    }
}
