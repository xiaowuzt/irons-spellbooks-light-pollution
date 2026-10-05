package com.gang.lightpollution;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Rewrites only standalone comments; assignment bytes, inline comments and values are retained. */
public final class ConfigCommentWriter {
    public enum Result { UPDATED, UNCHANGED, SKIPPED }
    private ConfigCommentWriter() {}

    public static Result localize(Path path, ForgeConfigSpec spec, ConfigCommentLanguage language) {
        return localize(path, spec, language, () -> {});
    }

    // Serialize the entire file/metadata transaction, including direct callers outside
    // ConfigLocalization. Otherwise two checks can both pass before either atomic move.
    // The hook makes an external edit between read and commit reproducible in a standalone check.
    static synchronized Result localize(Path path, ForgeConfigSpec spec, ConfigCommentLanguage language, Runnable beforeCommit) {
        Path temporary = null;
        ConfigCommentLanguage previous = ConfigComments.language(spec);
        Map<String, String> previousComments = ConfigComments.effectiveComments(spec);
        boolean metadataChanged = false;
        try {
            if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) return Result.SKIPPED;
            byte[] original = Files.readAllBytes(path);
            String text = new String(original, StandardCharsets.UTF_8);
            String rewritten = rewrite(text, ConfigComments.comments(spec, language));
            byte[] replacement = rewritten.getBytes(StandardCharsets.UTF_8);
            CommentedConfig parsed = new TomlParser().parse(rewritten.startsWith("\ufeff") ? rewritten.substring(1) : rewritten);
            Map<String, String> fileComments = new LinkedHashMap<>();
            ConfigComments.comments(spec, language).forEach((key, textValue) -> {
                List<String> parts = List.of(key.split("\\."));
                if (parsed.contains(parts)) fileComments.put(key, parsed.getComment(parts));
            });
            if (Arrays.equals(original, replacement)) {
                return ConfigComments.apply(spec, language, fileComments) ? Result.UNCHANGED : Result.SKIPPED;
            }
            temporary = Files.createTempFile(path.toAbsolutePath().getParent(), ".lp-comments-", ".tmp");
            Files.write(temporary, replacement);
            beforeCommit.run();
            // Never save the (possibly stale) ModConfig in-memory values over a fresh hand edit.
            if (!Arrays.equals(original, Files.readAllBytes(path))) return Result.SKIPPED;
            if (!ConfigComments.apply(spec, language, fileComments)) return Result.SKIPPED;
            metadataChanged = true;
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
            return Result.UPDATED;
        } catch (IOException | RuntimeException exception) {
            if (metadataChanged) ConfigComments.apply(spec, previous, previousComments);
            return Result.SKIPPED;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* only our temporary file */ }
            }
        }
    }

    /** Rejects malformed or unrepresentable TOML rather than reserializing its assignments. */
    public static String rewrite(String input, Map<String, String> descriptions) {
        boolean bom = input.startsWith("\ufeff");
        String source = bom ? input.substring(1) : input;
        CommentedConfig before = new TomlParser().parse(source);
        List<Line> lines = splitLines(source);
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        Map<List<String>, String> known = new LinkedHashMap<>();
        descriptions.forEach((path, text) -> known.put(List.of(path.split("\\.")), text));
        Map<Integer, String> additions = new HashMap<>();
        Set<Integer> removed = new HashSet<>();
        Set<List<String>> represented = new HashSet<>();
        List<String> table = List.of();
        ScanState state = new ScanState();
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i).content();
            if (!state.complete()) { state.advance(raw); continue; }
            String trimmed = raw.stripLeading();
            if (trimmed.isBlank() || trimmed.startsWith("#")) continue;
            List<String> path;
            if (trimmed.startsWith("[[")) { table = null; continue; } // no array-of-table fields in these specs
            if (trimmed.startsWith("[")) {
                int end = outsideQuote(trimmed, ']');
                if (end < 0) throw new IllegalArgumentException("Unclosed table header");
                table = keyPath(trimmed.substring(1, end));
                path = table;
            } else {
                int equals = outsideQuote(raw, '=');
                if (equals < 0) throw new IllegalArgumentException("Unrecognized TOML statement");
                state.advance(raw.substring(equals + 1));
                if (table == null) continue;
                path = new ArrayList<>(table);
                path.addAll(keyPath(raw.substring(0, equals)));
            }
            if (!known.containsKey(path)) continue;
            represented.add(List.copyOf(path));
            int previous = i - 1;
            while (previous >= 0) {
                String prior = lines.get(previous).content().stripLeading();
                if (prior.startsWith("#")) removed.add(previous);
                else if (!prior.isBlank()) break;
                previous--;
            }
            String comment = known.get(path);
            if (comment != null) {
                String indent = raw.substring(0, raw.length() - trimmed.length());
                StringBuilder block = new StringBuilder();
                for (String line : comment.split("\n", -1)) block.append(indent).append('#').append(line).append(newline);
                additions.put(i, block.toString());
            }
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (removed.contains(i)) continue;
            result.append(additions.getOrDefault(i, ""));
            result.append(lines.get(i).content()).append(lines.get(i).ending());
        }
        String output = result.toString();
        CommentedConfig after = new TomlParser().parse(output);
        if (!values(before).equals(values(after))) throw new IllegalArgumentException("Comment rewrite changed TOML values");
        for (var entry : known.entrySet()) {
            if (!after.contains(entry.getKey())) continue;
            String expected = entry.getValue();
            String actual = after.getComment(entry.getKey());
            if (expected != null && (!represented.contains(entry.getKey()) || actual == null
                    || !(actual.equals(expected) || actual.startsWith(expected + "\n")))) {
                // Known inline tables cannot receive their child field comments without changing
                // assignment bytes. Inline notes on ordinary assignments remain byte-for-byte and
                // are included in the file-specific Forge expectation by localize().
                throw new IllegalArgumentException("Cannot attach comments without changing values: " + entry.getKey());
            }
        }
        return (bom ? "\ufeff" : "") + output;
    }

    private static Map<String, Object> values(UnmodifiableConfig config) {
        Map<String, Object> result = new LinkedHashMap<>();
        config.valueMap().forEach((key, value) -> result.put(key, value instanceof UnmodifiableConfig child ? values(child) : value));
        return result;
    }

    private static List<String> keyPath(String text) {
        UnmodifiableConfig config = new TomlParser().parse(text.strip() + " = true");
        List<String> result = new ArrayList<>();
        while (true) {
            if (config.valueMap().size() != 1) throw new IllegalArgumentException("Ambiguous TOML key");
            var entry = config.valueMap().entrySet().iterator().next();
            result.add(entry.getKey());
            if (!(entry.getValue() instanceof UnmodifiableConfig child)) break;
            config = child;
        }
        return result;
    }

    private static int outsideQuote(String text, char target) {
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (quote == '"' && c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') quote = c;
            else if (c == target) return i;
            else if (c == '#') return -1;
        }
        return -1;
    }

    private record Line(String content, String ending) {}
    private static List<Line> splitLines(String input) {
        List<Line> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '\n') {
                boolean cr = i > start && input.charAt(i - 1) == '\r';
                lines.add(new Line(input.substring(start, cr ? i - 1 : i), cr ? "\r\n" : "\n"));
                start = i + 1;
            }
        }
        if (start < input.length()) lines.add(new Line(input.substring(start), ""));
        return lines;
    }

    private static final class ScanState {
        private int arrays, braces;
        private String triple;
        boolean complete() { return triple == null && arrays == 0 && braces == 0; }
        void advance(String text) {
            char quote = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (triple != null) {
                    if (triple.charAt(0) == '"' && c == '\\') i++;
                    else if (text.startsWith(triple, i)) { i += 2; triple = null; }
                } else if (quote != 0) {
                    if (quote == '"' && c == '\\') i++;
                    else if (c == quote) quote = 0;
                } else if (c == '#') break;
                else if ((c == '"' || c == '\'') && i + 2 < text.length()
                        && text.charAt(i + 1) == c && text.charAt(i + 2) == c) {
                    triple = text.substring(i, i + 3); i += 2;
                } else if (c == '"' || c == '\'') quote = c;
                else if (c == '[') arrays++;
                else if (c == ']') arrays--;
                else if (c == '{') braces++;
                else if (c == '}') braces--;
            }
        }
    }
}
