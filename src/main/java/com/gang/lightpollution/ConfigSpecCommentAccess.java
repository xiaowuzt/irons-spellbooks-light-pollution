package com.gang.lightpollution;

import net.minecraftforge.common.ForgeConfigSpec;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * Narrow adapter for Forge 47's immutable comment metadata. There is no supported setter: changing
 * only the file makes Forge "correct" it back on every load. No values, validators, caches or config
 * paths are changed here. Kept separate and covered by real-Spec tests so an incompatible Forge
 * fails closed (retains its original comments) instead of entering a save/reload loop.
 */
final class ConfigSpecCommentAccess {
    private static final Field VALUE_COMMENT = field(ForgeConfigSpec.ValueSpec.class, "comment");
    private static final Field LEVEL_COMMENTS = field(ForgeConfigSpec.class, "levelComments");

    private ConfigSpecCommentAccess() {}

    private static Field field(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            return field.trySetAccessible() ? field : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    static boolean available() { return VALUE_COMMENT != null && LEVEL_COMMENTS != null; }

    static void set(ForgeConfigSpec.ValueSpec value, String comment) throws IllegalAccessException {
        VALUE_COMMENT.set(value, comment);
    }

    @SuppressWarnings("unchecked")
    static Map<List<String>, String> sections(ForgeConfigSpec spec) throws IllegalAccessException {
        return (Map<List<String>, String>) LEVEL_COMMENTS.get(spec);
    }
}
