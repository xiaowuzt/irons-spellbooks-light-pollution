package com.gang.lightpollution;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** One description source for Forge TOML, locale switching, examples and the field reference. */
public final class ConfigComments {
    public record Text(String english, String chinese) {}
    public record Entry(String path, ForgeConfigSpec.ValueSpec value, Text text) {
        public String comment(ConfigCommentLanguage language) {
            Object defaultValue = value.getDefault();
            String defaults = format(defaultValue);
            String range = range(value);
            String english = text.english() + "\nDefault: " + defaults + "; unit: "
                    + ConfigDescriptions.unit(path, false) + ".\nAllowed: " + range + ".";
            String chinese = text.chinese() + "\n默认值：" + defaults + "；单位："
                    + ConfigDescriptions.unit(path, true) + "。\n允许范围：" + range + "。";
            return switch (language) {
                case EN_US -> english;
                case ZH_CN -> chinese;
                default -> english + "\n" + chinese;
            };
        }
    }

    private record Model(List<Entry> entries, Map<String, Text> sections) {}
    private static final Map<ForgeConfigSpec.Builder, Map<String, Text>> PENDING = new IdentityHashMap<>();
    private static final Map<ForgeConfigSpec, Model> MODELS = new IdentityHashMap<>();
    private static final Map<ForgeConfigSpec, ConfigCommentLanguage> LANGUAGES = new IdentityHashMap<>();
    private static final System.Logger LOGGER = System.getLogger(ConfigComments.class.getName());
    private static boolean warningShown;

    private ConfigComments() {}

    public static ForgeConfigSpec.Builder bilingual(ForgeConfigSpec.Builder builder, String key, String english) {
        return bilingual(builder, key, ConfigDescriptions.english(key, english), ConfigDescriptions.chinese(key));
    }

    public static synchronized ForgeConfigSpec.Builder bilingual(
            ForgeConfigSpec.Builder builder, String key, String english, String chinese) {
        Text text = new Text(Objects.requireNonNull(english), Objects.requireNonNull(chinese));
        PENDING.computeIfAbsent(builder, ignored -> new LinkedHashMap<>()).put(key, text);
        return builder.comment(english, chinese).translation("config." + ExampleMod.MODID + "." + key);
    }

    public static synchronized ForgeConfigSpec build(ForgeConfigSpec.Builder builder) {
        ForgeConfigSpec spec = builder.build();
        register(spec, PENDING.remove(builder));
        return spec;
    }

    /** Also admits existing specs (e.g. the presentation budget) without altering their values. */
    public static synchronized void register(ForgeConfigSpec spec) {
        if (!MODELS.containsKey(spec)) register(spec, Map.of());
    }

    private static void register(ForgeConfigSpec spec, Map<String, Text> descriptions) {
        List<Entry> entries = new ArrayList<>();
        Map<String, Text> sections = new LinkedHashMap<>();
        collect(spec, spec.getSpec(), "", descriptions == null ? Map.of() : descriptions, entries, sections);
        MODELS.put(spec, new Model(List.copyOf(entries), sections));
        apply(spec, ConfigLocalization.language());
    }

    private static void collect(ForgeConfigSpec spec, UnmodifiableConfig node, String prefix,
            Map<String, Text> descriptions, List<Entry> entries, Map<String, Text> sections) {
        node.valueMap().forEach((key, object) -> {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (object instanceof ForgeConfigSpec.ValueSpec value) {
                Text text = descriptions.get(path);
                if (text == null) text = new Text(ConfigDescriptions.english(path, value.getComment()),
                        ConfigDescriptions.chinese(path));
                entries.add(new Entry(path, value, text));
            } else if (object instanceof UnmodifiableConfig child) {
                String original = spec.getLevelComment(List.of(path.split("\\.")));
                Text text = descriptions.get(path);
                if (text == null && original != null) {
                    text = new Text(ConfigDescriptions.english(path, original), ConfigDescriptions.chinese(path));
                }
                sections.put(path, text);
                collect(spec, child, path, descriptions, entries, sections);
            }
        });
    }

    public static synchronized List<Entry> entries(ForgeConfigSpec spec) {
        register(spec);
        return MODELS.get(spec).entries();
    }

