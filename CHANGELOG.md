# Changelog

**Next update**
- **Pet-only pages fixed:** the verdict at the top of a page counted a pet you can only have once as if you could get several, so TzKal-Zuk at 300 kills without the pet said "A bit dry" while the pet itself said "Very dry". The top verdict, the account total and "chance of one by now" now add up each item the way it counts, so they agree with the item rows. This also touches Tempoross, Wintertodt, Royal Titans and, a little, every boss with a pet.
- **Chaos elemental pet fixed:** under "Shared with other bosses" it was judged on the chance of having it instead of the expected number, which made a missing pet look on rate. It's now "have it or not" like every pet.
- **Vorkath's head:** from your 50th kill the hover says 100% chance of one by now (the 50th kill always drops one), and the 50% / 90% kill counts stop at 50.
- **Lil' Zik:** 1/500 in Hard Mode (was 1/650), as on the wiki.
- **GP value:** an item you can only have once is never expected to be worth more than one copy.
- **Trip summary in chat:** when a trip of 3 or more kills ends (another boss, the button, or the first kill after the break), one line sums it up: "Trip at Zulrah: 43 kills, 1 unique (Magic fang), 0.29 expected · Spooned." It follows the Drop messages switch.
- **Last unique:** the account overview says how long ago your last unique was: "Last unique 12 days ago: Magic fang (Zulrah)." It goes by the drops On Rate saw itself.
- **Drop records:** an item's hover shows the fastest and longest it took between drops since you installed On Rate ("fastest drop after 17 kills, longest after 1,204").
- **Pets fixed:** the Items tab's Pets list and its "You have X of Y pets" count left out the Chaos elemental pet, because two bosses drop it. It's now counted once, judged on both bosses' kills.
- **Luck over time** stays flat across days you didn't play and only moves on the next day you did, instead of a slope that suggested your luck drifted while you were away.
- **Fortis Colosseum:** a new page, counted in claimed runs. Loot piles up wave by wave and is only yours when you claim it, so each run counts every wave up to the one you claimed at (from the wiki's chances per wave), and History lists runs by wave. Runs from now on use their real wave; completions from before On Rate count as wave 12 claims and are marked as an estimate. The item hovers show the chance by the wave you claim at. Dizana's quiver is guaranteed, so it isn't counted.
- Doom's "Rate per delve level" hover no longer starts with a stray comma.
- **Wintertodt:** a new page, counted in reward rolls like Tempoross. Rolls from now on are counted exactly as you search the reward cart; rolls from before are estimated as 3 per subdue minus the rewards still in the cart, and marked as an estimate. Tome of fire, the Pyromancer outfit (one row: each roll gives the piece you have fewest of), warm gloves and bruma torch (3 each), Phoenix, and the dragon axe.
- **Dragon axe** now drops from Wintertodt too, so like the draconic visage it's judged on the Dagannoth Kings and Wintertodt together, under "Shared with other bosses", instead of in the Kings' own verdict.
- **Tempoross:** a new page, counted in reward rolls (one per permit) because that's how its loot works. Rolls from now on are counted exactly from your reward permits; rolls from before On Rate are estimated as 4 per subdue minus the permits you hadn't spent yet, and marked as an estimate. Fish barrel, tackle box, big harpoonfish, Tome of water, dragon harpoon and Tiny tempor.
- **Trip value fixed:** the trip card's "Unique value" used the boss's average over all your kills, so a Doom trip of only delve level 1 (no uniques there) still showed a value from your deeper delves. It now adds up the trip's own kills: their delve level, raid points and team. A level 1 trip says ~0/h.
- The trip card's "Unique value" hover now names the item most of it comes from when one item is at least half of it ("Most of it is the Crystal tool seed (98%).").
- **Overlay tip, once:** during a long trip at one boss (30 kills or 45 minutes), the trip card offers the in-game overlay with one line, if it's off. Clicking it turns the overlay on. It shows once per account.
- **Views from the panel:** the item view is now a small **⇄** switch next to ITEMS (and BOSSES on the account overview): click it for the next look, and it's kept. **Luck over time** is always there on the account overview, opened and closed by clicking its title. Both left the settings, which now hold only things that change the numbers plus a few extras (GE value, trip card, overlay, colours, chat).
- **One fact under "On rate":** when a boss or your account is exactly On rate, one sentence under the verdict says what stands out, if anything does: the driest item ("Driest: Magic fang, drier than 91% of players."), an item most players would have by now ("6 in 10 players would have a Tumeken's shadow by now."), or the luckiest item ("Luckiest: Twisted bow, luckier than 99.5% of players."). Nothing shows when nothing stands out.
- **Three words for the middle:** what used to be "On rate" from 35% to 65% is now **A bit dry** (35 to 45%), **On rate** (45 to 55%) and **A bit spooned** (55 to 65%). Very dry, Dry, Spooned and Very spooned are unchanged. The same words show everywhere: the tiles, the lists, the overlay, the trip card and the luck graph. The line under a verdict follows them: "about average" only for On rate; A bit dry and A bit spooned show how common they are ("1 in 3 as dry").
- **Leave an item out:** right-click an item on a boss page to leave it out of the calculations, for a drop you got somewhere else (an Araxyte head from araxytes, an Occult necklace from a regular smoke devil) or items you don't care about (jars, seeds). It stops counting for that boss's verdicts, the account overview, the Items tab and dry messages, and moves to a greyed *Left out* list where a right-click counts it again. Items that also drop elsewhere say so when you hover them.
- **Percentages in the hover:** hover a verdict, an item or a boss for your result as a percentage, always from the side that holds most players: "drier than 67%" (67% of players have more), "luckier than 83%" (83% have fewer), with "1 in 1,205" added for far-out results. The panel itself keeps the short words ("1 in 3 as dry").
- **Zalcano fixed:** the crystal tool seed is one drop for the whole team, given to one player, but On Rate counted it as if you got your own roll every kill. It now counts the players around you, like at Nex and Corp, so the seed's expected count and the trip card's "Unique value" (it said about 4m/h) are right. Shards and the pet are still yours alone.
- **Raid trips fixed:** a trip ended after 30 minutes without a kill, so a raid that took longer started a new trip every time and the trip card said "1 raid". Raids, the Inferno and the Fight Caves now get 2 hours; another boss or the *Start a new trip* button still end a trip right away.
- Group bosses (Nex, the Hueycoatl, Corp, Zalcano): kills from before On Rate counted your team now use the team of the first kill it counted, instead of counting as solo.

**Update of 21 October 2026**
- **Two verdicts** at the top of every boss page and the account overview: **Uniques** (how many you got) and **GP value** (what they're worth), each with its numbers. On the account they stay at the top on every tab.
- **Recent** starts with **Today** and **Last 7 days**: kills and uniques in total and per boss, green when you got more than expected and orange when fewer (hover for the numbers). Kills from before this update are filled in from History.
- History says "your first one" for an item you got for the first time (was "1st one"), counts in raids for the raids and in delves for Doom, and hovering the line under it lists which uniques those were.
- **A fairer verdict:** you're only dry when fewer than 35% of players at your kill count have this many or fewer, and only spooned when fewer than 35% have this many or more. Before, having none of an item at 0.4 to 1 expected said "Dry" even though most players have none either; that's now "On rate". Very small counts change most (one lucky drop on a trip is "Spooned", not "Very spooned"); big counts barely change.
- **"2.1× the drop rate without one":** hovering something you don't have yet says how far past the drop rate you are, the way players count dry (1× on rate, 2× dry, 3× real pain). The "next 100 kills" line is gone.
- **Clearer words instead of "luckier than X%":** the tiles say how common your result is ("74% have none", "1 in 3 as dry", "1 in 6 as lucky", "about average"), and hovers give the full split ("18% of players have fewer, 15% as many, 67% more").
- The collection log line at the top only shows while pages are missing; "All history loaded" is gone.
- Fixed: the panel could stop responding to clicks until you opened another plugin and came back. It now checks itself whether it's on screen, and one page that fails to draw can't freeze it.
- Faster for players with many raids: saved raids are stored apart and only written when a raid ends, so a kill no longer rewrites them all, and the luck graph's point is worked out every 10 minutes instead of after every kill.
- The overlay updates when raid points come in after the kill message.
- New sort for the bosses list: **Longest dry streak**.
- The trip card shows what the uniques should be worth per hour at your speed.
- History keeps the last 500 kills per boss (was 200).
- The bottom of the panel is one line: "Feedback? Discord" and "Reset this boss…".
- Doom of Mokhaiotl counts in **delves**: the trip card ("Delves", "delves per hour"), the overlay, hover texts and chat messages. Barrows and Moons count in chests.
- Fixed: the trip card's **Expected** could be off on the first raid of a trip (for example 0.06 when your points gave 4.4%), because it took off your average raid instead of the raid you just did. The trip card and Recent's **Today** now count each raid at your own points.
- The Zulrah note explains the 1/512: two rolls of 1/1,024 per kill.
- **Tumeken's guardian** (the Tombs of Amascut pet) is tracked, with its own formula from your points and raid level (at high levels it's well above 1/30 of the purple chance).
- **Exact Tombs of Amascut points:** the game sends your points at the end of each raid, and On Rate now uses them instead of its own estimate.
- **Vorkath's head:** your 50th kill always drops one, and On Rate now counts that (one more head expected from kill 50 on).
- Pets you don't have show your chance of having one by now next to the verdict ("On rate · 12.5%").
- The luck graph only shows on the **Bosses** tab, so the Items and Recent lists start higher up.
- Pages with two bosses (Callisto and Artio, Vet'ion and Calvar'ion, Venenatis and Spindel, Dagannoth Kings, The Gauntlet) say which boss dropped an item and at which of its own kills, in History and Recent.
- Boss pages are tidier: the notes at the bottom are behind one "About these numbers" line (hover it).
- Pages with several bosses or raid modes put the counts under the name ("184 raids · 182 Normal · 2 CM", "Rex 300 · Prime 300 · Supreme 300"); hover for the full names.

*A cleaner look*
- **Colours have one job each:** green to red only means luck; orange is only for headers, tabs and links; "Any unique" is white instead of yellow; purple is only for raid loot.
- **Lists of bosses and items** show the name with a coloured verdict on the right and a dry-to-spooned scale with a marker underneath, instead of a bar that looked like progress. Rows you can click light up when you hover them.
- Bigger, bold verdicts in the Uniques and GP value tiles at the top of every page.
- In Recent, hovering a boss under Today or Last 7 days names its uniques ("Tyrannical ring"); the total line names them all with their boss.
- Bigger item and boss pictures, and names and numbers in the lists use a bigger font. In Recent, the boss names are bigger too, and clicking a boss in Today or Last 7 days opens its page.
- Sorted by **Longest dry streak**, the streak is white while it's normal, orange when it's long and red when it's very long.
- A small **ITEMS** header explains the white line on the item bars ("expected by now").
- **Easier to read:** the darkest grey text is gone; hints and notes are a lighter grey with an (i) mark.
- **Driest / Luckiest / Pets** is a switch with the selected one highlighted.
- The same spacing everywhere, slightly tighter item rows, and every hover text has the same layout: a bold title, the numbers, and a grey footnote.
- **No more asterisk** on raids and team bosses: "est." next to the expected number says the rates are an estimate, and the hover says why.

**Update of 19 October 2026**

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
