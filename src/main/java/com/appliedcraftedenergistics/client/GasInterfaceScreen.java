// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.client;

import com.appliedcraftedenergistics.ACEText;
import com.appliedcraftedenergistics.device.GasInterfaceBlockEntity;
import com.appliedcraftedenergistics.device.GasInterfaceMenu;
import com.appliedcraftedenergistics.gas.GasAmounts;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.AE2Button;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.IOptionalSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Screen for the ME Gas Interface.
 *
 * <h2>No new art</h2>
 * The screen uses AE2's own widgets and a <b>generated</b> background, so the assets workstream does not
 * have to draw anything. The layout lives in
 * {@code assets/ae2/screens/me_gas_interface.json} (AE2 only resolves style documents in the
 * {@code ae2} namespace, so that file is the one resource this subsystem adds) and relies on:
 *
 * <ul>
 *   <li>{@code generatedBackground} — AE2's procedural dialog panel ({@code BackgroundGenerator}); no
 *       texture file is involved.</li>
 *   <li>{@code common/common.json}, {@code common/player_inventory.json}, {@code common/toolbox.json} —
 *       AE2's shared fragments: colour palette, player inventory + toolbox slot positions, upgrade panel
 *       anchor. The upgrade panel itself is drawn by AE2's {@code UpgradesPanel}.</li>
 *   <li>{@code AE2Button} — AE2's themed button widget (AE2's own widget textures) for the mode and
 *       supply pressure buttons.</li>
 * </ul>
 *
 * <h2>What is shown</h2>
 * <pre>
 *   ME Gas Interface
 *   Buffer     [GasName]  1,500 kGU / 192 kGU
 *   Pressure   187.50 kPa / 2.40 MPa      (current / rated)
 *   Overpressure / Blocked                (only while applicable)
 *   Mode       [ Import &amp; Export ]        (button: click cycles the mode)
 *   Supply     [-] 100 kPa [+]            (the pressure the AE side fills to)
 * </pre>
 *
 * <p>Both controls mirror Applied Pneumatics' ME Pressure Interface idea: the player dials in the
 * pressure the interface works to instead of it being a server config only.</p>
 */
public class GasInterfaceScreen extends UpgradeableScreen<GasInterfaceMenu> {
    private static final String TEXT_TITLE = "dialog_title";
    private static final String TEXT_BUFFER_VALUE = "buffer_value";
    private static final String TEXT_PRESSURE_VALUE = "pressure_value";
    private static final String TEXT_SETPOINT = "setpoint_value";
    private static final String TEXT_STATUS = "status_value";

    private static final String WIDGET_MODE = "mode_button";
    private static final String WIDGET_SUPPLY_DOWN = "supply_down";
    private static final String WIDGET_SUPPLY_UP = "supply_up";

    /** Step of the supply pressure buttons, in kPa (the block entity snaps to the same grid). */
    private static final int SUPPLY_STEP_KPA = GasInterfaceBlockEntity.SUPPLY_PRESSURE_STEP_KPA;

    private final AE2Button modeButton;

    public GasInterfaceScreen(GasInterfaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, StyleManager.loadStyleDoc("/screens/me_gas_interface.json"));

        // Created once; the caption is refreshed in updateBeforeRender and clicking cycles the mode.
        this.modeButton = widgets.addButton(WIDGET_MODE, menu.mode.getDisplayName(), menu::sendCycleMode);
        widgets.addButton(WIDGET_SUPPLY_DOWN, Component.literal("-"),
                () -> menu.sendAdjustSupplyPressure(-SUPPLY_STEP_KPA));
        widgets.addButton(WIDGET_SUPPLY_UP, Component.literal("+"),
                () -> menu.sendAdjustSupplyPressure(SUPPLY_STEP_KPA));
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);

        // AE2's hand-drawn dialogs have their slot frames baked into the background texture. This screen
        // deliberately uses the generated panel instead (see the class javadoc), which has no slot art,
        // so draw AE2's shared 18x18 slot sprite behind the player slots here. Optional slots and the
        // upgrade panel already paint their own backgrounds, and hidden slots are parked off-screen.
        for (Slot slot : this.menu.slots) {
            if (slot.x < 0 || slot.y < 0 || slot instanceof IOptionalSlot) {
                continue;
            }
            if (this.menu.getSlotSemantic(slot) == SlotSemantics.UPGRADE) {
                continue;
            }
            Icon.SLOT_BACKGROUND.getBlitter()
                    .dest(offsetX + slot.x - 1, offsetY + slot.y - 1)
                    .blit(guiGraphics);
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        setTextContent(TEXT_TITLE, menu.getTitle());

        // --- buffer contents: gas name + amount / capacity --------------------------------------
        Component amounts = Component.literal(GasAmounts.format(menu.storedAmount) + " / "
                + GasAmounts.format(menu.maxAmount));
        setTextContent(TEXT_BUFFER_VALUE, menu.gasName == null
                ? amounts
                : Component.empty().append(menu.gasName).append(Component.literal("  ")).append(amounts));

        // --- pressure: current / rated ---------------------------------------------------------
        // GasAmounts.formatPressure delegates to CCB's own formatter (kPa below 1 MPa, MPa above), so
        // the numbers read the same as they do on a CCB gauge.
        setTextContent(TEXT_PRESSURE_VALUE, Component.literal(GasAmounts.formatPressure(menu.pressurePa)
                + " / " + GasAmounts.formatPressure(menu.maxPressurePa)));

        // --- controls ----------------------------------------------------------------------------
        modeButton.setMessage(menu.mode.getDisplayName());
        setTextContent(TEXT_SETPOINT, Component.literal(menu.supplyPressureKpa + " kPa"));

        // --- hints -------------------------------------------------------------------------------
        boolean supplyAboveSafe = menu.supplyAboveSafe;
        boolean atCeiling = menu.atPressureCeiling;
        if (supplyAboveSafe || atCeiling) {
            Component status = Component.empty();
            if (supplyAboveSafe) {
                status = status.copy().append(ACEText.INTERFACE_OVERPRESSURE);
            }
            if (atCeiling) {
                if (supplyAboveSafe) {
                    status = status.copy().append(Component.literal("   "));
                }
                status = status.copy().append(ACEText.INTERFACE_BLOCKED);
            }
            setTextContent(TEXT_STATUS, status);
        }
        setTextHidden(TEXT_STATUS, !(supplyAboveSafe || atCeiling));
    }
}
