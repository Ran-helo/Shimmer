package com.lowdragmc.shimmer.core.mixins;

import com.lowdragmc.shimmer.client.postprocessing.IPostParticleType;
import com.lowdragmc.shimmer.client.postprocessing.PostProcessing;
import com.lowdragmc.shimmer.core.IParticleEngine;
import com.lowdragmc.shimmer.platform.Services;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.ParticleOptions;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Queue;

/**
 * @author KilaBash
 * @date 2022/05/02
 * @implNote ParticleEngineMixin, inject particle postprocessing
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin implements IParticleEngine {
    @Shadow @Nullable
    protected abstract <T extends ParticleOptions> Particle makeParticle(T pParticleData, double pX, double pY, double pZ, double pXSpeed, double pYSpeed, double pZSpeed);

    @Shadow public abstract void add(Particle pEffect);

    @Shadow @Final private Map<ParticleRenderType, Queue<Particle>> particles;

    @Nullable
    public Particle createPostParticle(PostProcessing postProcessing, ParticleOptions pParticleData, double pX, double pY, double pZ, double pXSpeed, double pYSpeed, double pZSpeed) {
        Particle particle = makeParticle(pParticleData, pX, pY, pZ, pXSpeed, pYSpeed, pZSpeed);
        if (particle != null) {
            particle = Services.PLATFORM.createPostParticle(particle, postProcessing);
            add(particle);
            return particle;
        } else {
            return null;
        }
    }

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleRenderType;begin(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lnet/minecraft/client/renderer/texture/TextureManager;)V"))
    private void injectRenderPre(ParticleRenderType particlerendertype, BufferBuilder bufferBuilder, TextureManager textureManager){
        if (particlerendertype instanceof IPostParticleType && this.particles.get(particlerendertype).size() > 0) {
            PostProcessing postProcessing = ((IPostParticleType) particlerendertype).getPost();
            postProcessing.getPostTarget().bindWrite(false);
            postProcessing.hasParticle();
        }
        particlerendertype.begin(bufferBuilder, textureManager);
    }

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleRenderType;end(Lcom/mojang/blaze3d/vertex/Tesselator;)V"))
    private void injectRenderPost(ParticleRenderType particlerendertype, Tesselator tesselator){
        particlerendertype.end(tesselator);
        if (particlerendertype instanceof IPostParticleType) {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        }
    }

    @Inject(method = "render",
            at = @At(value = "RETURN"), remap = false)
    private void injectRenderReturn(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LightTexture lightTexture, Camera camera, float partialTicks, CallbackInfo ci) {
        for (PostProcessing postProcessing : PostProcessing.values()) {
            postProcessing.renderParticlePost();
        }
    }
}