    /** Expected comments for every known table and field; null means no table description. */
    public static synchronized Map<String, String> comments(ForgeConfigSpec spec, ConfigCommentLanguage language) {
        register(spec);
        Map<String, String> result = new LinkedHashMap<>();
        Model model = MODELS.get(spec);
        model.sections().forEach((path, text) -> result.put(path, text == null ? null : switch (language) {
            case EN_US -> text.english();
            case ZH_CN -> text.chinese();
            default -> text.english() + "\n" + text.chinese();
        }));
        model.entries().forEach(entry -> result.put(entry.path(), entry.comment(language)));
        return Collections.unmodifiableMap(result);
    }

    public static synchronized ConfigCommentLanguage language(ForgeConfigSpec spec) {
        register(spec);
        return LANGUAGES.getOrDefault(spec, ConfigCommentLanguage.BILINGUAL);
    }

    /** Updates the canonical expectation, not configuration values. Must precede rewriting a file. */
    public static synchronized boolean apply(ForgeConfigSpec spec, ConfigCommentLanguage language) {
        return apply(spec, language, Map.of());
    }

    static synchronized Map<String, String> effectiveComments(ForgeConfigSpec spec) {
        register(spec);
        Map<String, String> snapshot = new LinkedHashMap<>();
        Model model = MODELS.get(spec);
        model.entries().forEach(entry -> snapshot.put(entry.path(), entry.value().getComment()));
        model.sections().keySet().forEach(path -> snapshot.put(path, spec.getLevelComment(List.of(path.split("\\.")))));
        return snapshot;
    }

    /** File-specific inline notes are preserved and become part of Forge's expected comment. */
    static synchronized boolean apply(ForgeConfigSpec spec, ConfigCommentLanguage language, Map<String, String> fileComments) {
        if (!ConfigSpecCommentAccess.available()) {
            warn(new IllegalStateException("Forge 47 comment metadata is not accessible"));
            return false;
        }
        Model model = MODELS.get(spec);
        if (model == null) return false;
        synchronized (spec) {
            Map<ForgeConfigSpec.ValueSpec, String> old = new IdentityHashMap<>();
            Map<List<String>, String> oldSections = new LinkedHashMap<>();
            try {
                Map<List<String>, String> sections = ConfigSpecCommentAccess.sections(spec);
                oldSections.putAll(sections);
                for (Entry entry : model.entries()) {
                    old.put(entry.value(), entry.value().getComment());
                    ConfigSpecCommentAccess.set(entry.value(), fileComments.getOrDefault(entry.path(), entry.comment(language)));
                }
                Map<String, String> translated = comments(spec, language);
                for (String path : model.sections().keySet()) {
                    List<String> parts = List.of(path.split("\\."));
                    String text = fileComments.containsKey(path) ? fileComments.get(path) : translated.get(path);
                    if (text == null) sections.remove(parts); else sections.put(parts, text);
                }
                LANGUAGES.put(spec, language);
                return true;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                // Roll back metadata as a unit; never leave a half-translated expectation.
                try {
                    for (var entry : old.entrySet()) ConfigSpecCommentAccess.set(entry.getKey(), entry.getValue());
                    var sections = ConfigSpecCommentAccess.sections(spec);
                    sections.clear(); sections.putAll(oldSections);
                } catch (ReflectiveOperationException | RuntimeException rollback) {
                    exception.addSuppressed(rollback);
                }
                warn(exception);
                return false;
            }
        }
    }

    private static void warn(Exception exception) {
        if (warningShown) return;
        warningShown = true;
        LOGGER.log(System.Logger.Level.WARNING,
                "Cannot localize Forge TOML comments; retaining original comments and all values.", exception);
    }

    public static String range(ForgeConfigSpec.ValueSpec value) {
        ForgeConfigSpec.Range<?> range = value.getRange();
        if (range != null) return "[" + format(range.getMin()) + ", " + format(range.getMax()) + "]";
        Object def = value.getDefault();
        if (def instanceof Enum<?> e) return Arrays.stream(e.getDeclaringClass().getEnumConstants())
                .map(Enum::name).collect(Collectors.joining(", "));
        if (def instanceof Boolean) return "true, false";
        if (def instanceof List<?>) return "list<string>";
        return "string";
    }

    public static String format(Object value) {
        if (value instanceof String s) return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        if (value instanceof Enum<?> e) return "\"" + e.name() + "\"";
        if (value instanceof List<?> list) return list.stream().map(ConfigComments::format)
                .collect(Collectors.joining(", ", "[", "]"));
        return String.valueOf(value);
    }
}
