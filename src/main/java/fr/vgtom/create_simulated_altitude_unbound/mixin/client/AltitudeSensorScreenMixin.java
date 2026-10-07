package fr.vgtom.create_simulated_altitude_unbound.mixin.client;

import java.util.regex.Pattern;

import dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorBlockEntity;
import dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorScreen;
import fr.vgtom.create_simulated_altitude_unbound.AltitudeUnbound;
import fr.vgtom.create_simulated_altitude_unbound.content.AltitudeRange;
import fr.vgtom.create_simulated_altitude_unbound.duck.CalibratableSensor;
import fr.vgtom.create_simulated_altitude_unbound.duck.ScrollableSensorScreen;
import fr.vgtom.create_simulated_altitude_unbound.network.ConfigureAltitudeRangePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts the altitude scale on Simulated's own sensor screen: one box above the gauge for the top of
 * the scale, one below for the bottom.
 *
 * <p>Left blank, a box means "whatever the world builds to", which is what the sensor did before
 * this mod and what the grey hint in the box shows. Typing in either one recalibrates the gauge
 * under the cursor straight away — the altitudes printed beside the two handles are read back
 * through the sensor, so they re-scale as the text changes, with nothing to confirm.
 *
 * <p>The handles themselves are untouched. They pick a window within the sensor's 0..1 scale, which
 * stays the right control once the scale itself reaches high enough; the wheel now drives them too.
 */
@Mixin(AltitudeSensorScreen.class)
public abstract class AltitudeSensorScreenMixin extends Screen implements ScrollableSensorScreen {
    /** One notch moves a handle by a single redstone level, which is what the bar is graduated in. */
    @Unique
    private static final float ALTITUDE_UNBOUND$STEP = 1.0F / 15.0F;

    /** With shift held, for picking out an altitude the coarse step steps straight over. */
    @Unique
    private static final float ALTITUDE_UNBOUND$FINE_STEP = 1.0F / 200.0F;

    @Unique
    private static final Pattern ALTITUDE_UNBOUND$ALTITUDE = Pattern.compile("-?\\d{0,7}");

    @Unique
    private static final int ALTITUDE_UNBOUND$TEXT = 0xE0E0E0;

    @Unique
    private static final int ALTITUDE_UNBOUND$BAD_TEXT = 0xFF5555;

    /** Create's wooden readout palette: a dark edge, a lit bevel, and a well sunk into it. */
    @Unique
    private static final int ALTITUDE_UNBOUND$EDGE = 0xFF2B2117;

    @Unique
    private static final int ALTITUDE_UNBOUND$BEVEL = 0xFF8C6E48;

    @Unique
    private static final int ALTITUDE_UNBOUND$WELL = 0xFF231B12;

    @Unique
    private static final int ALTITUDE_UNBOUND$HINT = 0x7A5A34;

    @Unique
    private static final int ALTITUDE_UNBOUND$FRAME_WIDTH = 62;

    @Unique
    private static final int ALTITUDE_UNBOUND$FRAME_HEIGHT = 15;

    /** Height of the text line itself; the frame adds two pixels of border above and below. */
    @Unique
    private static final int ALTITUDE_UNBOUND$FIELD_HEIGHT = 11;

    /** Left edge of the icon, measured from the frame's own left edge. */
    @Unique
    private static final int ALTITUDE_UNBOUND$ICON_X = 3;

    /**
     * Where the digits are centred, measured from the frame's left edge: the middle of the frame, so
     * the icon sits under the left end of the line rather than pushing it off centre. At the full six
     * characters the line just clears the icon.
     */
    @Unique
    private static final int ALTITUDE_UNBOUND$TEXT_CENTRE = ALTITUDE_UNBOUND$FRAME_WIDTH / 2;

    /** Widest a handle's altitude may be drawn: the handle texture is 26 across. */
    @Unique
    private static final int ALTITUDE_UNBOUND$HANDLE_TEXT_WIDTH = 24;

    /** Top of the text line, measured from the frame's top edge. An unbordered EditBox does not centre it. */
    @Unique
    private static final int ALTITUDE_UNBOUND$TEXT_Y = 3;

