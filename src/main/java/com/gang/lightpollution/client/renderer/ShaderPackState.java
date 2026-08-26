package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;

import java.lang.reflect.Method;

/**
 * Tells whether a shader pack (Oculus/Iris) is currently driving the pipeline.
 *
 * <p>Everything this mod draws goes through its own core shaders, and Iris's own
 * documentation is blunt about that: <em>"Custom shaders added by mods or resource
 * packs are ignored by Iris when an Iris shader pack is loaded."</em> So the mod has
 * to know when it is in that situation and degrade instead of drawing something
 * wrong.</p>
 *
 * <p>Resolved entirely by reflection. A hard dependency would mean the mod could not
 * load without Oculus installed, and a compile-time one would mean shipping against
 * an API whose package name has moved at least once (Iris renamed
 * {@code net.coderbot.iris} to {@code net.irisshaders.iris}, and Oculus lags behind
 * upstream). Both spellings are tried.</p>
 *
 * <p>Note the distinction that matters: <em>installed</em> is not <em>in use</em>.
 * A player with Oculus installed but no pack selected must still get the full-quality
 * path, so this asks the API for the live state rather than checking the mod list.</p>
 */
public final class ShaderPackState {
    /** Candidate API entry points, newest naming first. */
    private static final String[] API_CLASSES = {
            "net.irisshaders.iris.api.v0.IrisApi",
            "net.coderbot.iris.api.v0.IrisApi",
    };
    /** Method names that have carried this meaning across versions. */
    private static final String[] IN_USE_METHODS = {
            "isShaderPackInUse", "isShaderPackEnabled",
    };

    private static boolean resolved;
    private static Object api;
    private static Method inUse;
    private static Method shadowPass;

    private ShaderPackState() {
    }

    /**
     * True when a pack is actually rendering. False when none is selected, and false
     * when no shader mod is installed at all.
     */
    public static boolean packActive() {
        resolve();
        if (api == null || inUse == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(inUse.invoke(api));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            // Do not keep retrying a call that has already thrown: this runs inside
            // the render loop.
            ExampleMod.LOGGER.warn(
                    "Shader-pack query failed, assuming none is active: {}", failure);
            api = null;
            inUse = null;
            return false;
        }
    }

    /** True when a shader mod is present, whether or not a pack is selected. */
    public static boolean shaderModPresent() {
        resolve();
        return api != null;
    }

    /**
     * True while the pack is rendering its shadow map rather than the camera view.
     *
     * <p>This matters more than it looks. Iris renders the world a second time from
     * the light's point of view to build the shadow map, and Forge's render-stage
     * events fire during that pass as well — so anything drawn without checking would
     * be emitted twice per frame, once into a buffer where it makes no sense.
     * Additive glows have no business writing depth into a shadow map.</p>
     */
    public static boolean renderingShadowPass() {
        resolve();
        if (shadowPass == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(shadowPass.invoke(api));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            shadowPass = null;
            return false;
        }
    }

    /** What was found, for the diagnostic dump. */
    public static String describe() {
        resolve();
        if (api == null) {
            return "no Iris/Oculus API found";
        }
        return api.getClass().getName() + "." + inUse.getName() + "() -> " + packActive();
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        for (String className : API_CLASSES) {
            try {
                Class<?> type = Class.forName(className);
                Object instance = type.getMethod("getInstance").invoke(null);
                for (String methodName : IN_USE_METHODS) {
                    try {
                        Method method = type.getMethod(methodName);
                        api = instance;
                        inUse = method;
                        try {
                            shadowPass = type.getMethod("isRenderingShadowPass");
                        } catch (NoSuchMethodException absent) {
                            shadowPass = null;
                        }
                        ExampleMod.LOGGER.info(
                                "Shader-pack detection bound to {}.{}(){}",
                                className, methodName,
                                shadowPass == null ? " (no shadow-pass query)" : "");
                        return;
                    } catch (NoSuchMethodException ignored) {
                        // Try the next spelling.
                    }
                }
            } catch (ClassNotFoundException absent) {
                // Expected when no shader mod is installed.
            } catch (ReflectiveOperationException | RuntimeException failure) {
                ExampleMod.LOGGER.warn("Found {} but could not use it: {}",
                        className, failure);
            }
        }
    }
}
