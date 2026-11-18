This mod changes death drops and combat with players for creatures it can identify as both player-owned and created by a spell or magical summoning staff.

<span style="color:#d6a100">**AI usage disclaimer** - This mod was developed with AI-agent assistance using [this agent workflow](https://github.com/mahghuuuls/minecraft-1.12.2-mod-agent-workflow). The project owner reviewed the work during development.</span>

This mod can automatically recognize summons that use the standard [Electroblob's Wizardry](https://www.curseforge.com/minecraft/mc-mods/electroblobs-wizardry) summon system when it can verify they belong to a player. That includes summons added by other mods, such as the Skeleton Mage from [Ancient Spellcraft](https://www.curseforge.com/minecraft/mc-mods/ancient-spellcraft), as well as Wizardry's own Zombie Minion.

Other supported examples include the Foxhound conjured by [Spell Bundle](https://www.curseforge.com/minecraft/mc-mods/spell-bundle) with [WizardryUtils](https://www.curseforge.com/minecraft/mc-mods/wizardryutils) and [Quark: RotN Edition](https://www.curseforge.com/minecraft/mc-mods/quark-rotn-edition). The Sludge Wand's Lil' Sludge from [Fish's Undead Rising](https://www.curseforge.com/minecraft/mc-mods/fishs-undead-rising) works even without Wizardry installed.

Three settings are enabled by default and can be turned off independently.

- Remove these summons' own death items and XP, including supplied equipment.
- Keep these summons from targeting or attacking players.
- Protect these summons from player attacks.

Protection covers melee attacks, arrows and some spells when the attacker can be identified. Enemies killed by summons keep their normal rewards. Deliberate sacrifice and dismissal still work. Edit `config/wizardrysummontweaks.cfg` and restart the server or reopen your singleplayer world to apply changes.

The Forge file targets [Cleanroom](https://github.com/CleanroomMC/Cleanroom). Install the mod on the server for multiplayer or in your game instance for singleplayer. Multiplayer clients can join without this mod.

[Source and compatibility details](https://github.com/mahghuuuls/wizardry-summon-tweaks#compatibility)
