package com.gang.lightpollution;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import java.util.ArrayList;
import java.util.List;

/** Real runtime specs also used by the example generator; client specs are behind a lazy holder. */
public final class ConfigurationFiles {
    public record Definition(String filename, ModConfig.Type type, ForgeConfigSpec spec) {}
    private ConfigurationFiles() {}

    public static List<Definition> common() {
        return List.of(new Definition(ExampleMod.MODID + "-localization.toml", ModConfig.Type.COMMON, Config.SPEC),
                new Definition(ExampleMod.MODID + "-server.toml", ModConfig.Type.SERVER, SpellConfig.SPEC),
                new Definition(ExampleMod.MODID + "-performance.toml", ModConfig.Type.COMMON,
                        com.gang.lightpollution.performance.ServerPerformanceBudget.SPEC));
    }

    public static List<Definition> all(boolean physicalClient) {
        List<Definition> definitions = new ArrayList<>(common());
        if (physicalClient) definitions.addAll(ClientHolder.DEFINITIONS);
        return List.copyOf(definitions);
    }

    public static void register(ModLoadingContext context, boolean physicalClient) {
        for (Definition definition : all(physicalClient)) {
            ConfigComments.register(definition.spec());
            context.registerConfig(definition.type(), definition.spec(), definition.filename());
        }
    }

    private static final class ClientHolder {
        private static final List<Definition> DEFINITIONS = List.of(
                new Definition(ExampleMod.MODID + "-client.toml", ModConfig.Type.CLIENT, SpellLightConfig.SPEC),
                new Definition(ExampleMod.MODID + "-black-holes.toml", ModConfig.Type.CLIENT, BlackHoleVisualConfig.SPEC),
                new Definition(ExampleMod.MODID + "-text.toml", ModConfig.Type.CLIENT,
                        com.gang.lightpollution.text.DynamicTextClientConfig.SPEC));
    }
}
