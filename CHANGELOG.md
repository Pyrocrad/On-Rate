# Changelog

Updates come through the Plugin Hub. Each heading is the date of that update; **Next update** is not out yet.

**Next update**

*Highlights*
- **Three new pages:** Fortis Colosseum, Tempoross and Wintertodt.
- **Clearer verdicts:** boss pages with less than half a unique expected say "Too early", and the middle of the scale has three words (A bit dry, On rate, A bit spooned).
- **More careful counting:** pets, Zalcano and items several bosses drop are counted more accurately, and unreadable saved data is kept instead of overwritten.

*Changes that affect your numbers*
- **Pets:** a pet you can only have once no longer counts as if you could get several. This changes the top verdict of pages with a pet (TzKal-Zuk, Tempoross, Wintertodt, Royal Titans and, a little, every boss with a pet), the account total and "chance of one by now". The Chaos elemental pet is now judged as "have it or not".
- **Verdict words:** the old "On rate" band (35% to 65%) is split into A bit dry, On rate and A bit spooned.
- **Zalcano:** the crystal tool seed now counts the players around you, so its expected count and the trip card's "Unique value" change.
- **Rates and caps:** Lil' Zik in Hard Mode is 1/500 (was 1/650); from your 50th kill Vorkath's head shows a 100% chance of one by now; the dragon axe is judged on the Dagannoth Kings and Wintertodt together; GP value never expects more than one copy of an item you can only have once.
- **Group bosses:** kills from before On Rate counted your team now use the team of the first kill it counted, instead of counting as solo.
- **Alchemical Hydra:** dragon thrownaxes and dragon knives no longer count as Hydra uniques. Wyrms, drakes and hydras drop them too, and the collection log has one total for all of them, so On Rate can't tell which came from the Hydra. That also means the thrownaxes and knives the Hydra really dropped aren't counted. The Hydra page loses both rows, expects two uniques fewer per 2,000 kills (about 4% fewer), and its received count, GP value and your account totals no longer include them. Ones already in your History stay saved, but no longer show in Recent or as your last unique.

