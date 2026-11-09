package org.langebe.skilltreemc.config;

import java.util.Arrays;

/**
 * One configurable number (or list of numbers, one per upgrade tier).
 *
 * <p>Settings are created through {@link Settings#number} / {@link Settings#tiers}, saved in
 * {@code config/skilltreemc.json} and can be changed in-game with {@code /st admin config}.
 * Game code always reads the <b>current</b> value ({@link #get()}, {@link #tier(int)}), so
 * changes take effect immediately.
 */
public final class Setting {

    private final String key;
    private final String description;
    private final double[] defaults;
    private final boolean list;
    private double[] values;

    Setting(String key, String description, boolean list, double... defaults) {
        this.key = key;
        this.description = description;
        this.list = list;
        this.defaults = defaults.clone();
        this.values = defaults.clone();
    }

    public String key() {
        return key;
    }

    public String description() {
        return description;
    }

    /** {@code true} if this setting has one value per upgrade tier. */
    public boolean isList() {
        return list;
    }

    /** The value (for lists: the first entry). */
    public double get() {
        return values[0];
    }

    public int getInt() {
        return (int) Math.round(values[0]);
    }

    /** {@code true} if the value is not 0 (for on/off settings). */
    public boolean isOn() {
        return values[0] != 0;
    }

    /**
     * Value for an upgrade tier (1 = base). Tiers above the list length use the last entry.
     */
    public double tier(int tier) {
        return values[Math.max(0, Math.min(tier, values.length) - 1)];
    }

    public int tierInt(int tier) {
        return (int) Math.round(tier(tier));
    }

    double[] values() {
        return values.clone();
    }

    double[] defaults() {
        return defaults.clone();
    }

    /** Upper bound for any value – far above every sensible setting, but safe from overflows. */
    static final double MAX_VALUE = 1_000_000;

    void set(double[] newValues) {
        if (newValues.length == 0 || (!list && newValues.length != 1)) {
            throw new IllegalArgumentException(list ? "Expected at least one value" : "Expected exactly one value");
        }
        for (double value : newValues) {
            // All settings are amounts, chances, durations or costs: negative values would e.g.
            // turn a mana cost into a mana gain, NaN/Infinity break the cooldown maths.
            if (!Double.isFinite(value) || value < 0 || value > MAX_VALUE) {
                throw new IllegalArgumentException("Values must be between 0 and " + (long) MAX_VALUE
                        + " (got " + value + ")");
            }
        }
        this.values = newValues.clone();
    }

    void reset() {
        this.values = defaults.clone();
    }

    /** Human-readable value, e.g. {@code 2.5} or {@code [12, 9, 9, 7, 7]}. */
    public String format() {
        return format(values);
    }

    public String formatDefault() {
        return format(defaults);
    }

    private String format(double[] array) {
        if (!list) {
            return Settings.formatNumber(array[0]);
        }
        return Arrays.stream(array).mapToObj(Settings::formatNumber).toList().toString();
    }
}
