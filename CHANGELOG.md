# Changelog

**Next update** (waiting for the Plugin Hub)

*Easier for new players*
- A boss whose collection log page isn't loaded yet says **Not synced** instead of "Very dry", and doesn't count in your account until it's loaded. The account overview says which bosses are waiting.
- A shorter welcome guide: three steps.
- The collection log line at the top lists the bosses you killed first ("Load 2 bosses you killed") and says you only need the bosses you do, not all 58 pages.
- Royal Titans and Yama pages say when your kills count as duo, and where to change it.
- The raid purple chance settings are folded away under **Raid purple chances**, and **Team bosses** comes before **Raids** in the settings.
- Raid pages say in one grey line how many raids use an average purple chance instead of your points; hover it for how to change that.
- Right after loading a log page, hovering an item no longer says "at least 0 kills (0.00x)": it says your streak is counted from your next kill.
- Clearer notes for shared items ("Draconic visage also drops from King Black Dragon..."), one line instead of two on an empty account, and *Reset this boss* is a small link instead of a big button.

*New*
- **History** on every boss page, folded until you click it: the kills On Rate saw and what they dropped, with how many kills each unique took. Raids show your points and that raid's purple chance, group bosses your team size, Royal Titans the titan you looted and Doom the delve level. It replaces the *Drop history* extra.
- The account overview has three tabs: **Bosses**, **Items** (your 10 driest or luckiest items, and your 5 luckiest and 5 driest pets) and **Recent** (your latest uniques across every boss). Click any of them to open its boss.
- Hovering an item also says your luck in plain words ("about 1 in 6 players is this dry") and your chance in the next 100 kills.
- **Yama** has a solo/duo setting like the Royal Titans. Both count as duo unless you change it.
- Royal Titans prayer scrolls each have their own row (1/150 each, as you loot one titan per kill).
- **Feedback:** an [On Rate Discord](https://discord.gg/gh9fx6T9zn), with a quiet link at the bottom of the panel.

*Changed*
- The **This trip card** is on by default (turn it off under *Display* in the settings).
- The very first time On Rate starts, it opens its panel with the welcome guide; **Got it** then opens the settings. The guide has a step about the settings that change the numbers most and the **Luck view**.
- Shared items (dragon pickaxe, godsword shards, Virtus, onyx...) only show on the page of each boss that drops them. In the account overview they count in the verdict and GE value but have no rows. The "unopened bosses = 0 kills" setting is gone.
- Bosses with **0 kills** no longer show in the account overview.
- The GE value is one line, on the account overview and on boss pages; hover it for the details. The notes under the account's bosses are one line too.
- The "\*" for estimates only shows on boss pages.
- The **in-game overlay** is wider, with your driest item and how long it's been dry on separate lines.
- Tormented Demons and demonic gorillas are no longer tracked: they're in the collection log's Other tab.
- The README is short now; the details moved to [docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md) and this changelog to its own file.
- **Faster:** after a kill On Rate only saves what changed, the GE value luck is worked out about 15 times faster (with the same result), and the panel isn't rebuilt while it's closed. The old drop history data is no longer saved, so your saved data gets smaller too.

**1.0.0** (on the Plugin Hub since 18 October 2026)
- Expected vs received uniques for every boss and raid, luck percentile and verdict, account overview with GE value luck, raid points, collection log sync, five luck views and optional extras.
- Shared items (dragon pickaxe, godsword shards, Virtus, onyx...) are judged on all their bosses' kills together.
- God Wars bodyguards count towards their general's uniques.
- Nex, Yama, the Hueycoatl and Corp count your team size at each kill.
- A **\*** marks bosses whose numbers are an estimate.
- Maggot King.
- Rates checked against the wiki (see [docs/RATES.md](docs/RATES.md)), with fixes for the Grotesque Guardians, Araxxor, Alchemical Hydra, Tombs of Amascut item mix and Mad Angel teleports.
