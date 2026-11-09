package org.langebe.skilltreemc.data;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import org.langebe.skilltreemc.SkillTreeMC;

import java.util.function.UnaryOperator;

/**
 * Low-level storage of a player's data.
 *
 * <p>Two Fabric data attachments live directly on the player entity:
 * <ul>
 *   <li>{@code skilltreemc:player_skills} – {@link PlayerSkills} (progress)</li>
 *   <li>{@code skilltreemc:player_state} – {@link PlayerState} (mana, cooldowns, spell slots)</li>
 * </ul>
 * Both are saved in the normal player file and survive death ({@code copyOnDeath}).
 *
 * <p>This class only reads and writes data – it does <b>not</b> run any game logic.
 * Game code should normally use {@link org.langebe.skilltreemc.core.SkillManager}.
 */
public final class SkillData {

    private static final AttachmentType<PlayerSkills> PLAYER_SKILLS = AttachmentRegistry.create(
            SkillTreeMC.id("player_skills"),
            builder -> builder
                    .persistent(PlayerSkills.CODEC)
                    .initializer(() -> PlayerSkills.EMPTY)
                    .copyOnDeath()
    );

    private static final AttachmentType<PlayerState> PLAYER_STATE = AttachmentRegistry.create(
            SkillTreeMC.id("player_state"),
            builder -> builder
                    .persistent(PlayerState.CODEC)
                    .initializer(() -> PlayerState.EMPTY)
                    .copyOnDeath()
    );

    private SkillData() {
    }

    /**
     * Forces class loading so the attachment types are registered during mod init.
     * Must be called from {@link SkillTreeMC#onInitialize()}.
     */
    public static void init() {
    }

    // ----- Progress ------------------------------------------------------------------

    public static PlayerSkills get(ServerPlayer player) {
        return player.getAttachedOrCreate(PLAYER_SKILLS);
    }

    public static void unlockSkill(ServerPlayer player, String skillId) {
        modify(player, skills -> skills.withUnlockedSkill(skillId));
    }

    public static void activateSkill(ServerPlayer player, String skillId) {
        modify(player, skills -> skills.withActiveSkill(skillId));
    }

    public static void deactivateSkill(ServerPlayer player, String skillId) {
        modify(player, skills -> skills.withoutActiveSkill(skillId));
    }

    public static void activatePath(ServerPlayer player, SkillPath path) {
        modify(player, skills -> skills.withActivePath(path.id()));
    }

    // ----- State ---------------------------------------------------------------------

    public static PlayerState getState(ServerPlayer player) {
        return player.getAttachedOrCreate(PLAYER_STATE);
    }

    public static void modifyState(ServerPlayer player, UnaryOperator<PlayerState> change) {
        player.modifyAttached(PLAYER_STATE, state -> change.apply(state != null ? state : PlayerState.EMPTY));
    }

    // ----- Reset ---------------------------------------------------------------------

    /** Deletes all skill progress and state of the player. */
    public static void reset(ServerPlayer player) {
        player.setAttached(PLAYER_SKILLS, PlayerSkills.EMPTY);
        player.setAttached(PLAYER_STATE, PlayerState.EMPTY);
    }

    private static void modify(ServerPlayer player, UnaryOperator<PlayerSkills> change) {
        // modifyAttached passes null if nothing is attached yet.
        player.modifyAttached(PLAYER_SKILLS, skills -> change.apply(skills != null ? skills : PlayerSkills.EMPTY));
    }
}
