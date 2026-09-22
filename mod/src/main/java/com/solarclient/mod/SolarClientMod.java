package com.solarclient.mod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SolarClient is entirely client-side (HUDs, waypoints, keybinds) so this
 * common entrypoint doesn't do much — it exists so the mod also loads
 * cleanly if someone ever puts it on a dedicated server by mistake, without
 * crashing the server.
 */
public class SolarClientMod implements ModInitializer {
    public static final String MOD_ID = "solarclient";
    public static final Logger LOGGER = LoggerFactory.getLogger("SolarClient");

    @Override
    public void onInitialize() {
        LOGGER.info("SolarClient common init (client-only mod, nothing to do here)");
    }
}
