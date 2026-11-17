package tools.gtnh.calc.export;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/** Called only during RenderTickEvent.END, with Minecraft's GL context current. */
final class IconRenderer {
    private static final int SIZE = 64;

    private IconRenderer() {}

    static BufferedImage render(ItemStack stack, FluidStack fluid) throws IOException {
        if (!OpenGlHelper.isFramebufferEnabled()) {
            throw new IllegalStateException("Framebuffer support is disabled");
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        float brightnessX = OpenGlHelper.lastBrightnessX;
        float brightnessY = OpenGlHelper.lastBrightnessY;
        int framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int renderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        boolean separateBuffers = GLContext.getCapabilities().OpenGL30 || GLContext.getCapabilities().GL_ARB_framebuffer_object;
        int readFramebuffer = separateBuffers ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) : framebuffer;
        boolean shaders = GLContext.getCapabilities().OpenGL20;
        int program = shaders ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
        Framebuffer target = null;
        BufferedImage image;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT | GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glLoadIdentity();
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glLoadIdentity();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            if (shaders) {
                GL20.glUseProgram(0);
            }
            target = new Framebuffer(SIZE, SIZE, true);
            target.setFramebufferColor(0, 0, 0, 0);
            target.framebufferClear();
            target.bindFramebuffer(true);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0, 16, 16, 0, -1000, 1000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glColor4f(1, 1, 1, 1);
            RenderHelper.enableGUIStandardItemLighting();
            if (fluid == null) {
                new RenderItem().renderItemAndEffectIntoGUI(minecraft.fontRenderer, minecraft.getTextureManager(), stack, 0, 0);
            } else {
                RenderHelper.disableStandardItemLighting();
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                IIcon icon = fluid.getFluid().getIcon(fluid);
                if (icon == null) throw new IOException("Fluid has no icon");
                minecraft.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
                int color = fluid.getFluid().getColor(fluid);
                GL11.glColor4f(((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f, (color & 255) / 255f, 1f);
                Tessellator t = Tessellator.instance;
                t.startDrawingQuads();
                t.addVertexWithUV(0, 16, 0, icon.getMinU(), icon.getMaxV());
                t.addVertexWithUV(16, 16, 0, icon.getMaxU(), icon.getMaxV());
                t.addVertexWithUV(16, 0, 0, icon.getMaxU(), icon.getMinV());
                t.addVertexWithUV(0, 0, 0, icon.getMinU(), icon.getMinV());
                t.draw();
            }
            ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int pixel = (y * SIZE + x) * 4;
                    int r = pixels.get(pixel) & 255;
                    int g = pixels.get(pixel + 1) & 255;
                    int b = pixels.get(pixel + 2) & 255;
                    int a = pixels.get(pixel + 3) & 255;
                    image.setRGB(x, SIZE - y - 1, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
        } finally {
            if (target != null) {
                target.deleteFramebuffer();
            }
            OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, framebuffer);
            if (separateBuffers) {
                OpenGlHelper.func_153171_g(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            }
            OpenGlHelper.func_153176_h(OpenGlHelper.field_153199_f, renderbuffer);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glPopMatrix();
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glPopMatrix();
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);
            OpenGlHelper.setActiveTexture(activeTexture);
            if (shaders) {
                GL20.glUseProgram(program);
            }
            GL11.glMatrixMode(matrixMode);
        }
        return AtlasImages.halfSize(image);
    }
}