    @Shadow
    @Final
    private AltitudeSensorBlockEntity blockEntity;

    @Shadow
    @Final
    private LerpedFloat visualHighSignal;

    @Shadow
    @Final
    private LerpedFloat visualLowSignal;

    @Shadow
    private float highSignal;

    @Shadow
    private float lowSignal;

    @Shadow
    private int barLeft;

    @Shadow
    private int barTop;

    // Private shadows cannot be abstract; mixin replaces these bodies with the real ones.
    @Shadow
    private boolean overBar(final double mouseX, final double mouseY, final boolean left) {
        throw new AssertionError();
    }

    @Shadow
    private boolean overGrabby(final double mouseX, final double mouseY, final boolean left) {
        throw new AssertionError();
    }

    @Shadow
    public abstract boolean outOfBounds(float value);

    @Unique
    private EditBox altitudeUnbound$maxBox;

    @Unique
    private EditBox altitudeUnbound$minBox;

    /**
     * The altitudes the two handles stand at, which is what survives a change of scale.
     *
     * <p>Remembered rather than read back from the handles each time, because the boxes apply on
     * every keystroke: typing "1000" one digit at a time passes through a scale that tops out at 1,
     * and a handle clamped there would have lost where it really was by the time the zeroes arrive.
     */
    @Unique
    private float altitudeUnbound$lowAltitude;

    @Unique
    private float altitudeUnbound$highAltitude;

    @Unique
    private int altitudeUnbound$maxFrameY;

    @Unique
    private int altitudeUnbound$minFrameY;

    private AltitudeSensorScreenMixin(final Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void altitudeUnbound$addRangeBoxes(final CallbackInfo ci) {
        final AltitudeRange saved = ((CalibratableSensor) this.blockEntity).altitudeUnbound$getRange();
        final boolean custom = saved.isUsable();

        // The gauge is a fixed 200 tall drawn around the screen centre, so the frames hang off that
        // rather than off the window corner: top of the scale above it, bottom of the scale below.
        // Both clear the handles, which stick 8px past either end of the bar.
        this.altitudeUnbound$maxFrameY = this.barTop - 25;
        this.altitudeUnbound$minFrameY = this.barTop + 210;

        this.altitudeUnbound$maxBox = altitudeUnbound$box("max",
                altitudeUnbound$worldMax(), custom ? Math.round(saved.max()) : null);
        this.altitudeUnbound$minBox = altitudeUnbound$box("min",
                altitudeUnbound$worldMin(), custom ? Math.round(saved.min()) : null);

        // Only once both exist: setValue fires the responder, which reads them both.
        this.altitudeUnbound$maxBox.setResponder(text -> altitudeUnbound$rangeEdited());
        this.altitudeUnbound$minBox.setResponder(text -> altitudeUnbound$rangeEdited());

        altitudeUnbound$layout();

        addRenderableWidget(this.altitudeUnbound$maxBox);
        addRenderableWidget(this.altitudeUnbound$minBox);

        altitudeUnbound$rememberAltitudes();
    }

    /**
     * Frames both boxes before they draw themselves. Widgets render after {@code renderWindow}, so
     * the well goes down here and the digits land on top of it.
     */
    @Inject(method = "renderWindow", at = @At("TAIL"))
    private void altitudeUnbound$renderFields(final GuiGraphics graphics, final int mouseX, final int mouseY,
                                              final float partialTicks, final CallbackInfo ci) {
        altitudeUnbound$renderField(graphics, this.altitudeUnbound$maxFrameY, true);
        altitudeUnbound$renderField(graphics, this.altitudeUnbound$minFrameY, false);
    }

    @Unique
    private void altitudeUnbound$renderField(final GuiGraphics graphics, final int top, final boolean up) {
        final int left = altitudeUnbound$frameLeft();
        final int right = left + ALTITUDE_UNBOUND$FRAME_WIDTH;
        final int bottom = top + ALTITUDE_UNBOUND$FRAME_HEIGHT;

        graphics.fill(left, top, right, bottom, ALTITUDE_UNBOUND$EDGE);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, ALTITUDE_UNBOUND$BEVEL);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 2, ALTITUDE_UNBOUND$WELL);

