package com.cobblemonpokerogue.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;

/**
 * Fullscreen in-game Chromium browser hosting PokeRogue. Adapted from MCEF's own
 * ExampleScreen (LGPL example in CinemaMod/mcef, 1.21.1 branch) with the 20px inset
 * removed — PokeRogue wants the whole surface — and an init guard, since MCEF
 * downloads its CEF natives on first client launch and may not be ready yet.
 *
 * This class is the ONLY place MCEF types appear; callers must not classload it
 * unless ModList.isLoaded("mcef") is true.
 */
public final class PokerogueScreen extends Screen {
    private MCEFBrowser browser;
    private final String url;

    private PokerogueScreen(String url) {
        super(Component.literal("PokeRogue"));
        this.url = url;
    }

    /** Entry point; checks MCEF readiness and shows the screen. */
    public static void open(Minecraft minecraft, String url) {
        if (!MCEF.isInitialized()) {
            // CEF natives still downloading (first launch) or init failed.
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(
                        "PokeRogue browser not ready: MCEF/Chromium is still initializing "
                                + "(first launch downloads the browser natives). Try again shortly."), false);
            }
            return;
        }
        minecraft.setScreen(new PokerogueScreen(url));
    }

    @Override
    protected void init() {
        super.init();
        if (browser == null) {
            // transparent=false: PokeRogue paints its own opaque background; an opaque
            // buffer avoids the game world bleeding through during page load.
            browser = MCEF.createBrowser(url, false);
        }
        resizeBrowser();
    }

    // The browser works in physical pixels; the Screen works in gui-scaled units.
    private int toBrowserX(double x) {
        return (int) (x * minecraft.getWindow().getGuiScale());
    }

    private int toBrowserY(double y) {
        return (int) (y * minecraft.getWindow().getGuiScale());
    }

    private void resizeBrowser() {
        if (width > 50 && height > 50) {
            browser.resize(toBrowserX(width), toBrowserY(height));
        }
    }

    @Override
    public void resize(Minecraft minecraft, int newWidth, int newHeight) {
        super.resize(minecraft, newWidth, newHeight);
        resizeBrowser();
    }

    @Override
    public void onClose() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        super.onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // ESC closes and releases the browser (Screen default, kept explicit —
        // it's part of the spike contract).
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        // Don't freeze singleplayer while the roguelike run is up; on a server this
        // is moot but keeps behavior consistent.
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (browser == null) {
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, browser.getRenderer().getTextureID());
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(0, height, 0).setUv(0.0f, 1.0f).setColor(255, 255, 255, 255);
        buffer.addVertex(width, height, 0).setUv(1.0f, 1.0f).setColor(255, 255, 255, 255);
        buffer.addVertex(width, 0, 0).setUv(1.0f, 0.0f).setColor(255, 255, 255, 255);
        buffer.addVertex(0, 0, 0).setUv(0.0f, 0.0f).setColor(255, 255, 255, 255);
        BufferUploader.drawWithShader(buffer.build());
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableDepthTest();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        browser.sendMousePress(toBrowserX(mouseX), toBrowserY(mouseY), button);
        browser.setFocus(true);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        browser.sendMouseRelease(toBrowserX(mouseX), toBrowserY(mouseY), button);
        browser.setFocus(true);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        browser.sendMouseMove(toBrowserX(mouseX), toBrowserY(mouseY));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        browser.sendMouseWheel(toBrowserX(mouseX), toBrowserY(mouseY), scrollY, 0);
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // PokeRogue is keyboard-driven: forward everything, keep Chromium focused.
        browser.sendKeyPress(keyCode, scanCode, modifiers);
        browser.setFocus(true);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        browser.sendKeyRelease(keyCode, scanCode, modifiers);
        browser.setFocus(true);
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint == (char) 0) {
            return false;
        }
        browser.sendKeyTyped(codePoint, modifiers);
        browser.setFocus(true);
        return super.charTyped(codePoint, modifiers);
    }
}
