# Where the drop rates come from

Every rate On Rate uses is in [`bosses.json`](../src/main/resources/com/expecteddrops/bosses.json). This page says where each one comes from and when it was last checked, so a question like "why does it say 1/381?" has an answer.

Unless a line says otherwise, rates come from the boss's page on the [Old School RuneScape Wiki](https://oldschool.runescape.wiki), at full contribution (solo) and without diary, Combat Achievement or Slayer task boosts.

**Last full check: 18 October 2026.**

## Checked and matching the wiki

| Boss | Wiki page | Notes |
|---|---|---|
| Abyssal Sire | [Abyssal Sire](https://oldschool.runescape.wiki/w/Abyssal_Sire) | Unsired 1/100. The jar and pet come out of the Unsired, so their per-kill rates are worked out from it. |
| Alchemical Hydra | [Alchemical Hydra](https://oldschool.runescape.wiki/w/Alchemical_Hydra) | Dragon thrownaxes and knives drop 500–1,000 at a time (counted as one drop each). |
| Amoxliatl | [Amoxliatl](https://oldschool.runescape.wiki/w/Amoxliatl) | |
| Araxxor | [Araxxor](https://oldschool.runescape.wiki/w/Araxxor) | 1/200 for any halberd piece, 1/600 for each one. You get no duplicate pieces until you have a full set, which this doesn't model. |
| Barrows | [Barrows](https://oldschool.runescape.wiki/w/Barrows) | 7 rolls of 1/2,448 per item: all six brothers killed. |
| Brutus | [Brutus](https://oldschool.runescape.wiki/w/Brutus) | |
| Callisto and Artio | [Callisto](https://oldschool.runescape.wiki/w/Callisto), [Artio](https://oldschool.runescape.wiki/w/Artio) | |
| Cerberus | [Cerberus](https://oldschool.runescape.wiki/w/Cerberus) | |
| Chaos Elemental | [Chaos Elemental](https://oldschool.runescape.wiki/w/Chaos_Elemental) | Dragon pickaxe and pet only (shared items). |
| Chaos Fanatic | [Chaos Fanatic](https://oldschool.runescape.wiki/w/Chaos_Fanatic) | |
| Commander Zilyana, General Graardor, K'ril Tsutsaroth, Kree'arra | [Zilyana](https://oldschool.runescape.wiki/w/Commander_Zilyana), [Graardor](https://oldschool.runescape.wiki/w/General_Graardor), [K'ril](https://oldschool.runescape.wiki/w/K%27ril_Tsutsaroth), [Kree'arra](https://oldschool.runescape.wiki/w/Kree%27arra) | Bodyguard rates from [Sergeant Strongstack](https://oldschool.runescape.wiki/w/Sergeant_Strongstack), [Flight Kilisa](https://oldschool.runescape.wiki/w/Flight_Kilisa) and [Tstanon Karlak](https://oldschool.runescape.wiki/w/Tstanon_Karlak): 1/16,256 per Bandos piece, 1/16,129 per Armadyl piece, 3/16,129 for the spear and Saradomin sword, 1/1,524 per godsword shard. |
| Corporeal Beast | [Corporeal Beast](https://oldschool.runescape.wiki/w/Corporeal_Beast) | Spirit shield 8/512 = 1/64. Sigil rates already include the 1/585 sigil table. |
| Crazy Archaeologist | [Crazy archaeologist](https://oldschool.runescape.wiki/w/Crazy_archaeologist) | |
| Deranged Archaeologist | [Deranged archaeologist](https://oldschool.runescape.wiki/w/Deranged_archaeologist) | |
| Duke Sucellus, The Leviathan, The Whisperer, Vardorvis | [Leviathan](https://oldschool.runescape.wiki/w/The_Leviathan), [Whisperer](https://oldschool.runescape.wiki/w/The_Whisperer), [Vardorvis](https://oldschool.runescape.wiki/w/Vardorvis) | Normal mode. Duke follows the same pattern (1/720); its wiki table didn't load during the check. |
| Giant Mole | [Giant Mole](https://oldschool.runescape.wiki/w/Giant_Mole) | Mole claws and skins drop every kill, so they aren't tracked. |
| Hueycoatl | [The Hueycoatl](https://oldschool.runescape.wiki/w/The_Hueycoatl), [Huberte](https://oldschool.runescape.wiki/w/Huberte) | |
| Kalphite Queen | [Kalphite Queen](https://oldschool.runescape.wiki/w/Kalphite_Queen) | |
| King Black Dragon | [King Black Dragon](https://oldschool.runescape.wiki/w/King_Black_Dragon) | |
| Kraken | [Kraken](https://oldschool.runescape.wiki/w/Kraken) | |
| Maggot King | [Maggot King](https://oldschool.runescape.wiki/w/Maggot_King) | Open-stomach pet rate. Taking eggs instead gives about 1/1,502, which On Rate can't see. |
| Moons of Peril | [Lunar Chest](https://oldschool.runescape.wiki/w/Lunar_Chest) | All three moons defeated. |
| Nex | [Nex](https://oldschool.runescape.wiki/w/Nex) | |
| Phantom Muspah | [Phantom Muspah](https://oldschool.runescape.wiki/w/Phantom_Muspah) | |
| Phosani's Nightmare | [Phosani's Nightmare](https://oldschool.runescape.wiki/w/Phosani%27s_Nightmare) | |
| Royal Titans | [Royal Titans](https://oldschool.runescape.wiki/w/Royal_Titans) | You loot one titan per kill and each titan has its own scroll at 1/75 (Deadeye from Eldric, Mystic vigour from Branda). On Rate assumes you loot both equally: 1/150 per scroll per kill. Duo kills halve that. |
| Sarachnis | [Sarachnis](https://oldschool.runescape.wiki/w/Sarachnis) | |
| Scorpia | [Scorpia](https://oldschool.runescape.wiki/w/Scorpia) | |
| Scurrius | [Scurrius](https://oldschool.runescape.wiki/w/Scurrius) | |
| Shellbane Gryphon | [Shellbane Gryphon](https://oldschool.runescape.wiki/w/Shellbane_Gryphon) | Belle's folly 1/75 since the July 2026 update. |
| Skotizo | [Skotizo](https://oldschool.runescape.wiki/w/Skotizo) | |
| The Mad Angel | [The Mad Angel](https://oldschool.runescape.wiki/w/The_Mad_Angel) | |
| Thermonuclear Smoke Devil | [Thermonuclear smoke devil](https://oldschool.runescape.wiki/w/Thermonuclear_smoke_devil) | |
| TzTok-Jad, TzKal-Zuk | [TzTok-Jad](https://oldschool.runescape.wiki/w/TzTok-Jad), [TzKal-Zuk](https://oldschool.runescape.wiki/w/TzKal-Zuk) | Off-task rates. |
| Venenatis and Spindel | [Venenatis](https://oldschool.runescape.wiki/w/Venenatis), [Spindel](https://oldschool.runescape.wiki/w/Spindel) | |
| Vet'ion and Calvar'ion | [Vet'ion](https://oldschool.runescape.wiki/w/Vet%27ion), [Calvar'ion](https://oldschool.runescape.wiki/w/Calvar%27ion) | |
| Yama | [Yama](https://oldschool.runescape.wiki/w/Yama) | |
| Zalcano | [Zalcano](https://oldschool.runescape.wiki/w/Zalcano) | Zalcano shard 1/750 is the best case; it drops to 1/1,500 with low contribution. |
| Zulrah | [Zulrah](https://oldschool.runescape.wiki/w/Zulrah) | Two rolls per kill: 2 × 1/1,024 = 1/512 per kill for each unique. |

## Fixed in this check

| Boss | Was | Now | Why |
|---|---|---|---|
| Grotesque Guardians | Gloves 1/500, ring 1/500, hammer 1/750, core 1/1,000 | 1/250, 1/250, 1/375, 1/500 | The Guardians roll their table twice per kill. The old numbers were per roll. |
| The Mad Angel | Ardeaglais teleport 2/25 | 1/25, 2 at a time | Same expected number of teleports, but luck is now judged per drop rather than per teleport. |

## From other sources

- **Doom of Mokhaiotl:** per-delve-level rates from the [Delve Calculator](https://github.com/Speaax/Delve-calculator) by Speaax.
- **Tumeken's guardian (ToA pet):** the [chest page](https://oldschool.runescape.wiki/w/Chest_(Tombs_of_Amascut)) gives 1% per 350,000 − 700 × raid level points, where the level counts in full up to 400 and a third above that (at most 550), and it isn't capped at 55%. On Rate uses that formula for every raid it read your points for. Next to a purple that's about 1/33 of the chance at raid level 150, 1/31 at 300, 1/19 at 400 and 1/14 at 500. Raids without points use 1/30 of the setting's purple chance.
- **Vorkath's head:** 1/50 per kill, and your 50th kill always drops one ([Vorkath's head](https://oldschool.runescape.wiki/w/Vorkath's_head)). From kill 50 on, On Rate counts that kill as a sure head, so one more head is expected than 1/50 alone gives. (The Kalphite Queen's tattered head, guaranteed on the 256th kill, is a different item; On Rate tracks the normal Kq head.)
- **Raids:** unique weights from the wiki pages for [Chambers of Xeric](https://oldschool.runescape.wiki/w/Chambers_of_Xeric/Rewards), [Theatre of Blood](https://oldschool.runescape.wiki/w/Theatre_of_Blood/Rewards) and [Tombs of Amascut](https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Rewards). The purple chance comes from your points (see the README) or from the settings.
- **The Gauntlet, Dagannoth Kings, Hespori, Obor, Bryophyta:** wiki rates, not rechecked in this pass because they haven't changed in years.
- **Vorkath, and the Alchemical Hydra's heads, jar and pet:** wiki rates. Their tables didn't load during the check, so they weren't rechecked this time.

## Checking again

When Jagex changes a drop rate:

1. Look the item up on the wiki.
2. Change the `rate` in `bosses.json`.
3. Add a line to "Fixed" above with the date.

The tests check that every rate is between 0 and 1, and that every boss has a way to count kills.
