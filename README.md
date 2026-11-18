# Wizardry Summon Tweaks

Configurable death rewards and combat protections for affected summons. An affected summon is a creature the mod can identify as both player-owned and created by a spell or magical summoning staff.

[Mod page](MOD-PAGE.md) | [Changelog](CHANGELOG.md) | [MIT license](LICENSE) | [Third-party notices](THIRD-PARTY-NOTICES.md)

## Installation

This is a Forge 1.12.2 artifact targeting [Cleanroom](https://github.com/CleanroomMC/Cleanroom). Cleanroom provides the required [MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixin-booter), minimum 12.1. Install Wizardry Summon Tweaks on the server in multiplayer, or in the game instance for singleplayer. Multiplayer clients can join without this mod.

## Configuration

Edit `config/wizardrysummontweaks.cfg` on the server or in the singleplayer instance. The three rules default to `true` and can be toggled independently. Restart the server or reopen your singleplayer world to apply changes.

```text
general {
    B:suppressSummonDrops=true
    B:protectPlayers=true
    B:protectSummons=true
}
```

- `suppressSummonDrops` removes the summon's own death items and XP, including supplied equipment. Enemies killed by summons keep their normal rewards.
- `protectPlayers` stops affected summons from targeting players and blocks supported attacks from those summons against players.
- `protectSummons` blocks supported player attacks against affected summons. Deliberate sacrifice, dismissal and natural expiry still work.

Operators can look at a creature and run `/wizardrysummontweaks inspect` to see its classification, owner and active rules. The command also accepts a loaded entity's UUID.

## Compatibility

Representative coverage for optional integrations includes the following summons.

- Zombie and Skeleton Minions, spell-created Snow Golems and Spirit Wolves from [Electroblob's Wizardry](https://www.curseforge.com/minecraft/mc-mods/electroblobs-wizardry) 4.3.19.
- The Skeleton Mage from [Ancient Spellcraft](https://www.curseforge.com/minecraft/mc-mods/ancient-spellcraft) 1.8.3.
- The [Spell Bundle](https://www.curseforge.com/minecraft/mc-mods/spell-bundle) 1.7.4 Foxhound with [WizardryUtils](https://www.curseforge.com/minecraft/mc-mods/wizardryutils) 1.3.1 and [Quark: RotN Edition](https://www.curseforge.com/minecraft/mc-mods/quark-rotn-edition) r1.6-193.
- The Sludge Wand's Lil' Sludge from [Fish's Undead Rising](https://www.curseforge.com/minecraft/mc-mods/fishs-undead-rising) 1.6.2. This case can work with Wizardry absent.

Combat protection covers melee attacks, arrows and selected damaging spells, such as Paralysis, when the mod can identify the attacker. Ordinary pets keep their original behavior. Older summons with missing origin records and attacks with an unidentified source may remain unaffected. [Threatworks](https://www.curseforge.com/minecraft/mc-mods/threatworks) 1.1.0 is optional. Supported summon hits can still earn threat and draw enemy aggro during an active encounter.
