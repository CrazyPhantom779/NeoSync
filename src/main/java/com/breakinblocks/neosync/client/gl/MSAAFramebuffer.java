package com.breakinblocks.neosync.client.gl;

import com.google.common.collect.Queues;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL30C;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

@OnlyIn(Dist.CLIENT)
public class MSAAFramebuffer extends RenderTarget {
    public static final int MIN_SAMPLES = 2;
    public static final int MAX_SAMPLES = GL30.glGetInteger(GL30C.GL_MAX_SAMPLES);

    private static final Map<Integer, MSAAFramebuffer> INSTANCES = new HashMap<>();
    private static final List<MSAAFramebuffer> ACTIVE_INSTANCES = new ArrayList<>();
    private static final ConcurrentMap<Integer, ConcurrentLinkedQueue<Runnable>> RENDER_CALLS = new ConcurrentHashMap<>();

    private final int samples;
    private int rboColor;
    private int rboDepth;
    private boolean inUse;

    private MSAAFramebuffer(int samples) {
        super(true);
        if (samples < MIN_SAMPLES || samples > MAX_SAMPLES) {
            throw new IllegalArgumentException(String.format("The number of samples should be >= %s and <= %s.", MIN_SAMPLES, MAX_SAMPLES));
        }
        if ((samples & (samples - 1)) != 0) {
            throw new IllegalArgumentException("The number of samples must be a power of two.");
        }

        this.samples = samples;
        this.setClearColor(1F, 1F, 1F, 0F);
    }

    private static MSAAFramebuffer getInstance(int samples) {
        return INSTANCES.computeIfAbsent(samples, x -> new MSAAFramebuffer(samples));
    }

    public static void use(int samples, Runnable drawAction) {
        use(samples, Minecraft.getInstance().getMainRenderTarget(), drawAction);
    }

    public static void use(int samples, RenderTarget mainBuffer, Runnable drawAction) {
        RenderSystem.assertOnRenderThreadOrInit();
        MSAAFramebuffer msaaBuffer = MSAAFramebuffer.getInstance(samples);
        msaaBuffer.resize(mainBuffer.width, mainBuffer.height, true);

        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, mainBuffer.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, msaaBuffer.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, msaaBuffer.width, msaaBuffer.height, 0, 0, msaaBuffer.width, msaaBuffer.height, GL30C.GL_COLOR_BUFFER_BIT, GL30C.GL_LINEAR);

        msaaBuffer.bindWrite(true);
        drawAction.run();
        msaaBuffer.unbindWrite();

        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, msaaBuffer.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, mainBuffer.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, msaaBuffer.width, msaaBuffer.height, 0, 0, msaaBuffer.width, msaaBuffer.height, GL30C.GL_COLOR_BUFFER_BIT, GL30C.GL_LINEAR);

        msaaBuffer.clear(true);
        mainBuffer.bindWrite(false);

        executeDelayedRenderCalls(samples);
    }

    public static void renderAfterUsage(int samples, Runnable renderCall) {
        if (getInstance(samples).isInUse() || !RenderSystem.isOnRenderThreadOrInit()) {
            RENDER_CALLS.computeIfAbsent(samples, x -> Queues.newConcurrentLinkedQueue()).add(renderCall);
            return;
        }

        renderCall.run();
    }

    public static void renderAfterUsage(Runnable renderCall) {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(() -> renderAfterUsage(renderCall));
        }

        if (ACTIVE_INSTANCES.size() != 0) {
            RENDER_CALLS.computeIfAbsent(ACTIVE_INSTANCES.get(0).samples, x -> Queues.newConcurrentLinkedQueue()).add(renderCall);
            return;
        }

        renderCall.run();
    }

    private static void executeDelayedRenderCalls(int samples) {
        RenderSystem.assertOnRenderThreadOrInit();
        ConcurrentLinkedQueue<Runnable> queue = RENDER_CALLS.getOrDefault(samples, Queues.newConcurrentLinkedQueue());
        while(!queue.isEmpty()) {
            queue.poll().run();
        }
    }

    @Override
    public void resize(int width, int height, boolean getError) {
        if (this.width != width || this.height != height) {
            super.resize(width, height, getError);
        }
    }

    @Override
    public void createBuffers(int width, int height, boolean getError) {
        RenderSystem.assertOnRenderThreadOrInit();
        int maxSize = RenderSystem.maxSupportedTextureSize();
        if (width <= 0 || width > maxSize || height <= 0 || height > maxSize) {
            throw new IllegalArgumentException("Window " + width + "x" + height + " size out of bounds (max. size: " + maxSize + ")");
        }

        this.viewWidth = width;
        this.viewHeight = height;
        this.width = width;
        this.height = height;

        this.frameBufferId = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, this.frameBufferId);

        this.rboColor = GlStateManager.glGenRenderbuffers();
        GlStateManager._glBindRenderbuffer(GL30C.GL_RENDERBUFFER, this.rboColor);
        GL30.glRenderbufferStorageMultisample(GL30C.GL_RENDERBUFFER, samples, GL30C.GL_RGBA8, width, height);
        GlStateManager._glBindRenderbuffer(GL30C.GL_RENDERBUFFER, 0);

        this.rboDepth = GlStateManager.glGenRenderbuffers();
        GlStateManager._glBindRenderbuffer(GL30C.GL_RENDERBUFFER, this.rboDepth);
        GL30.glRenderbufferStorageMultisample(GL30C.GL_RENDERBUFFER, samples, GL30C.GL_DEPTH_COMPONENT, width, height);
        GlStateManager._glBindRenderbuffer(GL30C.GL_RENDERBUFFER, 0);

        GL30.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL30C.GL_RENDERBUFFER, this.rboColor);
        GL30.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_RENDERBUFFER, this.rboDepth);

        this.colorTextureId = Minecraft.getInstance().getMainRenderTarget().getColorTextureId();
        this.depthBufferId = Minecraft.getInstance().getMainRenderTarget().getDepthTextureId();

        this.checkStatus();
        this.clear(getError);
        this.unbindRead();
    }

    @Override
    public void destroyBuffers() {
        RenderSystem.assertOnRenderThreadOrInit();
        this.unbindRead();
        this.unbindWrite();

        if (this.frameBufferId > -1) {
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
            GlStateManager._glDeleteFramebuffers(this.frameBufferId);
            this.frameBufferId = -1;
        }

        if (this.rboColor > -1) {
            GlStateManager._glDeleteRenderbuffers(this.rboColor);
            this.rboColor = -1;
        }

        if (this.rboDepth > -1) {
            GlStateManager._glDeleteRenderbuffers(this.rboDepth);
            this.rboDepth = -1;
        }

        this.colorTextureId = -1;
        this.depthBufferId = -1;
        this.width = -1;
        this.height = -1;
    }

    @Override
    public void bindWrite(boolean setViewport) {
        super.bindWrite(setViewport);
        if (!this.inUse) {
            ACTIVE_INSTANCES.add(this);
            this.inUse = true;
        }
    }

    public boolean isInUse() {
        return this.inUse;
    }

    @Override
    public void unbindWrite() {
        super.unbindWrite();
        if (this.inUse) {
            this.inUse = false;
            ACTIVE_INSTANCES.remove(this);
        }
    }
}