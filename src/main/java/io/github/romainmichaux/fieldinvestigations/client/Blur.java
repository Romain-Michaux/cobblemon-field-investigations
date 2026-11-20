package io.github.romainmichaux.fieldinvestigations.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

// A real blur for a Pokémon portrait: the portrait is rendered once into an offscreen texture, then that texture is laid
// down several times around its place, each layer see-through, so the layers average into a soft blur. (Portraits are
// 3D item models: drawn directly they ignore transparency.) With no radius it simply lays the portrait down once, which
// also allows mirroring it (portraits face left).
final class Blur {
    private static TextureTarget target;
    private static int size;

    private Blur() {}

    static void portrait(GuiGraphics g, String species, int x, int y, float scale, double radius) {
        portrait(g, species, x, y, scale, radius, false);
    }

    static void portrait(GuiGraphics g, String species, int x, int y, float scale, double radius, boolean mirror) {
        Minecraft mc = Minecraft.getInstance();
        int pad = (int) Math.ceil(radius) + 2;
        int box = (int) Math.ceil(16 * scale) + pad * 2;
        int gui = (int) Math.max(1, mc.getWindow().getGuiScale());
        int px = box * gui;
        if (target == null || size != px) {
            if (target != null) target.destroyBuffers();
            target = new TextureTarget(px, px, true, Minecraft.ON_OSX);
            size = px;
        }
        // Render the portrait alone into the texture, with a projection in GUI units.
        g.flush();
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        VertexSorting sorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, box, box, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
        GuiGraphics off = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        Draw.portrait(off, species, pad, pad, scale);
        off.flush();
        RenderSystem.setProjectionMatrix(projection, sorting);
        mc.getMainRenderTarget().bindWrite(true);
        // Lay it down: the centre and a ring of offsets, each layer weighted so that together they average.
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int n = radius < 0.5 ? 1 : 10;
        for (int i = 0; i < n; i++) {
            double a = i == 0 ? 0 : i * Math.PI * 2 / (n - 1), r = i == 0 ? 0 : radius * (i % 2 == 0 ? 1 : 0.55);
            float dx = (float) (Math.cos(a) * r), dy = (float) (Math.sin(a) * r);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f / (i + 1));
            quad(g, x - pad + dx, y - pad + dy, box, mirror);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private static void quad(GuiGraphics g, float x, float y, int box, boolean mirror) {
        float u0 = mirror ? 1 : 0, u1 = 1 - u0;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        // The framebuffer's rows go bottom-up: the texture is read upside down.
        b.addVertex(m, x, y, 0).setUv(u0, 1);
        b.addVertex(m, x, y + box, 0).setUv(u0, 0);
        b.addVertex(m, x + box, y + box, 0).setUv(u1, 0);
        b.addVertex(m, x + box, y, 0).setUv(u1, 1);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
}
