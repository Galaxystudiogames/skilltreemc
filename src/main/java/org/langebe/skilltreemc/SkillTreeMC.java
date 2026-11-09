package org.langebe.skilltreemc;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.langebe.skilltreemc.config.Settings;
import org.langebe.skilltreemc.core.SkillManager;
import org.langebe.skilltreemc.core.SkillRegistry;
import org.langebe.skilltreemc.data.SkillData;
import org.langebe.skilltreemc.input.InputHandler;
import org.langebe.skilltreemc.mechanics.ActiveAbilities;
import org.langebe.skilltreemc.mechanics.CombatMechanics;
import org.langebe.skilltreemc.mechanics.CooldownHud;
import org.langebe.skilltreemc.mechanics.Cosmetics;
import org.langebe.skilltreemc.mechanics.Feedback;
import org.langebe.skilltreemc.mechanics.Hex;
import org.langebe.skilltreemc.mechanics.Scheduler;
import org.langebe.skilltreemc.mechanics.Stun;
import org.langebe.skilltreemc.mechanics.UtilityActives;
import org.langebe.skilltreemc.mechanics.UtilityMechanics;
import org.langebe.skilltreemc.mechanics.UtilityPassives;
import org.langebe.skilltreemc.skills.ModSkills;
import org.langebe.skilltreemc.spell.Mana;
import org.langebe.skilltreemc.spell.ModSpells;
import org.langebe.skilltreemc.spell.SpellRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mod entry point (registered as "main" entrypoint in fabric.mod.json). */
public class SkillTreeMC implements ModInitializer {

    public static final String MOD_ID = "skilltreemc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing SkillTreeMC");

        // Data + content. Spells first: skills reference them.
        SkillData.init();
        SkillTreeConfig.init();
        ModSpells.register();
        ModSkills.register();
        // All settings exist now -> read config/skilltreemc.json (and write missing keys).
        Settings.load();

        // Game logic
        SkillManager.registerEvents();
        Scheduler.register();
        Feedback.register();
        CooldownHud.register();
        Cosmetics.register();
        CombatMechanics.register();
        ActiveAbilities.register();
        Stun.register();
        Hex.register();
        UtilityMechanics.register();
        UtilityActives.register();
        UtilityPassives.register();
        Mana.register();
        InputHandler.register();
        ModCommands.register();

        LOGGER.info("SkillTreeMC initialized with {} skills, {} spells and {} settings",
                SkillRegistry.all().size(), SpellRegistry.all().size(), Settings.all().size());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> startupBanner());
    }

    /** Mod version from fabric.mod.json (filled in from gradle.properties at build time). */
    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    /** Banner in the server console once the server is ready. */
    private static void startupBanner() {
        String[] lines = {
                "",
                "  ╔═══════════════════════════════════════════════════╗",
                "  ║   ⚔  ✦  ➹   S K I L L   T R E E   M C   ➹  ✦  ⚔   ║",
                "  ╚═══════════════════════════════════════════════════╝",
                String.format("   Version %s  ·  by LANGEBE", version()),
                String.format("   %d skills  ·  %d spells  ·  %d settings",
                        SkillRegistry.all().size(), SpellRegistry.all().size(), Settings.all().size()),
                "   Paths: Combat · Utils · Support/Spells",
                "   Config: " + Settings.file(),
                "   Admin:  /st admin config list   ·   Players: /st help",
                "",
        };
        for (String line : lines) {
            LOGGER.info(line);
        }
    }

    /** Builds an identifier in this mod's namespace, e.g. {@code skilltreemc:player_skills}. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