*Everything in this update*
- **Unreadable saved data is kept:** if On Rate can't read an account's saved data, History or raids, it starts fresh for that part but keeps a copy of the old data (under a ".corrupt" key in RuneLite's settings) instead of overwriting it. If that happens to you, please tell us in an issue or on Discord.
- **Clearer text:** a pass over the panel, settings and chat wording.
  - **Shorter hovers:** the account **?** help (165 words) and the Uniques tile note (88) are about half as long. The Discord line is gone from both help hovers (the link at the bottom stays). The setup line and the Unique value hover no longer repeat their own labels.
  - **Neutral wording:** "50% / 90% chance by 693 / 2,302 kills" now reads "Starting from none, 50% / 90% chance of one takes 693 / 2,302 kills". The chance rings say "40% so far" instead of "next: 40%", and the Expected tab's bar is "how close that is to the next whole one" instead of "progress to the next one".
  - **Clearer statistics:** the dry streak says "Your last one came before On Rate started tracking" instead of contradicting itself, far-out results say "1 in 1,339 players", the Compact table's Luck column has a hover, and the pets hover says what the ranking is.
  - **Same words everywhere:** *uniques* (not "drops") for the things counted, *got* (not "received"), *view* (not "look"), and "Too early to judge" in the account footnote, matching the boss page's "Too early". The **Trip card** setting lost its "This".
  - **Settings text:** GE value now says it also shows on boss pages, Items tab mentions pets, Use raid points is half as long, and the Raids section no longer tells you to change values that most players can leave alone.
  - **Account help fixed:** it said team bosses assume you do all the damage. It now says they use your share of the kill.
  - **Chat:** milestone messages read "Magic fang: 1,024 kills without one, 2x the drop rate." Plurals agree everywhere ("1 kill", "1 unique").
  - **Dropped:** "Click to open" in hovers of rows that already light up, and the right-click hint in the ITEMS legend (it stays in the **?** help).
- **New welcome guide:** "Are you on rate?" says in one line what On Rate does, then four numbered steps (collection log, Loot Tracker, go kill something, settings) with a thin line between them. Under them, *Where the words come from* lists each verdict with the rank among 100 typical players it stands for, and *What this does not mean* says that "players" is maths, not real accounts, that every kill is its own roll, and that rates are solo base rates without boosts. *Things you may see* explains the moments that could surprise you: the collection log reminder, "Not synced", the luck graph starting tomorrow, short names in the search, leaving items out, resetting a boss, and what to do if saved data was unreadable. The settings step says Royal Titans and Yama count as duo kills unless you set them to Solo or Some of each. The button at the bottom is now *Got it, open settings*, so it's clear it opens them.
- **Chaos Elemental page:** Chaos Elemental has its own boss page again, with its kill count, History and its shared items (dragon pickaxe and pet), each judged on every boss that drops it. It shows "All shared" instead of a verdict, since none of its uniques are its own.
- **Pictures for shared items:** the dragon axe, the Chaos elemental pet and other items several bosses drop now show their picture straight away, instead of initials until you opened their collection log page.
- **Hovers in rows:** item, Uniques tile, GP value and account hovers show their numbers as label / value rows with a thin line between them, instead of a stack of sentences. An item hover opens with "Your result", names the boss under the item, and lists drop rates per wave, delve level or raid one per row. The Uniques tile's hover drops its dry / spooned explanation, and the trip card's per hour, unique value, verdict and uniques use the same rows. An item you have none of adds "Each kill is its own roll" to its hover. The Items tab and shared items use the same hover as an item on its boss page. The Recent tab (day totals and latest uniques) and History (each line, and the seen / uniques summary under the list) use the same rows.
- **Items tab, Driest and Luckiest:** up to 15 items instead of 10 (Driest can hold fewer: it only lists items below average with at least 1 expected), and each row also says **got / expected** (`0 / 2.60`, `3 / 1.20`) beside the dot, in a column of one width so the dots line up and the row is no taller. The verdict word, the dot, the hover and the order are unchanged, and a line under the switch and the hover say that expected is an average, not drops you were owed. Pets keep their verdict and chance.
- **Calmer panel:**
  - **Items you can't read yet are quiet:** on a boss page, an item with none got and less than half expected (the same line as "Too early") has a grey name and count and a grey tick, and a slimmer bar. Every item stays in its place, and its hover is the same. Counts are the regular weight and grey until you have one; names stay bold.
  - **Account lists:** each row has one neutral track with a dot instead of five colour bands (the Luck gauge keeps its bands), and "On rate" is white. The other verdict words use the boss page's calmer colours.
  - **One control line:** the account's Bosses tab has the view switch (**⇄**) at the end of the Sort line, the "BOSSES" caption is gone, and the Items tab has the same switch.
  - **Orange means selected:** the trip card, the welcome guide and the "Load bosses you killed" line are no longer orange; orange stays on tabs and the focus ring, so it can't be mistaken for "dry".
  - **Less repeating:** History no longer prints a dash on kills without a drop, the purple chance line is one short sentence (what your raids with points averaged is under History), the Recent tab's colour key is in the hover of Today and Last 7 days, and the footer is one line: About · Feedback · Reset this boss…
  - **Same words:** "have it", "have both" and "have all 3" in every view (the bars and rings used to say "obtained"), and section titles are small grey capitals with ▸ / ▾ where they fold.
  - **Nothing cut off in the tiles:** "Very spooned" and "A bit spooned" no longer lose their last letters when the panel shows its scrollbar, and a long line like "only 1 in 10,000 has none" goes onto a second line instead of ending in "...".
- **Clearer panel:**
  - **Expected tab:** the progress bars are one neutral colour. They show how far you are to the next whole expected unique, not luck.
  - **Too early:** a boss page with less than half a unique expected says "Too early" instead of a verdict, because a few kills can't tell dry from lucky. "Dry" and "Spooned" are drawn in a quieter colour than "Very dry" and "Very spooned" on boss pages. The account overview keeps its verdicts as they were.
  - **Account overview:** the **Account** button is now a bold **‹ Account** link next to the **?** on boss pages, and the header is one line. The sort stays right above the bosses, and the notable line and the last unique are one block.
  - **Luck graph:** the axis says *Spooned*, *On rate* and *Dry*, the line is neutral (the bands carry the colour), the day under the mouse is marked, the latest verdict is written beside the last point and a middle date shows when there's room. It needs two days of history before it draws.
  - **Keyboard and screen readers:** tabs, History, the setup line, the luck graph switch, Reset, the Account link and the look switch can be reached with Tab, used with Enter or Space, show a focus ring and have a name. Bar text has a darker outline, and long boss names in the Chance rings view wrap to two lines.
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

*Your numbers may change:* see **A fairer verdict** below, which changes when a result counts as dry or spooned.

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

**First release, 18 October 2026** (1.0.0)
- Expected vs received uniques for every boss and raid, luck percentile and verdict, account overview with GE value luck, raid points, collection log sync, five luck views and optional extras.
- Shared items (dragon pickaxe, godsword shards, Virtus, onyx...) are judged on all their bosses' kills together.
- God Wars bodyguards count towards their general's uniques.
- Nex, Yama, the Hueycoatl and Corp count your team size at each kill.
- A **\*** marks bosses whose numbers are an estimate.
- Maggot King.
- Rates checked against the wiki (see [docs/RATES.md](docs/RATES.md)), with fixes for the Grotesque Guardians, Araxxor, Alchemical Hydra, Tombs of Amascut item mix and Mad Angel teleports.
