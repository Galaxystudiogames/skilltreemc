# Skill Tree MC

A server-side Fabric mod for SMP servers. Players level up in three **paths** (Combat, Utils, Support/Spells), one level at a time, and pay with their **XP levels**. Combat and Support split into two branches at level 5.

Built from the design document *"Skill System — SMP Mod"* (HTML version, including the Utils choice levels). Values the document leaves open were chosen to fit the surrounding numbers (see [Chosen values](#chosen-values)). **Every balance value is configurable**: in `config/skilltreemc.json` or in-game with `/st admin config` (see [Configuration](#configuration)).

## Contents

- [Installation](#installation)
- [Controls](#controls)
- [Commands](#commands)
- [The paths](#the-paths)
  - [Combat](#combat)
  - [Support / Spells](#support--spells)
  - [Utils](#utils)
- [Animations](#animations)
- [HUD: action bar & cooldown scoreboard](#hud-action-bar--cooldown-scoreboard)
- [Cosmetics](#cosmetics)
- [Configuration](#configuration)
- [Chosen values](#chosen-values)
- [Differences from the design document](#differences-from-the-design-document)
- [Changing & extending](#changing--extending)
- [For developers](#for-developers)

## Installation

| Component     | Version      |
|---------------|--------------|
| Minecraft     | 26.3         |
| Fabric Loader | ≥ 0.19.5     |
| Fabric API    | 0.161.0+26.3 |
| Java          | 25           |

1. Set up a Fabric server for 26.3 and put [Fabric API](https://modrinth.com/mod/fabric-api) into `mods/`.
2. Put `skilltreemc-<version>.jar` (from `build/libs/`) into `mods/` and start the server.

Players do **not** need the mod on their client. Menus are normal chest inventories, and the Spell Orb is a marked Heart of the Sea.

> `fabric.mod.json` contains `"environment": "server"`: the mod only loads on dedicated servers, not in singleplayer.

## Controls

Without a client mod there are no custom key bindings, so existing actions are reused:

| Input                          | Spell Orb in hand            | Utility Compass in hand       | otherwise                    |
|--------------------------------|------------------------------|-------------------------------|------------------------------|
| Right-click                    | cast the selected fast spell | use the selected Utils perk   | –                            |
| Sneak + right-click            | select the next fast slot    | select the next Utils perk    | –                            |
| Sneak + offhand key (`F`)      | open the spell menu          | toggle **Rage** / **Shield**  | toggle **Rage** / **Shield** |

The **Utility Compass** is a marked compass. You get it with your first active Utils perk (Grapple, Eagle Eye, Smoke Screen, Phase Dash, Tracker). With Eagle Eye and Tracker its needle points at the tracked player.

Without sneaking, `F` swaps hands as usual. While sneaking, hands are only kept in place if the mod uses the key.

**Spell menu:** click a spell, then click a fast slot to put it there. Left-click on a slot selects it, right-click clears it.

## Commands

| Command                               | Description                                        | Permission |
|---------------------------------------|----------------------------------------------------|------------|
| `/st open`                            | open the skill tree                                | everyone   |
| `/st spells`                          | open the spell menu                                | everyone   |
| `/st orb`                             | replace a lost Spell Orb (Support path required)   | everyone   |
| `/st compass`                         | replace a lost Utility Compass (active perk req.)  | everyone   |
| `/st cooldowns`                       | show/hide your own cooldown scoreboard             | everyone   |
| `/st aura`                            | show/hide your own mastery aura                    | everyone   |
| `/st info [player]`                   | show unlocked skills                               | everyone / OP |
| `/st help`                            | help                                               | everyone   |
| `/st admin list`                      | all skills with ID and cost                        | OP         |
| `/st admin unlock <player> <skill>`   | unlock a skill for free (no checks)                | OP         |
| `/st admin reset <player>`            | delete progress, mana and cooldowns                | OP         |
| `/st admin config …`                  | view and change balance values, see [Configuration](#configuration) | OP |

## The paths

Every level is a node in the path menu. A level can only be bought once the previous level is unlocked. Costs grow exponentially (`SkillTreeConfig.levelCost`). Unlocked levels can be toggled on and off by clicking them.

At the **split** (level 5) you choose one of two branches. The other branch is locked afterwards (barrier in the menu).

**Path menu as a tree:** each path is shown top to bottom as a tree, with **one row per level**:

```
[L1]  ·  ·  ·  ◆  ·  ·  ·  ·      one node → centre
[L4]  ·  ·  ·  ◆  ·  ·  ·  ·
[L5]  ·  ·  ◆  ·  ·  ·  ◆  ·      split → left (DPS / Healer) and right (Tank / Damage)
[L8]  ·  ·  ◆  ·  ◆  ·  ◆  ·      three options (Utils choices) → left, centre, right
[▲]  ·  Back  ·  Level  ·  ·  ·  [▼]
```

- On the left of every row a glass pane shows the level (the stack size is the level number). It turns lime as soon as something on that level is unlocked.
- 5 levels are visible at once. **▲ bottom left** and **▼ bottom right** scroll: left-click by 1 level, right-click by a whole page.
- When opened, the menu scrolls to the next level you can still unlock.

### Combat

PvP-focused. Crit chance, crits, stun and the Rage extension only work on **melee hits** – with any weapon or the fist, including the mace smash attack and the spear, but not arrows, thrown tridents or spells. A killing blow counts as a hit too (crit effect, Rage extension).

| Level | Content |
|-------|---------|
| L1 | Crit Rate 3 %, Crit Multiplier 5 %, Reduction Rate 3 %, Reduction Percent 5 % |
| L2 | +1 heart |
| L3 | Crit 5 % / 7 %, Reduction 5 % / 7 % |
| L4 | **Combat Sense**: players you hit glow for 1.5 s |
| L5 | Split: **DPS** or **Tank** |

| DPS | | Tank | |
|---|---|---|---|
| L6–7, L9–14 | Crit Rate 7 → 25 %, Multiplier 10 → 30 %, Reduction Rate 6 → 10 %, Glowing 2 / 2.5 / 3 s (L7 / L10 / L13) | L6–7, L9–14 | Reduction Rate 7 → 25 %, Percent 10 → 30 %, Crit Rate 6 → 10 %, Glowing like DPS |
| L6, L9, L11, L14 | **+1 heart** each (+4 total) | L6–7, L9–15 | **+1 heart** each (+9 total) |
| L8 | **Stun**: 4 % per hit, 2.5 s unable to move and skills locked, 8 s internal cooldown | L8 | **Living Immortal**: below 2 hearts, 25 % chance for Resistance 10 + Regeneration 10 (2 s), 40 s cooldown; also saves you from a lethal hit |
| L15 | **Rage** (toggle): Speed II + 100 % crit rate, 10 s base duration, every hit extends it by 80 % of the time since the last hit, 2.5 min cooldown | L15 | **Shield** (toggle): every hit is reduced, Reduction +10 %, 8 s, 2 min cooldown |

### Support / Spells

Mana-based. At level 1 you get the **Spell Orb** and a blue **mana boss bar**. Mana regenerates every second.

| Level | Content |
|-------|---------|
| L1 | 100 mana, 0.5 mana/s, 2 fast slots; Area Heal, Heal Ray, Firebolt |
| L2 | 150 mana, 3rd fast slot |
| L3 | Damage Aura |
| L4 | 1 mana/s |
| L5 | Split: **Healer/Buffer** or **Damage/Debuffer** |
| L6 / L10 / L14 | 4th / 5th / 6th fast slot (both branches) |
| L7, L9, L11, L12, L15–17 | Max mana 175 / 200 / 250 / 300, regen 1.25 / 1.5 / 1.75 mana/s |

| Healer/Buffer | | Damage/Debuffer | |
|---|---|---|---|
| L8 | Advanced Area Heal, Advanced Heal Ray, **Buff Shower** | L8 | Advanced Firebolt, Advanced Damage Aura, **Dixiklo** |
| L13 | **Angels Circle**, **Buff Shower+** | L13 | **Demon Shockwave** |
| L18 | **Sanctum** (ultimate) | L18 | **Hex of Corruption** (ultimate) |

All spells (values in `spell/ModSpells.java`):

| Spell | Effect | Mana | CD |
|---|---|---|---|
| Area Heal | 3 hearts, 3 block radius (incl. caster) | 10 | 60 s |
| Heal Ray | 3 hearts for the player you look at, 5 blocks | 10 | 30 s |
| Firebolt | 3 damage + fire, target you look at (20 blocks) | 15 | 40 s |
| Damage Aura | 4 damage to every player except the caster, 3 blocks | 20 | 70 s |
| Advanced Area Heal | 4 hearts, 5 block radius | 15 | 60 s |
| Advanced Heal Ray | 4.5 hearts, 8 blocks | 15 | 30 s |
| Advanced Firebolt | 5 damage + 4 s fire | 25 | 40 s |
| Advanced Damage Aura | 6 damage, 4 block radius | 30 | 70 s |
| Buff Shower | zone (5 s, 10 blocks): Speed + Strength (90 s) + Fire Res (8 min) | 45 | 75 s |
| Buff Shower+ | as above, Speed + Strength 3 min | 70 | 80 s |
| Angels Circle | 4 hearts + Regeneration I 5 s, 8 blocks | 80 | 120 s |
| Sanctum | Absorption IV + Regeneration II 8 s, 12 blocks, particle links, conduit/beacon sound | 150 | 200 s |
| Dixiklo | stationary cloud (6.5 s) with Weakness, Slowness, Poison | 45 | 75 s |
| Demon Shockwave | 20 blocks, up to 58.5 magic damage + Slowness/Weakness/Blindness, scales with closeness; hits allies too | 90 | 180 s |
| Hex of Corruption | choose a target → lightning with 5 hearts of true damage (never lethal, at least ½ heart remains), healing = damage for 12 s, Weakness II + Slowness II | 130 | 220 s |

Advanced spells replace the base spell (in the fast slots as well) and share its cooldown.

Stunned players cannot cast spells. If there is no target, neither mana nor cooldown is used.

### Utils

All-rounder without a hard split. From L6 on you pick **one** of 2–3 options per choice level. The upgrade levels L11–L23 automatically improve the perks you picked.

| Level | Content |
|-------|---------|
| L1 | **Swift Feet**: Speed I out of combat (10 s after the last hit). **Soft Landing**: −20 % fall damage |
| L2 | **Night Owl**: permanent Night Vision, without flickering |
| L3 | **Scavenger**: every mob drop has a 15 % chance to drop twice |
| L4 | Swift Feet → Speed II, Soft Landing → −40 % |
| L5 | the choice system begins |

| Level | Theme | Options |
|-------|-------|---------|
| L6 | Mobility vs. Stealth | **Grapple** (active): launches you where you look, 12 s CD · **Ghost Walk** (passive): no footstep sounds, faster sneaking |
| L7 | Combat Support vs. Sustain | **Potion Mastery**: potions last 30 % longer · **Iron Gut**: hunger drains 40 % slower |
| L8 | Information vs. Offense vs. Resources | **Eagle Eye** (active): all players within 80 blocks, 10 s, 60 s CD · **Smoke Screen** (active): cloud, enemies blinded for 3 s, 45 s CD · **Midas Touch**: ores 10 % doubled |
| L9 | Aggressive Mobility vs. Defense | **Phase Dash** (active): 5 block teleport, 8 s CD · **Fortify**: 10 % damage reduction while standing still |
| L10 | Tracking vs. Ambush vs. Equipment | **Tracker** (active): nearest player on the compass for 15 s, 45 s CD · **Ambush**: first hit after sneaking +25 % · **Tinker**: gear loses 20 % less durability |

| Upgrade | improves |
|---------|----------|
| L11, L15, L19, L23 | choice of L6 (Grapple / Ghost Walk) |
| L12, L16, L20 | choice of L7 (Potion Mastery / Iron Gut) |
| L13, L17, L21 | choice of L8 (Eagle Eye / Smoke Screen / Midas Touch) |
| L14, L18, L22 | choice of L9 (Phase Dash / Fortify) |
| L15, L19, L23 | choice of L10 (Tracker / Ambush / Tinker) |
| L15 | **Swift Feet** stays active in combat |

The individual upgrade texts are in the tooltip of each upgrade level and use the values from the design document. The numbers live in `SkillTreeConfig.java` (section *Utils choice perks*) as arrays per upgrade tier.

## Animations

Every mechanic has visible and audible feedback. Everything uses vanilla particles and sounds sent by the server, so it is visible without a client mod, to all nearby players. The effects are collected in `mechanics/Fx.java`.

| What | Effect |
|---|---|
| **Custom crit** | cloud of red particles + crit sparks + damage hearts on the target; crit sound, the attacker also hears a "strong hit" |
| Damage reduction (Tank, Shield) | light-blue shield wall in front of the player + shield block sound |
| Combat Sense | electric sparks on the player that was hit |
| Stun | yellow ring above the head + anvil sound; while stunned, 3 yellow "stars" circle above the head |
| Living Immortal | totem particles + golden spiral rising around the player |
| Rage | start: red shockwave + roar; active: red swirl at the feet + angry particles; end: smoke |
| Shield | start: blue dome + armor sound; active: rotating blue ring; end: smoke |
| Cooldown over (Rage/Shield) | "ding" + green particles, only for the player, and "… is ready!" |
| Ambush | sword sweep + dark red particles |
| Fortify | grey ring on the ground when it activates, then a few grey particles every second |
| Ghost Walk | puff of smoke when turning invisible and when revealed |
| Grapple | gust of wind + cloud trail behind the player |
| Phase Dash | portal particles at the start, along the path and at the destination |
| Eagle Eye / Tracker | expanding ring of light; Tracker upgrade: particle trail at the target, only visible to you |
| Smoke Screen | large smoke cloud |
| Midas Touch / Scavenger | gold or green particles + sound when a drop was doubled |
| Potion Mastery | witch particles when a potion was not used up |
| Every spell | purple mana sparks at the hand + enchanting-glyph particles; plus the spell's own animation (hearts, flame beam, shockwave, lightning …) |
| Hex | dark purple swirl around the cursed player while the hex lasts |
| Skill unlocked | spiral in the path colour (Combat red, Utils cyan, Support purple) + totem sparks + level-up sound |

**Animations of the big spells** (`spell/SpellFx.java`):

| Spell | Animation |
|---|---|
| **Sanctum** | flash and pillar of light above the caster, bell; a golden rune circle with a pentagram is drawn on the ground; pillars of light on all allies; for the whole duration golden halos and pulsing light links from the caster to every ally |
| **Hex of Corruption** | purple vortex around the caster, then a curse orb flies to the target (about 0.6 s) and hits with a purple flash, soul explosion and ink rings; the target hears the elder guardian curse; while the hex lasts, a curse circle rotates under their feet |
| **Demon Shockwave** | 0.75 s charge: soul fire is pulled into the caster, the heartbeat sound speeds up; then a red flash, explosion, sonic boom and a shockwave of souls, flames and red/black particles |
| **Angels Circle** | two beating wings of light on the back (2 s), two counter-rotating rings of light grow to the full radius, light trickles down |
| **Buff Shower / Buff Shower+** | rain of drops in the buff colours (Speed blue, Strength red, Fire Res orange); denser and with a golden ring for Buff Shower+ |
| **Dixiklo** | toxic green and brown bubbles rise from the cloud for its whole lifetime, with bubbling sounds |
| **Firebolt** | fire impact; the advanced version has a double flame beam and a small explosion |

Gameplay-relevant: **Demon Shockwave only hits after the charge (0.75 s)**, from where the caster stands at that moment. **Hex** takes effect when the orb hits (about 1.1 s after choosing the target). Mana and cooldown are spent at cast time and refunded if the spell is interrupted before it lands (caster dies, logs out or changes dimension, target gone or no longer hittable).

## HUD: action bar & cooldown scoreboard

**Proc messages in the action bar.** When a skill triggers randomly or automatically, it is shown briefly above the hotbar. If several trigger at the same moment, they appear in one line, e.g. `✦ CRIT +50%   ⚡ STUN`.

| Message | When | Who sees it |
|---|---|---|
| `✦ CRIT +X%` | custom crit (not during Rage, where every hit crits) | attacker |
| `⚡ STUN` | stun triggered | attacker (the victim sees "You are stunned!") |
| `◉ MARKED` | Combat Sense makes the enemy glow (only if they were not glowing before) | attacker |
| `⛨ BLOCK -X%` | damage reduction triggered (not with Shield active, which always blocks) | the player who was hit |
| `✚ LIVING IMMORTAL` | Living Immortal saves you | you |
| `⛨ FORTIFIED` | Fortify tier 4: automatically after taking a crit | you |
| `⚔ AMBUSH +X%` | Ambush hit | attacker |
| `☁ GHOST WALK` | Ghost Walk makes you invisible while sneaking | you |
| `↺ GRAPPLE +1` | Grapple tier 5: a hit gives back one charge | you |
| `⚗ POTION KEPT` | Potion Mastery: potion not used up | you |
| `✪ MIDAS x2` / `♻ SCAVENGER x2` | drops doubled | you |
| `☠ HEX: HEAL → DAMAGE` | Hex turns healing into damage | the cursed player |

A proc message stays for at least `hud.proc_hold_seconds` (1.5 s) before the Rage/Shield timer overwrites it. `hud.proc_messages 0` turns the messages off.

**Cooldown scoreboard.** On the right of the screen is a list of everything currently on cooldown, with the time left: spells (purple), Combat abilities (red/gold), Utils perks (cyan), and internal cooldowns such as Stun or Living Immortal. Grapple and Phase Dash charges are listed separately ("Grapple", "Grapple #2"). The longest cooldown is on top, the last 3 seconds are green. When nothing is on cooldown, the scoreboard disappears.

- Each player only sees their own scoreboard. It is not added to the server scoreboard.
- If the server shows its own sidebar (`/scoreboard objectives setdisplay sidebar …`), it is covered during a cooldown and shown again afterwards.
- Every player can turn it off and on with `/st cooldowns` (saved). To turn it off for everyone: `/st admin config set hud.cooldown_sidebar 0`.
- New cooldown keys get their display name in `CooldownHud.NAMES`. If one is missing, the key is made readable (`ghost_walk_invis` → "Ghost Walk Invis").

## Cosmetics

Everything here is purely visual and can be turned off individually in the config (`cosmetics.*`, `1` = on).

| What | Description | Setting |
|---|---|---|
| Unlock title | When unlocking, the skill name appears large in the path colour, with path and level below | `cosmetics.unlock_title` |
| Mastery | Unlocking the highest level of a path shows the title "★ MASTERED ★", three firework bursts and the challenge-complete sound | (part of `unlock_title`) |
| Server message | On mastery, a chat message for everyone: "★ Steve has mastered the ⚔ Combat path! ★" | `cosmetics.mastery_broadcast` |
| Tab list | After the name, the level of every chosen path, ★ = mastered, e.g. `Steve ⚔15★ ✦8` | `cosmetics.tab_badges` |
| Mastery aura | Particles in the path colour circle the feet, one colour per mastered path. Hidden while invisible (Ghost Walk) and in spectator mode. Every player can turn it off with `/st aura` | `cosmetics.mastery_aura` |
| Welcome message | On join: mod name, version, the three paths and clickable `[/st open]` and `[/st help]` | `cosmetics.welcome_message` |

On server start the console shows a banner with the version, the number of skills/spells/settings and the path of the config file.

## Configuration

Every number in the mod is a **setting** with a default value in the code. There are 158 in total: mechanics, cost curve, skill stats per level and all spell values. The current values are in **`config/skilltreemc.json`**. The file is created on server start, and new keys are added to it.

```json
{
  "general.max_paths": 3,
  "combat.rage.seconds": 10,
  "utils.grapple.cooldown": [12, 9, 9, 7, 7],
  "spell.firebolt.damage": 3,
  ...
}
```

Lists such as `utils.grapple.cooldown` contain **one value per tier**. The first entry is for the base level, then one per upgrade. The description of each setting says which levels are meant.

> The file stores every value, including defaults. If a new mod version changes a default, existing servers keep the old value until it is reset with `/st admin config reset <key>`.

**In-game (OP):**

| Command | Effect |
|---|---|
| `/st admin config list [filter]` | list settings, e.g. `list combat`, `list spell.hex`. Yellow means: changed from the default |
| `/st admin config get <key>` | value, default and description |
| `/st admin config set <key> <value>` | change it, e.g. `set combat.rage.seconds 12` or `set utils.grapple.cooldown 10,8,8,6,6` |
| `/st admin config reset <key>` | reset one value to its default |
| `/st admin config resetall` | reset all values |
| `/st admin config reload` | re-read the file after editing it by hand |

Changes take effect **immediately** and are saved to the file right away. Tooltips show the current values too. Exception: the fixed attribute bonuses from the design document (+1 heart at L2, Soft Landing −20 %/−40 %) are not configurable. The extra hearts of DPS and Tank are (`skill.combat.dps.extra_hearts`, `skill.combat.tank.extra_hearts`).

Key groups:

| Prefix | Content |
|---|---|
| `general.*` | max paths, free in creative mode |
| `cosmetics.*` | unlock title, tab list, mastery aura, server message, welcome message (each on/off) |
| `hud.*` | proc messages in the action bar, cooldown scoreboard (global on/off) |
| `cost.*` | cost curve per path (`base × factor^(level−1)`) |
| `combat.*` | Stun, Rage, Shield, Living Immortal, combat timer |
| `skill.*` | stat values of the levels (crit, reduction, glowing, mana, Swift Feet, Scavenger …) |
| `utils.*` | Utils choice perks and their upgrades |
| `spell.<id>.*` | mana, cooldown and values of every spell |
| `support.*` | Hex range |

## Chosen values

The design document leaves these values open. They were chosen to fit the given numbers, and all of them can be changed in the configuration:

| Area | Value | Reasoning |
|---|---|---|
| Cost curve | `2 × 1.15^(L−1)` (Utils `× 1.11`) → Combat L15 ≈ 14, Support L18 ≈ 21, Utils L23 ≈ 20 XP levels | a whole path ≈ late-game grind, single levels stay affordable |
| Combat DPS L6–14 | Crit Rate 7, 10, 12, 15, 17, 20, 22, 25 %; Multiplier 10, 12, 15, 18, 20, 22, 25, 30 %; Reduction Rate 6, 6, 7, 7, 8, 8, 9, 10 % | 25 % crit rate at max level; the off-branch stat (reduction) ends at 10 % and keeps the L3 percent (7 %) |
| Combat Tank L6–14 | Reduction Rate 7 → 25 %, Percent 10 → 30 %; Crit Rate 6 → 10 % | mirrors DPS (crit multiplier stays at the L3 value, 7 %) |
| Combat Sense | 1.5 s (L4), 2 / 2.5 / 3 s (L7 / L10 / L13) | "short", but upgrades are noticeable |
| Stun | 4 % | middle of "3–5 %" |
| Living Immortal | 25 % proc chance | rare with a 40 s cooldown, but reliable enough |
| Rage / Shield | 10 s / 8 s base duration | Rage gets longer with hits, Shield (100 % reduction) is shorter |
| Heal Ray | 10 mana, 30 s | single target, so cheaper than Area Heal |
| Advanced spells | +1–2 hearts or damage, bigger radius/range, Firebolt +10 mana | "stronger than base", "higher mana cost" |
| Buff Shower | Speed + Strength 90 s | according to the document Buff Shower+ raises it to 3 min |
| Dixiklo | 6.5 s cloud, radius 3, effects 5 s each, stationary | "5–8 s", stationary is the most predictable |
| Demon Shockwave | debuffs max 8 s | scales with distance |
| Demon Shockwave damage | 58.5 at the centre (`spell.demon_shockwave.max_damage`) | magic damage ignores armor points, only Protection counts (full Protection IV = −64 %). 1 block from the centre against full Protection IV netherite: 58.5 × 0.95 × 0.36 ≈ 20 HP = 10 hearts. Without Protection it is an instant kill |
| Hex impact | 10 HP = 5 hearts (`spell.hex.damage`), floor 1 HP (`spell.hex.min_health`) | true damage: ignores armor, Protection and absorption; never kills; players already at ≤ ½ heart take nothing; creative players take nothing |
| Support mana L7–17 | 175 / 200 / 250 / 300 max, 1.25 / 1.5 / 1.75 regen | Sanctum (150) and Hex (130) stay castable |
| Grapple | strength 1.5, lift 0.35 | not tried in-game yet, adjust if needed |
| Ghost Walk | sneak speed +0.15 / +0.3; invisibility while sneaking with 8 s cooldown | no permanent invisibility by sneak spamming |
| Smoke Screen | radius 4 | close-range escape tool |
| Midas Touch | Fortune I; XP bottle 0.5 % per block | bottles are rare, no XP farming on stone |
| Fortify | active after 0.5 s standing still; Auto-Fortify 5 s | — |
| Ambush | armed after 1 s of sneaking, stays armed for 5 s; re-invisibility 1.5 s | — |
| Iron Gut L20 | 1 HP every 4 s with full hunger | about like Regeneration I, only half as fast |
| Hex | range 50 blocks | — |

Not implemented yet: **XP bottles with crafting recipes** (payment is done with XP levels) and **Utils filler levels**. There is no room for filler levels, because every level from L11 on is already an upgrade level.

## Differences from the design document

- **Hex of Corruption** opens a menu with the heads of the players in range (50 blocks) instead of a book. Reason: without a client mod the server cannot open a book editor and wait for the input.
- The **lightning** of Hex is visual only and causes no fire or block damage.
- **Casting fast spells:** the document does not define the controls. Implemented: right-click with the orb (cast) and sneak + right-click (switch slot).
- **Active Utils perks:** the document names no input. They are used with the Utility Compass, because sneak + F is already used for Rage/Shield.
- **Eagle Eye** shows "all players on the compass": a compass needle can only point at one spot. So it points at the nearest player, and the action bar lists everyone with direction and distance.
- **Ghost Walk – invisibility breaks on attack:** in vanilla, invisibility does not break on attack. The mod does this for Ghost Walk invisibility so that the L23 upgrade ("only breaks after 1 s") makes sense.
- **Fortify Auto-Fortify** reacts to crits from the mod's own crit system (Combat), not to vanilla jump crits.

## Changing & extending

| What | Where |
|---|---|
| Numbers in a running game | `/st admin config` or `config/skilltreemc.json` |
| Default values in code | `SkillTreeConfig.java` (mechanics), `skills/ModSkills.java` (stats, `skill.*`), spell classes + `spell/ModSpells.java` (`spell.*`) |
| Level contents, icons, order | `skills/ModSkills.java` |
| New spell | create a class in `spell/spells/` extending `Spell`, implement `cast()`, register it in `ModSpells`, attach it to a skill with `.spell("id")` |
| New mechanic | class in `mechanics/`, register it in `SkillTreeMC.onInitialize()` |
| New setting | `Settings.number(key, default, description)` or `Settings.tiers(...)`; read it in code with `.get()` / `.tier(n)`; in description texts as `{key}`, `{key@2}` or `{key%}` |

What a node in `ModSkills` can give (combinable):

```java
new Skill("combat.l2", COMBAT)
        .level(2).name("+1 Heart").icon(Items.GOLDEN_APPLE)          // level = row in the tree
        .requires("combat.l1")                                       // requirement
        .requiresAny("a", "b")                                       // at least one of them (after choice levels)
        .exclusiveGroup("combat.branch")                             // only one of the group
        .stat(Stat.CRIT_RATE, 0.05)                                  // value for a mechanic (highest wins)
        .stat(Stat.CRIT_RATE, SOME_SETTING)                          // … or from the configuration
        .attribute(Attributes.MAX_HEALTH, 2, Operation.ADD_VALUE)    // vanilla attribute
        .effect(MobEffects.NIGHT_VISION, 0)                          // permanent effect
        .spell("firebolt");                                          // unlocks a spell
```

For special cases there are methods to override: `onUnlock` (once), `onEnable`, `onDisable` and `everySecond`.

**Rules:** never change skill or spell IDs once the mod has been played, because they are stored in the save data. Nodes on the same level appear left to right in **registration order**, so register the left branch first. At most 7 nodes fit on one level. Errors such as duplicate IDs, a missing level, unknown requirements or unknown spells stop the server start with a clear message.

## For developers

```bash
./gradlew build       # -> build/libs/skilltreemc-<version>.jar
./gradlew runServer   # dev server in run/, then connect with a client to localhost; in the console: op <name>
```

### Project structure

```
src/main/java/org/langebe/skilltreemc/
├── SkillTreeMC.java            entry point, registers everything, startup banner
├── SkillTreeConfig.java        mechanic settings (default values)
├── config/                     Setting, Settings (registry, JSON load/save, placeholders)
├── ModCommands.java            /st commands
├── skills/ModSkills.java       ★ all levels of all paths
├── spell/
│   ├── ModSpells.java          ★ all spells with their values
│   ├── Spell.java              base class (cast, upgrade via replaces)
│   ├── spells/                 the individual spells
│   ├── SpellCaster.java        fast slots, checks (stun/mana/cooldown), casting
│   ├── Mana.java               mana, regeneration, boss bar
│   ├── SpellOrb.java           orb item
│   ├── SpellUtil.java          target search, particles
│   └── SpellFx.java            animations of the big spells
├── mechanics/
│   ├── CombatMechanics.java    crit, reduction, Combat Sense, stun proc, Living Immortal
│   ├── ActiveAbilities.java    Rage, Shield
│   ├── Stun.java, Hex.java     status conditions
│   ├── UtilityMechanics.java   Swift Feet, Scavenger, loot hook
│   ├── UtilityPerks.java       Utils choice perks + upgrade tiers
│   ├── UtilityActives.java     Grapple, Eagle Eye, Smoke Screen, Phase Dash, Tracker, Utility Compass
│   ├── UtilityPassives.java    Ghost Walk, Potion Mastery, Iron Gut, Midas Touch, Fortify, Ambush, Tinker
│   ├── CombatTracker.java      "in combat" detection
│   ├── Fx.java                 all animations (particles + sounds)
│   ├── CooldownHud.java        cooldown scoreboard (sidebar per player)
│   ├── Cosmetics.java          unlock titles, mastery, tab list, aura, welcome message
│   ├── Scheduler.java          delayed/repeated tasks
│   └── Feedback.java           action bar (incl. proc messages), chat, sounds
├── input/InputHandler.java     orb/compass right-click, sneak + F
├── mixin/                      damage/healing/effects (LivingEntity), offhand key (packet handler),
│                               potions (PotionContents, ThrownSplashPotion), hunger (Player, FoodProperties),
│                               durability (ItemStack), tab list name (ServerPlayer)
├── core/                       Skill, Stat, SkillRegistry, SkillManager, Cooldowns
├── data/                       SkillPath, PlayerSkills, PlayerState, SkillData (persistence)
└── gui/                        menus (path selection, path, spells, Hex target) + ItemBuilder
```

### Data

Two Fabric data attachments are attached to the player. They are saved with the player file and survive death:

- `skilltreemc:player_skills`: unlocked skills, active skills, chosen paths.
- `skilltreemc:player_state`: mana, cooldowns (as world time, so they survive relogs and restarts), fast slots, selected slot, whether the cooldown scoreboard or the mastery aura is hidden.

Not saved: running Rage/Shield phases (they end on logout and the cooldown starts), Stun, Hex and the combat timer.

## License

[GPL-3.0-or-later](LICENSE) © LANGEBE
