// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.client;

import appeng.api.client.AEKeyRenderHandler;
import com.appliedcraftedenergistics.gas.GasKey;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.Level;
import net.ty.createcraftedbeginning.api.gas.Gas;

/**
 * Draws a gas stack inside AE2 terminals, pattern slots and tooltips.
 *
 * <p>AE2 requires a render handler for every storage channel, otherwise rendering a terminal that
 * contains the key throws. Gases have no item form, so the icon is synthesised from the gas' own
 * tint: a soft "vapour" cluster rather than an item sprite.</p>
 */
public final class GasKeyRenderHandler implements AEKeyRenderHandler<GasKey> {
    private static final int ICON_SIZE = 16;

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics graphics, int x, int y, GasKey key) {
        Gas gas = key.getGas();
        int tint = gas == null || gas.isEmpty() ? 0xFF9AA7B0 : gas.getTint();
        int rgb = FastColor.ARGB32.color(255, tint);
        int light = FastColor.ARGB32.color(255, mix(tint, 0xFFFFFF, 0.45f));
        int dark = FastColor.ARGB32.color(140, mix(tint, 0x000000, 0.35f));

        // A soft cluster of three puffs, so different gases are distinguishable by hue.
        graphics.fill(x + 2, y + 6, x + 10, y + 14, dark);
        graphics.fill(x + 5, y + 2, x + 14, y + 11, rgb);
        graphics.fill(x + 3, y + 4, x + 11, y + 12, rgb);
        graphics.fill(x + 6, y + 4, x + 9, y + 7, light);
        graphics.fill(x + 4, y + 7, x + 6, y + 9, light);
        graphics.fill(x + 2, y + 13, x + 10, y + 14, dark);
        graphics.fill(x + 5, y + 12, x + 14, y + 13, dark);
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource buffers, GasKey what, float scale,
            int combinedLight, Level level) {
        // Gases are not drawn on block faces (no screens/monitors requirement in v0.1).
    }

    @Override
    public Component getDisplayName(GasKey stack) {
        return stack.getDisplayName();
    }

    private static int mix(int argb, int otherRgb, float ratio) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int or = (otherRgb >> 16) & 0xFF;
        int og = (otherRgb >> 8) & 0xFF;
        int ob = otherRgb & 0xFF;
        r = (int) (r + (or - r) * ratio);
        g = (int) (g + (og - g) * ratio);
        b = (int) (b + (ob - b) * ratio);
        return (r << 16) | (g << 8) | b;
    }

    static int iconSize() {
        return ICON_SIZE;
    }
}
