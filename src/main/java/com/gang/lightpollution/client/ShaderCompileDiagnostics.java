package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Recovers the real GLSL error when a shader fails to build.
 *
 * <p>Oculus/Iris mixes into {@link com.mojang.blaze3d.shaders.Program}'s compile
 * path to replace Minecraft's terse failure with its own
 * {@code ShaderCompileException}, but it calls {@code cancel()} on a callback
 * that is not cancellable. Mixin's own {@code CancellationException} is thrown
 * first, so the driver's info log — the only thing that says which line is
 * wrong — is destroyed before it is ever read. The user-visible result is
 * "The call m_166612_ is not cancellable" and no way to act on it.
 *
 * <p>Compiling the same source again through raw GL bypasses that mixin
 * entirely, so the info log survives. This runs only after a failure, so it
 * costs nothing on a healthy launch.</p>
 */
final class ShaderCompileDiagnostics {
    private ShaderCompileDiagnostics() {
    }

    /** Logs the driver's info log for whichever stage of {@code id} fails. */
    static void report(ResourceProvider resources, ResourceLocation id, Throwable failure) {
        ExampleMod.LOGGER.error("Shader {} failed to build: {}", id, describe(failure));
        for (String stage : List.of("vsh", "fsh")) {
            ResourceLocation source = ResourceLocation.fromNamespaceAndPath(
                    id.getNamespace(), "shaders/core/" + id.getPath() + "." + stage);
            try {
                compileForDiagnostics(resources, source, stage);
            } catch (IOException | RuntimeException probeFailure) {
                ExampleMod.LOGGER.error("  could not re-compile {} for diagnosis: {}",
                        source, probeFailure.toString());
            }
        }
    }

    /** Unwraps the cause chain, which is where the useful message usually hides. */
    private static String describe(Throwable failure) {
        StringBuilder out = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (out.length() > 0) {
                out.append(" <- ");
            }
            out.append(current.getClass().getSimpleName()).append(": ")
                    .append(current.getMessage());
            if (current.getCause() == current) {
                break;
            }
        }
        return out.toString();
    }

    private static void compileForDiagnostics(ResourceProvider resources,
                                              ResourceLocation source, String stage)
            throws IOException {
        String resolved = String.join("\n",
                new ImportResolver(resources, source).process(read(resources, source)));

        int shader = GL20.glCreateShader(stage.equals("vsh")
                ? GL20.GL_VERTEX_SHADER : GL20.GL_FRAGMENT_SHADER);
        try {
            GL20.glShaderSource(shader, resolved);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != 0) {
                ExampleMod.LOGGER.error("  {} compiles cleanly on its own ({} lines)",
                        stage, resolved.split("\n", -1).length);
                return;
            }
            ExampleMod.LOGGER.error("  {} GLSL error: {}", stage,
                    GL20.glGetShaderInfoLog(shader, 32_768).trim());
            logNumberedSource(resolved);
        } finally {
            GL20.glDeleteShader(shader);
        }
    }

    /**
     * Prints the fully expanded source with line numbers. The driver reports
     * lines of the POST-import text, which no file on disk matches, so without
     * this the error's line number cannot be located.
     */
    private static void logNumberedSource(String resolved) {
        String[] lines = resolved.split("\n", -1);
        ExampleMod.LOGGER.error("  --- expanded source, {} lines ---", lines.length);
        for (int i = 0; i < lines.length; i++) {
            ExampleMod.LOGGER.error("  {}| {}", i + 1, lines[i]);
        }
    }

    private static String read(ResourceProvider resources, ResourceLocation location)
            throws IOException {
        try (InputStream stream = resources.open(location)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Resolves {@code #moj_import} the same way Minecraft does, guarding against
     * the cycles a hand-written include set can contain.
     */
    private static final class ImportResolver extends GlslPreprocessor {
        private final ResourceProvider resources;
        private final Set<ResourceLocation> seen = new java.util.HashSet<>();
        private final ResourceLocation origin;

        private ImportResolver(ResourceProvider resources, ResourceLocation origin) {
            this.resources = resources;
            this.origin = origin;
            this.seen.add(origin);
        }

        @Override
        public String applyImport(boolean useFullPath, String name) {
            // An import may name its own namespace regardless of which bracket
            // form was used -- "#moj_import <ns:file.glsl>" arrives here with
            // useFullPath false and the namespace still inside the name, so
            // prefixing the include directory blindly produces a path with a
            // colon in it and the location fails to parse.
            String namespace = origin.getNamespace();
            String path = name;
            int colon = name.indexOf(':');
            if (colon > 0) {
                namespace = name.substring(0, colon);
                path = name.substring(colon + 1);
            }

            ResourceLocation full;
            if (useFullPath && colon < 0) {
                ResourceLocation parsed = ResourceLocation.tryParse(name);
                if (parsed == null) {
                    return "#error unresolvable import " + name;
                }
                full = ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(),
                        "shaders/include/" + parsed.getPath());
            } else {
                full = ResourceLocation.fromNamespaceAndPath(namespace,
                        "shaders/include/" + path);
            }
            if (!seen.add(full)) {
                return "";
            }
            try {
                return String.join("\n", process(read(resources, full)));
            } catch (IOException missing) {
                return "#error missing import " + full;
            }
        }
    }
}
