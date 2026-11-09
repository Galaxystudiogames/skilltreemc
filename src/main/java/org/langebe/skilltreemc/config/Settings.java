package org.langebe.skilltreemc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.langebe.skilltreemc.SkillTreeMC;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registry of all {@link Setting}s plus loading/saving of {@code config/skilltreemc.json}.
 *
 * <p>The file is a flat JSON object: {@code "key": value} or {@code "key": [v1, v2, ...]}
 * for per-tier lists. Missing keys use their default; the file is rewritten on startup,
 * so new settings appear automatically. Descriptions are shown by
 * {@code /st admin config get <key>}.
 */
public final class Settings {

    private static final Map<String, Setting> SETTINGS = new LinkedHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** {key}, {key@2} (tier 2) and {key%} (as percent) placeholders in tooltip texts. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z0-9_.]+)(?:@(\\d+))?(%?)}");

    private Settings() {
    }

    // ---------------------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------------------

    public static Setting number(String key, double defaultValue, String description) {
        return register(new Setting(key, description, false, defaultValue));
    }

    /** A per-tier list: first value = tier 1 (base), then one value per upgrade. */
    public static Setting tiers(String key, String description, double... defaultsPerTier) {
        return register(new Setting(key, description, true, defaultsPerTier));
    }

    private static Setting register(Setting setting) {
        if (SETTINGS.containsKey(setting.key())) {
            throw new IllegalArgumentException("Duplicate setting '" + setting.key() + "'");
        }
        SETTINGS.put(setting.key(), setting);
        return setting;
    }

    public static Setting get(String key) {
        return SETTINGS.get(key);
    }

    public static Collection<Setting> all() {
        return Collections.unmodifiableCollection(SETTINGS.values());
    }

    // ---------------------------------------------------------------------------------
    // Changing values (commands)
    // ---------------------------------------------------------------------------------

    /**
     * Sets a value from text, e.g. {@code "2.5"} or {@code "12,9,9,7,7"}, and saves the file.
     *
     * @throws IllegalArgumentException with a readable message if the input is invalid
     */
    public static void set(String key, String text) {
        Setting setting = require(key);
        String[] parts = text.replace("[", "").replace("]", "").split("[,\\s]+");
        double[] values = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                values[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + parts[i] + "' is not a number");
            }
        }
        setting.set(values);
        save();
    }

    public static void reset(String key) {
        require(key).reset();
        save();
    }

    public static void resetAll() {
        SETTINGS.values().forEach(Setting::reset);
        save();
    }

    private static Setting require(String key) {
        Setting setting = SETTINGS.get(key);
        if (setting == null) {
            throw new IllegalArgumentException("Unknown setting '" + key + "'");
        }
        return setting;
    }

    // ---------------------------------------------------------------------------------
    // File
    // ---------------------------------------------------------------------------------

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(SkillTreeMC.MOD_ID + ".json");
    }

    /** Loads the file (if it exists) and writes it back with all current keys. */
    public static void load() {
        Path file = file();
        if (Files.exists(file)) {
            try {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    Setting setting = SETTINGS.get(entry.getKey());
                    if (setting == null) {
                        SkillTreeMC.LOGGER.warn("Unknown setting '{}' in {} – ignored", entry.getKey(), file);
                        continue;
                    }
                    try {
                        setting.set(read(entry.getValue()));
                    } catch (RuntimeException e) {
                        SkillTreeMC.LOGGER.warn("Invalid value for '{}' in {} – using default", entry.getKey(), file);
                    }
                }
            } catch (IOException | RuntimeException e) {
                SkillTreeMC.LOGGER.error("Could not read {} – using defaults", file, e);
                return; // don't overwrite a broken file the admin may want to fix
            }
        }
        save();
    }

    public static void save() {
        JsonObject json = new JsonObject();
        for (Setting setting : SETTINGS.values()) {
            double[] values = setting.values();
            if (setting.isList()) {
                JsonArray array = new JsonArray();
                for (double value : values) {
                    array.add(number(value));
                }
                json.add(setting.key(), array);
            } else {
                json.addProperty(setting.key(), number(values[0]));
            }
        }
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(json));
        } catch (IOException e) {
            SkillTreeMC.LOGGER.error("Could not write {}", file(), e);
        }
    }

    private static double[] read(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            double[] values = new double[array.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = array.get(i).getAsDouble();
            }
            return values;
        }
        return new double[]{element.getAsDouble()};
    }

    /** Whole numbers are written without ".0". */
    private static Number number(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15 ? (Number) (long) value : (Number) value;
    }

    // ---------------------------------------------------------------------------------
    // Text helpers
    // ---------------------------------------------------------------------------------

    /**
     * Replaces placeholders in tooltip texts with current values:
     * {@code {key}}, {@code {key@2}} (value of tier 2) and {@code {key%}} (×100, for percentages).
     */
    public static String format(String template) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Setting setting = SETTINGS.get(matcher.group(1));
            String replacement;
            if (setting == null) {
                replacement = matcher.group();
            } else {
                double value = matcher.group(2) != null ? setting.tier(Integer.parseInt(matcher.group(2))) : setting.get();
                replacement = formatNumber(matcher.group(3).isEmpty() ? value : value * 100);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(Math.round(value * 1000) / 1000.0);
    }
}
