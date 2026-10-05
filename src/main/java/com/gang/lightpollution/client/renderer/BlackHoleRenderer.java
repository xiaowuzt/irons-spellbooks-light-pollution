package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.*;
import com.gang.lightpollution.client.BlackHoleShaders;
import com.gang.lightpollution.client.perf.AdaptiveVisualQuality;
import com.gang.lightpollution.client.perf.PerfTracker;
import com.gang.lightpollution.entity.*;
import com.gang.lightpollution.fx.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import java.util.*;

/** Scheme A: mod-owned colour/depth snapshot + scissored half-resolution trace + depth-guided resolve.
 * No GameRenderer patch, external shader pack, framebuffer feedback or client gameplay decisions. */
public final class BlackHoleRenderer {
    private static final BufferBuilder QUAD=new BufferBuilder(256);
    private static final BlackHoleLutTextures LUT=new BlackHoleLutTextures();
    private static final Set<String> FAILED=new HashSet<>();
    private static final Set<UUID> LENSED=new HashSet<>();
    public static boolean hasDedicatedLens(UUID instanceId) { return LENSED.contains(instanceId); }
    private static TextureTarget scene,effect;
    private static ClientLevel world;
    private static boolean targetFailure;
    private BlackHoleRenderer() {}
    public static boolean lowMode() {
        return BlackHoleVisualConfig.mode==BlackHoleVisualConfig.Mode.LOW
                || BlackHoleVisualConfig.mode==BlackHoleVisualConfig.Mode.AUTO && SpellLightConfig.qualityPreset==SpellLightConfig.QualityPreset.LOW;
    }
    public static boolean particleFallback(Entity entity) {
        if (BlackHoleVisualConfig.mode==BlackHoleVisualConfig.Mode.PARTICLES || targetFailure || lowMode()) return true;
        if (entity instanceof RedshiftAbyssEntity) return !RedshiftAbyssRenderer.available();
        if (BlackHoleShaders.get("black_hole_resolve")==null || FAILED.contains("black_hole_low")) return true;
        if (entity instanceof BlackHoleEntity bh) return FAILED.contains(bh.effectId()) || BlackHoleShaders.get(bh.effectId())==null;
        return false;
    }
    public static void render(Matrix4f projection,Matrix4f view,Camera camera,float partial) {
        if (!RenderSystem.isOnRenderThread()) return;
        Minecraft mc=Minecraft.getInstance();
        LENSED.clear();
        if (world!=mc.level) {reset();world=mc.level;}
        List<Entity> visible=new ArrayList<>();
        if (mc.level!=null) for (Entity entity:mc.level.entitiesForRendering()) {
            if (!(entity instanceof BlackHoleInstance)) continue;
            if (entity instanceof BlackHoleEntity bh && !bh.bhReady()) continue;
            if (entity instanceof RedshiftAbyssEntity && !lowMode() && RedshiftAbyssRenderer.available()) continue;
            if (entity.distanceToSqr(camera.getPosition())<=192*192) visible.add(entity);
        }
        if (visible.isEmpty() || BlackHoleVisualConfig.mode==BlackHoleVisualConfig.Mode.PARTICLES) {
            if (scene!=null || effect!=null) try(SceneRenderState ignored=new SceneRenderState(4)){releaseTargets();}
            return;
        }
        RenderTarget main=mc.getMainRenderTarget();
        if (targetFailure || main.width<1 || main.height<1 || main.getDepthTextureId()<0) {targetFailure=true;return;}
        visible.sort(Comparator.comparingDouble(e->e.distanceToSqr(camera.getPosition())));
        if (visible.size()>BlackHoleVisualConfig.maxVisible) visible.subList(BlackHoleVisualConfig.maxVisible,visible.size()).clear();
        // Back-to-front within the bounded selection; this is compositing, not a multi-hole GR solution.
        Collections.reverse(visible);
        long timing=PerfTracker.begin(PerfTracker.Section.CINEMATIC);
        try(SceneRenderState ignored=new SceneRenderState(4)) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            ensureTargets(main);
            Matrix4f rotation=new Matrix4f(view).setTranslation(0,0,0),inverse=new Matrix4f(projection).invert();
            for(Entity entity:visible) {
                String id=entity instanceof BlackHoleEntity bh?bh.effectId():"redshift_abyss";
                boolean low=lowMode() || FAILED.contains(id) || BlackHoleShaders.get(id)==null;
                ShaderInstance shader=BlackHoleShaders.get(low?"black_hole_low":id),resolve=BlackHoleShaders.get("black_hole_resolve");
                if(shader==null || resolve==null) continue;
                BlackHoleParameters p=BlackHoleUniforms.opticalParameters(entity,partial);
                if(p.bhHorizonRadius()<=.005F || p.bhEnvelope()<=.001F) continue;
                float support=p.bhInfluenceRadius()*(entity instanceof RadiantCollapseEntity?2.1F*SpellLightConfig.radiantCollapseVisualScale:1F);
                var center=BlackHoleUniforms.eye(rotation,p.bhCenter().subtract(camera.getPosition()));
                var bounds=ScreenEffectBounds.sphere(projection,center,support,effect.width,effect.height);
                if(bounds.empty()) continue;
                try {
                    copyScene(main);fullScreenState();
                    effect.setClearColor(0,0,0,0);effect.clear(Minecraft.ON_OSX);effect.bindWrite(true);
                    RenderSystem.enableScissor(bounds.x(),bounds.y(),bounds.width(),bounds.height());
                    shader.setSampler("bhSceneSampler",scene.getColorTextureId());shader.setSampler("bhDepthSampler",scene.getDepthTextureId());
                    BlackHoleUniforms.upload(shader,p,projection,inverse,rotation,camera.getPosition(),main.width,main.height,support,steps());
                    if(entity instanceof SchwarzschildLensEntity e) {
                        SchwarzschildLensRenderer.prepare(shader,e,partial);
                        if(!low) {shader.setSampler("bhDeflectionSampler",LUT.deflection());shader.setSampler("bhInverseRadiusSampler",LUT.inverse());}
                    } else if(entity instanceof RadiantCollapseEntity e) RadiantCollapseRenderer.prepare(shader,e,partial);
                    else if(entity instanceof StasisSingularityEntity e) StasisSingularityRenderer.prepare(shader,e,partial);
                    else BlackHoleUniforms.vec4(shader,"bhEffect",0,0,0,4);
                    // LUT creation can bind textures but does not change the target framebuffer.
                    draw(shader);
                    main.bindWrite(true);
                    var full=ScreenEffectBounds.sphere(projection,center,support,main.width,main.height);
                    RenderSystem.enableScissor(full.x(),full.y(),full.width(),full.height());
                    resolve.setSampler("bhSceneSampler",scene.getColorTextureId());resolve.setSampler("bhDepthSampler",scene.getDepthTextureId());
                    resolve.setSampler("bhEffectSampler",effect.getColorTextureId());
                    BlackHoleUniforms.upload(resolve,p,projection,inverse,rotation,camera.getPosition(),main.width,main.height,support,steps());
                    draw(resolve);
                    if (entity instanceof SchwarzschildLensEntity) LENSED.add(p.bhInstanceId());
                } catch(java.io.IOException | RuntimeException failure) {
                    if(QUAD.building()) QUAD.end().release();
                    if (low) FAILED.add("black_hole_low");
                    if(FAILED.add(id)) ExampleMod.LOGGER.warn("Black-hole {} GLSL disabled until F3+T; particle/low fallback remains active",id,failure);
                }
            }
        } catch(RuntimeException failure) {
            targetFailure=true;ExampleMod.LOGGER.warn("Black-hole render targets unavailable; particle fallback active until resource reload",failure);
        } finally {PerfTracker.end(PerfTracker.Section.CINEMATIC,timing);}
    }
    private static int steps() {
        int count=BlackHoleVisualConfig.mode==BlackHoleVisualConfig.Mode.HIGH?400:switch(SpellLightConfig.qualityPreset){case LOW->96;case MEDIUM->192;case HIGH->320;case ULTRA->400;};
        return Math.max(80,Math.min(400,AdaptiveVisualQuality.volumeSteps(count)));
    }
    private static void ensureTargets(RenderTarget main) {
        if(scene!=null && (scene.width!=main.width || scene.height!=main.height || scene.isStencilEnabled()!=main.isStencilEnabled())) releaseTargets();
        if(scene==null) {scene=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);if(main.isStencilEnabled())scene.enableStencil();}
        float scale=lowMode()?.5F:.67F;
        scale=Math.min(scale*AdaptiveVisualQuality.volumeResolutionScale(),1024F/Math.max(1,main.width));
        int width=Math.max(1,Math.round(main.width*scale)),height=Math.max(1,Math.round(main.height*scale));
        if(effect!=null && (effect.width!=width || effect.height!=height)){effect.destroyBuffers();effect=null;}
        if(effect==null) {
            effect=new TextureTarget(width,height,false,Minecraft.ON_OSX);
            GlStateManager._bindTexture(effect.getColorTextureId());
            TextureUpload.image2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA16F,width,height,0,GL11.GL_RGBA,GL11.GL_FLOAT,(java.nio.ByteBuffer)null);
            effect.setFilterMode(GL11.GL_NEAREST);effect.bindWrite(true);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Black-hole HDR framebuffer incomplete");
        }
    }
    private static void copyScene(RenderTarget main) {
        RenderSystem.disableScissor();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);
        GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,scene.width,scene.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
    }
    private static void fullScreenState() {
        RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
        RenderSystem.disableCull();RenderSystem.colorMask(true,true,true,true);RenderSystem.disableScissor();
    }
    private static void draw(ShaderInstance shader) {
        RenderSystem.setShader(()->shader);QUAD.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
        QUAD.vertex(-1,-1,0).uv(0,0).endVertex();QUAD.vertex(1,-1,0).uv(1,0).endVertex();
        QUAD.vertex(1,1,0).uv(1,1).endVertex();QUAD.vertex(-1,1,0).uv(0,1).endVertex();
        int gpu=PerfTracker.beginGpu(PerfTracker.Section.CINEMATIC);
        try{BufferUploader.drawWithShader(QUAD.end());}finally{PerfTracker.endGpu(gpu);}
    }
    private static void releaseTargets(){if(scene!=null)scene.destroyBuffers();if(effect!=null)effect.destroyBuffers();scene=effect=null;}
    public static void reset() {
        if(!RenderSystem.isOnRenderThread()){RenderSystem.recordRenderCall(BlackHoleRenderer::reset);return;}
        try(SceneRenderState ignored=new SceneRenderState(4)){
            releaseTargets();LUT.close();
        }
        FAILED.clear();LENSED.clear();targetFailure=false;world=null;
    }
}
