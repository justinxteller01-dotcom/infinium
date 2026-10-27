package dev.justin.infinium.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.justin.infinium.client.InfiniumModMenuConfigScreen;

/** Mod Menu configuration entrypoint for Infinium. */
public class InfiniumModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return InfiniumModMenuConfigScreen::new;
    }
}