        altitudeUnbound$renderLimitIcon(graphics, left + ALTITUDE_UNBOUND$ICON_X,
                top + ALTITUDE_UNBOUND$TEXT_Y, up);

        final EditBox box = up ? this.altitudeUnbound$maxBox : this.altitudeUnbound$minBox;
        if (box.getValue().isEmpty()) {
            // Under the widget, so a focused box still blinks its caret over the top of the hint.
            graphics.drawString(this.font, String.valueOf(up ? altitudeUnbound$worldMax() : altitudeUnbound$worldMin()),
                    box.getX(), box.getY(), ALTITUDE_UNBOUND$HINT, true);
        }
    }

    @Unique
    private int altitudeUnbound$frameLeft() {
        return this.width / 2 - ALTITUDE_UNBOUND$FRAME_WIDTH / 2;
    }

    /**
     * Sizes each box to the text it holds and centres it in the space left of the icon.
     *
     * <p>Vanilla's EditBox only ever draws from its left edge, so centring means moving the widget
     * itself. It is sized to hug the digits, which would leave almost nothing to click on — hence
     * {@link #altitudeUnbound$clickField} handing clicks anywhere in the frame to the box.
     */
    @Unique
    private void altitudeUnbound$layout() {
        altitudeUnbound$layout(this.altitudeUnbound$maxBox, this.altitudeUnbound$maxFrameY,
                altitudeUnbound$worldMax());
        altitudeUnbound$layout(this.altitudeUnbound$minBox, this.altitudeUnbound$minFrameY,
                altitudeUnbound$worldMin());
    }

    @Unique
    private void altitudeUnbound$layout(final EditBox box, final int frameTop, final int worldDefault) {
        // An empty box shows its hint, so that is what has to be centred.
        final String shown = box.getValue().isEmpty() ? String.valueOf(worldDefault) : box.getValue();
        final int textWidth = this.font.width(shown);

        box.setX(altitudeUnbound$frameLeft() + ALTITUDE_UNBOUND$TEXT_CENTRE - textWidth / 2);
        box.setY(frameTop + ALTITUDE_UNBOUND$TEXT_Y);
        // Two spare pixels so the caret at the end of the line has somewhere to sit.
        box.setWidth(textWidth + 2);

        // EditBox scrolls its text to keep the caret visible, and it does that from insertText before
        // it ever tells us the value changed — so it has just measured against the old, narrower box
        // and parked the first characters off the left edge. Dragging the caret to the start resets
        // that against the width we have only now set; then put the caret back where it was.
        final int cursor = box.getCursorPosition();
        box.setCursorPosition(0);
        box.setCursorPosition(cursor);
        box.setHighlightPos(cursor);
    }

    /**
     * Lets the whole frame act as the box, since the box itself is only as wide as its digits.
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void altitudeUnbound$clickField(final double mouseX, final double mouseY, final int button,
                                            final CallbackInfoReturnable<Boolean> cir) {
        final EditBox box = altitudeUnbound$frameUnder(mouseX, mouseY);
        if (box == null) {
            // Nothing else on this screen takes focus, so without this a box would keep the caret —
            // and the keyboard — for as long as the screen stayed open.
            if (getFocused() == this.altitudeUnbound$maxBox || getFocused() == this.altitudeUnbound$minBox) {
                setFocused(null);
            }
            return;
        }

        if (box.isMouseOver(mouseX, mouseY)) {
            // On the digits themselves: let the box have the click, so the caret lands between the
            // two characters that were clicked between rather than always at the end.
            return;
        }

        // Elsewhere in the frame — the icon, the padding — there is no character to land on, so take
        // the near end of the line.
        setFocused(box);
        box.setFocused(true);
        final int caret = mouseX < box.getX() ? 0 : box.getValue().length();
        box.setCursorPosition(caret);
        box.setHighlightPos(caret);
        cir.setReturnValue(true);
    }

    @Unique
    private EditBox altitudeUnbound$frameUnder(final double mouseX, final double mouseY) {
        final int left = altitudeUnbound$frameLeft();
        if (mouseX < left || mouseX >= left + ALTITUDE_UNBOUND$FRAME_WIDTH) {
            return null;
        }
        if (mouseY >= this.altitudeUnbound$maxFrameY
                && mouseY < this.altitudeUnbound$maxFrameY + ALTITUDE_UNBOUND$FRAME_HEIGHT) {
            return this.altitudeUnbound$maxBox;
        }
        if (mouseY >= this.altitudeUnbound$minFrameY
                && mouseY < this.altitudeUnbound$minFrameY + ALTITUDE_UNBOUND$FRAME_HEIGHT) {
            return this.altitudeUnbound$minBox;
        }
        return null;
    }

    /**
     * A bar with an arrow running into it: the ceiling the scale tops out at, or the floor it
     * bottoms out at.
     */
    @Unique
    private void altitudeUnbound$renderLimitIcon(final GuiGraphics graphics, final int x, final int y,
                                                 final boolean up) {
        final int colour = ALTITUDE_UNBOUND$BEVEL;
        if (up) {
            graphics.fill(x, y, x + 7, y + 1, colour);
            graphics.fill(x + 3, y + 2, x + 4, y + 3, colour);
            graphics.fill(x + 2, y + 3, x + 5, y + 4, colour);
            graphics.fill(x + 1, y + 4, x + 6, y + 5, colour);
            graphics.fill(x + 3, y + 5, x + 4, y + 8, colour);
        } else {
            graphics.fill(x + 3, y, x + 4, y + 3, colour);
            graphics.fill(x + 1, y + 3, x + 6, y + 4, colour);
            graphics.fill(x + 2, y + 4, x + 5, y + 5, colour);
            graphics.fill(x + 3, y + 5, x + 4, y + 6, colour);
            graphics.fill(x, y + 7, x + 7, y + 8, colour);
        }
    }

    /**
     * Shrinks a handle's altitude to fit the handle instead of spilling across the screen.
     *
     * <p>Simulated centres the number on a 26-wide handle, which was ample while the scale could not
     * reach past the build height. A calibrated sensor reads in the tens of thousands, so the label
     * is scaled down once it outgrows the handle it belongs to.
     *
     * <p>Only the two altitude labels take this route: the window title goes through the Component
     * overload of drawCenteredString, which is a different method.
     */
    @Redirect(method = "renderWindow", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString"
                    + "(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private void altitudeUnbound$fitHandleLabel(final GuiGraphics graphics, final Font font, final String text,
                                                final int centreX, final int y, final int colour) {
        final int textWidth = font.width(text);
        if (textWidth <= ALTITUDE_UNBOUND$HANDLE_TEXT_WIDTH) {
            graphics.drawCenteredString(font, text, centreX, y, colour);
            return;
        }

        final float scale = (float) ALTITUDE_UNBOUND$HANDLE_TEXT_WIDTH / textWidth;
        final PoseStack pose = graphics.pose();
        pose.pushPose();
        // About the middle of the label, so it shrinks into the handle rather than off one side.
        pose.translate(centreX, y + 4.0F, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -textWidth / 2, -4, colour, true);
        pose.popPose();
    }

    /**
     * Pins down where the handles currently are in blocks. Called whenever the player moves one, so
     * that the next change of scale has somewhere honest to put it back.
     */
    @Unique
    private void altitudeUnbound$rememberAltitudes() {
        this.altitudeUnbound$lowAltitude = this.blockEntity.toWorldHeight(this.lowSignal);
        this.altitudeUnbound$highAltitude = this.blockEntity.toWorldHeight(this.highSignal);
    }

    @Inject(method = "updateValues", at = @At("TAIL"))
    private void altitudeUnbound$rememberDraggedAltitudes(final double mouseX, final double mouseY,
                                                          final CallbackInfo ci) {
        altitudeUnbound$rememberAltitudes();
    }

    @Unique
    private EditBox altitudeUnbound$box(final String key, final int worldDefault, final Integer value) {
        // Position and width are set by layout(), which re-centres the digits on every keystroke.
        final EditBox box = new EditBox(this.font, 0, 0, 1, ALTITUDE_UNBOUND$FIELD_HEIGHT,
                Component.translatable(AltitudeUnbound.MODID + ".gui.altitude_sensor." + key));
        // Six characters is what fits between the icon and the right edge of the frame. It caps the
        // typeable range at +/-99999, which is 300x the vanilla build height; anything wider can still
        // arrive from a Clipboard or an older save, the scale just cannot be typed in here.
        box.setMaxLength(6);
        box.setFilter(text -> text.isEmpty() || text.equals("-")
                || ALTITUDE_UNBOUND$ALTITUDE.matcher(text).matches());
        // The frame is drawn with the rest of the window, in Create's palette rather than vanilla's.
        box.setBordered(false);
        // No setHint: vanilla hides its hint the moment the box is focused, and the world's limit is
        // worth seeing exactly while you are deciding whether to override it. Drawn in renderField.
        box.setValue(value == null ? "" : String.valueOf(value));
        box.setTooltip(Tooltip.create(
                Component.translatable(AltitudeUnbound.MODID + ".gui.altitude_sensor." + key + ".tip")));
        return box;
    }

    /**
     * Pushes what is in the boxes onto the client's own copy of the sensor, which is what the
     * altitudes beside the handles are read back through. The server hears about it on close.
     */
    @Unique
    private void altitudeUnbound$rangeEdited() {
        final Level level = this.blockEntity.getLevel();
        if (level == null) {
            return;
        }

        altitudeUnbound$layout();

        final Float typedMin = altitudeUnbound$parse(this.altitudeUnbound$minBox);
        final Float typedMax = altitudeUnbound$parse(this.altitudeUnbound$maxBox);

        if (typedMin == null && typedMax == null) {
            altitudeUnbound$markValid(true);
            altitudeUnbound$rescale(AltitudeRange.AUTO);
            return;
        }

        final float min = typedMin == null ? level.getMinBuildHeight() : typedMin;
        final float max = typedMax == null ? level.getMaxBuildHeight() : typedMax;

        if (!AltitudeRange.isValid(min, max)) {
            // Keep the last good scale on the gauge rather than letting it go haywire mid-keystroke.
            altitudeUnbound$markValid(false);
            return;
        }

        altitudeUnbound$markValid(true);
        altitudeUnbound$rescale(new AltitudeRange(true, min, max));
    }

    /**
     * Moves the gauge onto {@code range}, leaving each handle at the altitude it was already set to.
     *
     * <p>The handles are stored as a fraction of the scale, so without this they would drift with it:
     * a handle at 280 on a scale topping out at 300 would slide down to 233 if the top moved to 250.
     * Keeping the altitude means stretching the scale upwards leaves a handle exactly where it was,
     * and shrinking it past a handle pushes that handle down to the new end — and no further, so the
     * altitude is still remembered if the scale grows back.
     */
    @Unique
    private void altitudeUnbound$rescale(final AltitudeRange range) {
        ((CalibratableSensor) this.blockEntity).altitudeUnbound$setRange(range);

        this.lowSignal = Mth.clamp(this.blockEntity.toNormalHeight(this.altitudeUnbound$lowAltitude), 0.0F, 1.0F);
        this.highSignal = Mth.clamp(this.blockEntity.toNormalHeight(this.altitudeUnbound$highAltitude), 0.0F, 1.0F);

        this.visualLowSignal.chase(this.lowSignal, 0.85F, Chaser.EXP);
        this.visualHighSignal.chase(this.highSignal, 0.85F, Chaser.EXP);
    }

    @Unique
    private void altitudeUnbound$markValid(final boolean valid) {
        final int colour = valid ? ALTITUDE_UNBOUND$TEXT : ALTITUDE_UNBOUND$BAD_TEXT;
        this.altitudeUnbound$minBox.setTextColor(colour);
        this.altitudeUnbound$maxBox.setTextColor(colour);
    }

    @Unique
    private Float altitudeUnbound$parse(final EditBox box) {
        final String text = box.getValue();
        if (text.isEmpty() || text.equals("-")) {
            return null;
        }
        try {
            return Float.parseFloat(text);
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    @Unique
    private int altitudeUnbound$worldMin() {
        final Level level = this.blockEntity.getLevel();
        return level == null ? 0 : level.getMinBuildHeight();
    }

    @Unique
    private int altitudeUnbound$worldMax() {
        final Level level = this.blockEntity.getLevel();
        return level == null ? 0 : level.getMaxBuildHeight();
    }

    /**
     * Simulated sends the handle positions when its screen closes; the scale they are measured
     * against goes with them.
     */
    @Inject(method = "onClose", at = @At("HEAD"))
    private void altitudeUnbound$sendRange(final CallbackInfo ci) {
        PacketDistributor.sendToServer(new ConfigureAltitudeRangePayload(this.blockEntity.getBlockPos(),
                ((CalibratableSensor) this.blockEntity).altitudeUnbound$getRange()));
    }

    /**
     * Nudges whichever handle the cursor is over. Dragging one is awkward once a notch is worth tens
     * of blocks, which is exactly what a calibrated range makes it worth.
     */
    @Override
    public boolean altitudeUnbound$scrollSlider(final double mouseX, final double mouseY, final double scrollDelta) {
        if (scrollDelta == 0.0) {
            return false;
        }

        final Boolean target = altitudeUnbound$sliderUnder(mouseX, mouseY);
        if (target == null) {
            return false;
        }
        final boolean overLow = target;

        final float step = hasShiftDown() ? ALTITUDE_UNBOUND$FINE_STEP : ALTITUDE_UNBOUND$STEP;
        final float change = (float) scrollDelta * step;

        if (hasControlDown()) {
            // Same as holding control while dragging: slide the whole window without resizing it.
            if (outOfBounds(this.lowSignal + change) || outOfBounds(this.highSignal + change)) {
                return true;
            }
            this.lowSignal += change;
            this.highSignal += change;
        } else if (overLow) {
            this.lowSignal = Mth.clamp(this.lowSignal + change, 0.0F, 1.0F);
        } else {
            this.highSignal = Mth.clamp(this.highSignal + change, 0.0F, 1.0F);
        }

        this.visualLowSignal.chase(this.lowSignal, 0.85F, Chaser.EXP);
        this.visualHighSignal.chase(this.highSignal, 0.85F, Chaser.EXP);
        altitudeUnbound$rememberAltitudes();

        final Player player = Minecraft.getInstance().player;
        if (player != null) {
            final float pitch = overLow ? this.lowSignal : this.highSignal;
            player.playSound(SoundEvents.LEVER_CLICK, 0.2F, 0.25F + pitch * 0.5F);
        }
        return true;
    }

    /**
     * Which handle the cursor is addressing.
     *
     * <p>Simulated's own hover tests cover a 13-wide column on either side of the window, which
     * leaves the lit bar down the middle belonging to neither — and that bar is the obvious thing to
     * point at. Anywhere in the window that the two columns do not claim goes to whichever handle is
     * nearer vertically.
     *
     * @return {@code TRUE} for the low handle, {@code FALSE} for the high one, {@code null} if the
     *         cursor is not over the bar at all
     */
    @Unique
    private Boolean altitudeUnbound$sliderUnder(final double mouseX, final double mouseY) {
        if (overGrabby(mouseX, mouseY, true) || overBar(mouseX, mouseY, true)) {
            return Boolean.TRUE;
        }
        if (overGrabby(mouseX, mouseY, false) || overBar(mouseX, mouseY, false)) {
            return Boolean.FALSE;
        }

        // barLeft is guiLeft + 3, and the background is 42 wide.
        final int windowLeft = this.barLeft - 3;
        if (mouseX < windowLeft || mouseX > windowLeft + 42
                || mouseY < this.barTop || mouseY > this.barTop + 200) {
            return null;
        }

        final double lowY = this.barTop + (1.0F - this.lowSignal) * 200.0;
        final double highY = this.barTop + (1.0F - this.highSignal) * 200.0;
        return Math.abs(mouseY - lowY) <= Math.abs(mouseY - highY);
    }
}
