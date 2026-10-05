package com.gang.lightpollution;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;

/** Installation-local comment language. Deliberately has no Minecraft client class references. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ConfigLocalization {
    private static final System.Logger LOGGER = System.getLogger(ConfigLocalization.class.getName());
    private static final Map<ForgeConfigSpec, Path> LOCAL_FILES = new IdentityHashMap<>();
    private static final Map<Path, Boolean> WARNED = new java.util.HashMap<>();
    private static volatile ConfigCommentLanguage mode = ConfigCommentLanguage.AUTO;
    private static volatile ConfigCommentLanguage language = ConfigCommentLanguage.BILINGUAL;
    private static boolean physicalClient;
    private static String clientLanguage = "en_us";

    private ConfigLocalization() {}
    public static ConfigCommentLanguage language() { return language; }

    /** Called before specs are registered, so the very first generated files use the right language. */
    public static synchronized void initialize(boolean client, Path gameDirectory, Path configDirectory) {
        physicalClient = client;
        if (client) clientLanguage = readGameLanguage(gameDirectory.resolve("options.txt"));
        Path preference = configDirectory.resolve(ExampleMod.MODID + "-localization.toml");
        if (Files.isRegularFile(preference)) {
            try {
                Object value = new TomlParser().parse(Files.readString(preference, StandardCharsets.UTF_8)).get("commentLanguage");
                if (value instanceof String name) mode = ConfigCommentLanguage.valueOf(name.toUpperCase(java.util.Locale.ROOT));
            } catch (IOException | RuntimeException ignored) { /* Forge will handle an invalid preference normally. */ }
        }
        language = mode.resolve(physicalClient, clientLanguage);
    }

    static String readGameLanguage(Path options) {
        try {
            for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
                if (line.startsWith("lang:")) return line.substring(5).strip();
            }
        } catch (IOException ignored) { /* A fresh installation has no options.txt yet. */ }
        return "en_us";
    }

    public static synchronized void setMode(ConfigCommentLanguage selection) {
        mode = selection;
        updateLanguage();
    }

    public static synchronized void setClientLanguage(String selection) {
        if (!physicalClient || selection == null || selection.equals(clientLanguage)) return;
        clientLanguage = selection;
        updateLanguage();
    }

    private static void updateLanguage() {
        ConfigCommentLanguage next = mode.resolve(physicalClient, clientLanguage);
        if (next == language) return;
        language = next;
        refresh();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static synchronized void onConfig(ModConfigEvent event) {
        var config = event.getConfig();
        if (!ExampleMod.MODID.equals(config.getModId()) || !(config.getSpec() instanceof ForgeConfigSpec spec)) return;
        if (event instanceof ModConfigEvent.Unloading) {
            LOCAL_FILES.remove(spec);
            return;
        }
        // Synced multiplayer SERVER data is in-memory, not a FileConfig. Never write a local
        // world/server file merely because its spec is also used by a remote server connection.
        if (!(config.getConfigData() instanceof FileConfig file)) {
            LOCAL_FILES.remove(spec);
            return;
        }
        ConfigComments.register(spec);
        Path path = file.getNioPath().toAbsolutePath().normalize();
        LOCAL_FILES.put(spec, path);
        localize(spec, path);
    }

    private static void refresh() {
        LOCAL_FILES.forEach(ConfigLocalization::localize);
    }

    private static void localize(ForgeConfigSpec spec, Path path) {
        if (ConfigCommentWriter.localize(path, spec, language) == ConfigCommentWriter.Result.SKIPPED) {
            if (WARNED.putIfAbsent(path, true) == null) LOGGER.log(System.Logger.Level.WARNING,
                    "Skipped comment localization for " + path + "; file values were not written. Check syntax, inline tables, permissions or a concurrent edit.");
        } else WARNED.remove(path);
    }
}
