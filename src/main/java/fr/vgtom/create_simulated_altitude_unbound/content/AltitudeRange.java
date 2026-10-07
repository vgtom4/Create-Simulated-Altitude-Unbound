package fr.vgtom.create_simulated_altitude_unbound.content;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;

/**
 * The altitude window one sensor normalises against, in world blocks.
 *
 * <p>{@link #AUTO} means "whatever the level's build limits are", which is what Simulated does on
 * its own. Anything else is a range the player typed in.
 */
public record AltitudeRange(boolean custom, float min, float max) {
    public static final String CUSTOM_KEY = "AltitudeUnboundCustom";
    public static final String MIN_KEY = "AltitudeUnboundMin";
    public static final String MAX_KEY = "AltitudeUnboundMax";

    /** Sensors above the default build limit are the point of the mod, but a range has to end somewhere. */
    public static final float LIMIT = 1_000_000.0F;

    /** Narrower than this and a single redstone step would be a fraction of a block. */
    public static final float MIN_SPAN = 2.0F;

    public static final AltitudeRange AUTO = new AltitudeRange(false, 0.0F, 0.0F);

    public static boolean isValid(final float min, final float max) {
        return Float.isFinite(min)
                && Float.isFinite(max)
                && min >= -LIMIT
                && max <= LIMIT
                && max - min >= MIN_SPAN;
    }

    public boolean isUsable() {
        return this.custom && isValid(this.min, this.max);
    }

    public float span() {
        return this.max - this.min;
    }

    /** @return the range actually in force for a sensor in {@code level}, custom or not */
    public AltitudeRange resolve(final LevelHeightAccessor level) {
        if (this.isUsable()) {
            return this;
        }
        return new AltitudeRange(false, level.getMinBuildHeight(), level.getMaxBuildHeight());
    }

    /** @return true if this range reaches outside what the world can actually build, which is allowed but worth saying */
    public boolean exceedsWorld(final LevelHeightAccessor level) {
        return this.isUsable() && (this.min < level.getMinBuildHeight() || this.max > level.getMaxBuildHeight());
    }

    public void write(final CompoundTag tag) {
        if (!this.custom) {
            // Keep the tag clean for sensors that were never calibrated, so their NBT stays byte-for-byte
            // what Simulated alone would have written.
            tag.remove(CUSTOM_KEY);
            tag.remove(MIN_KEY);
            tag.remove(MAX_KEY);
            return;
        }
        tag.putBoolean(CUSTOM_KEY, true);
        tag.putFloat(MIN_KEY, this.min);
        tag.putFloat(MAX_KEY, this.max);
    }

    /** Reads a range back, falling through to {@link #AUTO} for sensors saved before this mod existed. */
    public static AltitudeRange read(final CompoundTag tag) {
        if (!tag.getBoolean(CUSTOM_KEY)) {
            return AUTO;
        }
        final float min = tag.getFloat(MIN_KEY);
        final float max = tag.getFloat(MAX_KEY);
        return isValid(min, max) ? new AltitudeRange(true, min, max) : AUTO;
    }

    public static AltitudeRange worldDefault(final Level level) {
        return new AltitudeRange(false, level.getMinBuildHeight(), level.getMaxBuildHeight());
    }
}
