# Changelog

Updates come through the Plugin Hub. Each heading is the date of that update; **Next update** is not out yet. How everything works is in [docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md).

**Next update**

*Removed*
- **One-time cards:** the *New in On Rate* notice about the Time page and the one-time offer to turn on the in-game overlay are gone.
- **In-game overlay:** removed, with its setting.
- **Chat messages:** sync reminders, drop messages and dry milestone messages are removed, with their settings.
- **Copy my luck:** the footer link is removed.
- **Higher-contrast bars:** the setting is removed.
- **Keyboard use:** Tab, Enter and Space activation, the focus ring, the arrow keys on the luck graph and the item rows' keyboard names are removed. The mouse works as before.
- **Welcome guide:** the getting-started card at the top of the panel, its *Show welcome guide* setting and the panel opening by itself the first time are gone. The collection log line under the title still tells you which pages to open.

*Redesigned*
- **Recent tab as tiles and pages:** **Today** and **Last 7 days** are two tiles at the top (kills, uniques, coloured when clearly more or fewer than expected; hover for the numbers). Under them a switch shows one list at a time: **Uniques** (your latest uniques, top 10 of N with show all), **By boss** (a row per boss for the last 7 days, click to open it) or **Trips** (this session's finished trips). Nothing is counted differently.

*Changes that affect your numbers*
- **Dragon 2h sword:** it now counts. Its one collection log total is judged on all nine bosses that drop it (Kalphite Queen, Callisto, Artio, Vet'ion, Calvar'ion, Venenatis, Spindel, Scorpia and Chaos Elemental), the same way as the dragon pickaxe, so no single boss looks luckier than it was. It shows under **Shared with other bosses** and counts toward uniques and GP value. Rates are unchanged.

*Forecast*
- **UNIQUES FORECAST on more bosses:** the folded forecast table under History (Raid extras) now shows for every single-fight boss, not a fixed list. Team and estimate bosses (Nex, Yama, Zalcano, God Wars, Royal Titans, Barrows, Brutus, Tempoross and others) have it with a note that the chances use your average share or estimate. Vorkath forecasts only his real rolls and says the head is guaranteed at kill 50. Combined pages (Dagannoth Kings, Gauntlet, Callisto and Artio...) get one small table per part. Doom forecasts at the delve levels you did so far. Fortis Colosseum, Moons of Peril, Wintertodt and Chaos Elemental have none. The "rest of the log" line under the verdict follows the same rule. Drop rates and verdicts are unchanged.
- **Doom times from your first delve:** on the Time page, each delve level uses your own average time from its first timed delve (it was 20), and the wiki time for levels you haven't timed. The Doom forecast uses the same time per delve, so both pages show the same hours.
- **Closest to green log:** a new sort in the account overview's boss list. It puts the bosses whose collection log page is closest to done first, and shows on each row the average time to get everything still missing, the same figure as the Time page and Ladlor's chart (hover for what is missing and when half and 9 in 10 players are done). Items several bosses drop (dragon pickaxe) count on every page that lists them once you have them from any boss. The time of a kill is the one the Time page uses: your own average, else the wiki's (marked with ~). A small Pets slider next to the sort leaves pets out (default) or counts them; it starts off again after a restart. Nothing else changes.
*Fixed*
- **Cut-off notes:** the notes on the Single drops page and the Time page are no longer wider than the panel, so their last words are not cut off.
*Easier to read*
- **Luck text contrast:** verdict words, luck percentages and pill text in the dry colours (Classic, Purple, Colour-blind and the others) are lifted a little toward white where needed so they stay readable on the dark panel. The luck colours of bars, dots and graphs are unchanged. The search box hint is lighter too.

*Behind the scenes*
- **Clearer "I got this at..." dialog:** when On Rate can't add a past drop because of the number you typed (too high, or a kill it saw itself), it says why and asks again with your entries kept, instead of closing the dialog. Refusals a different number can't fix still close it, with the reason.
- **Readable Custom luck colours:** text in a Custom luck colour is lightened just enough to stay readable (4.5:1 contrast) on the dark panel. The bars, rings and dots keep the exact colours you picked; the ready-made sets are unchanged.
- **One place for text widths:** the widths that wrap text in the panel, its tooltips and its dialogs now come from one list instead of numbers typed in many places. The panel looks the same; the two dialogs that were 220 and 260 wide (Add to History, collection log) are now the same width as the others.
- **Screen-reader names:** the boss search, the boss and sort drop-downs, the Time page's Show drop-down and the Expected / Received tabs now have names (the selected tab says so), and the raid points graph names its mode (points or purple chance). Nothing you see changes.
- **Faster after a kill:** your account is worked out in the background, so the panel stays responsive during long sessions. Nothing you see changes: every number is checked against the previous version. Nothing is worked out while the panel is closed, numbers from before a change (a click, a setting, another account) are never drawn, and the panel still follows the newest boss when several events arrive at once.
- **Safer saving:** your progress is always saved to the account it belongs to, even when you switch accounts mid-save. Damaged saved data no longer loses everything: the readable parts load and the damaged parts are left out.
- **Safer stopping:** game events that arrive while On Rate is being turned off no longer cause errors. Turning it off also puts RuneLite's tooltip timing back and leaves nothing of the panel behind.
- **Smoother during kills and raids:** working out the GP value no longer holds up the game right after a kill, a raid page works out its raid sections once instead of once per item, Yama's Oathplate figures are worked out once instead of at every redraw, and counting your team no longer does needless work every game tick.
- **Tidier raid History:** the "Average purple chance" heading now sits inside the same card as the part rows below it, instead of floating above it.

*Fixes*
- **Raid Data Tracker folder:** the notice, the setting's description and the docs said On Rate reads Raid Data Tracker's raids when you log in. It only reads them after you pick that plugin's folder, and the choice is not kept, so it has to be made again each time RuneLite starts. A line under the raid sections on the Chambers of Xeric and Tombs of Amascut pages now says whether the import is off, waiting for the folder, or reading the folder you chose this session.

*Fixed*
- **Drop messages setting:** its description no longer mentions a trip summary, which On Rate no longer sends.

*Fixed*
- **Recent tab heading:** the **LATEST UNIQUES** heading is now also shown when you have no kills or uniques in the last 7 days, instead of the list appearing without a title.

*Game update of 11 November 2026*
- **The Fractured Archive's new loot rates:** the team's purple chance is now fixed per raid size (1 in 16 for a solo or duo, up to 1 in 7.5 for eight players) and elemental fragments are 1/14. Raids On Rate saved before the update keep the chance they had, so your luck so far doesn't shift. **Fractured Archive team purple chance (%)** (formerly TFA team (%)) in the settings goes back to the new default (6.25%), also if you had changed it.

*Fixes*
- **Right-click hints match the menu:** a hover or tooltip only says "Right-click" when that row really has a right-click menu. Shared items with no place to move older copies to no longer promise one.
*Changed*
- **Item hovers say it both ways:** the line "You're drier than 67% of players" now ends with the verdict tile's wording ("1 in 3 as dry" or "1 in 6 as lucky"), so the compact table's chip percentage can't be mistaken for a luck score.
- **Boss search:** a search that matches no boss now says so under the search box and keeps the page you were on, instead of leaving it to be wiped by the next redraw. Escape clears the search, and the search box has a name for screen readers.

*Changed*
- **Collection log line says what is left:** it now reads "3 bosses you killed not loaded yet", "Collection log: 0 of N pages opened" or "Collection log: M pages not opened yet" instead of counting what is done, and the guide's progress line says "opened" too.

*Fixes*
- **Luck over time labels:** the left margin now fits the axis words (Spooned, On rate, Dry), so a line near the top no longer runs under them, and the verdict at the end of the line moves above or below it to stay clear of the line, with a dark backing if there is no clear spot.

*New*
- **Search by item:** the search box also finds bosses by the items they drop, from 3 letters: "fang" lists every boss that drops one, "tassets" finds General Graardor and Moons of Peril. Shared items count too. A word has to start an item's name ("ring" finds Archers ring, not Smouldering stone), boss names still come first, and the open list says which item a boss was found by.
- **Uniques forecast on plain boss pages:** a folded table under History giving the kills until 50% and 90% of players at your chances would have every unique you're missing, plus the pet. Only for plain bosses where the maths holds, not for Doom, the Colosseum, Vorkath, combined pages and the like. Raid forecasts now leave out uniques you left out of the count, as the boss pages already did. Details: [Boss page](docs/HOW-IT-WORKS.md#boss-page).
- **Luck over time window:** once you have more than 30 days of points, a **30 days | 90 days | All** row above the graph zooms in on the latest days. It is the same line, still your whole account's luck on each day (a note under the graph says so), not the luck of those days alone. The choice is not saved: it is All again after a restart.
- **Trips this session:** the Recent tab lists your last five finished trips (3 or more kills): boss, kills, uniques, when, how long and a verdict. A trip under half an expected unique says **Too early** instead of a verdict. The list is kept until RuneLite closes or you switch account; Today and Last 7 days are saved.
- **History: show more:** a link under History's rows shows the last 50, then all the rows On Rate kept, and **Show fewer** goes back to 10. It starts at 10 again on every boss. At bosses that list only your uniques, the rows are counted per unique, so a kill with two uniques no longer counts as one row.
- **Time list remembered:** the Time page's dropdown (Items, Ladlor chart, Pets, Raids) keeps your choice, also after RuneLite restarts. Saved pages from before still open.
- **Folds are remembered:** History, Personal bests and Points over time stay open (or folded) after RuneLite restarts, the way you left them. Purple chance by setup and Raids left for the log always start folded.
- **Feedback link opens GitHub:** the small **Feedback** link at the bottom of the panel now opens the GitHub issue chooser (bug, wrong drop rate or idea) instead of the Discord. The Discord is still linked in the README. The link only opens your browser; nothing is sent.
- **Items tab setting, clearer:** its description, tab tooltip and help now say that turning it off also hides pets, single drops and the Time page (how long the items you don't have take). The one-time "New in On Rate" message's **Show me** button no longer does nothing when the Items tab is off: it stays greyed out and says to turn the tab on under Display.
- **Trip card without the thin bar:** the bar under its sentence is gone. The verdict, the sentence and the rows say the same.
- **Clearer raid setting names:** the raid settings are named after the raid and mode, for example **Chambers of Xeric purple chance, Normal (%)**, **Theatre of Blood team size** and **Fractured Archive team size** (formerly CoX Normal (%), ToB team size and TFA team size). Your values are kept, and the panel's hints now point to these names. The two team sizes stay under Raids and the purple chances under Raid purple chances.
- **Long item names wrap:** names like "Tonalztics of ralos (uncharged)" break onto a second or third line in the Time, Items and account rows instead of ending in "..."; under the chance rings a long name keeps two lines. Hover a row for the full name.
- **Opens on Bosses:** the account overview always opens on its Bosses tab when RuneLite starts, instead of the tab you left it on. Your sort order and Items list are still remembered.
- **Higher-contrast bars:** a new setting (off by default) under Display that draws the bars with a lighter fill so they stand out more from their track. Only the shade changes; the numbers and text stay the same.
- **View button:** a small icon button on the ITEMS line of a boss page, and on the account overview's Sort row, switches between the five item views (bars, bars with tabs, compact table, chance rings, luck gauge) without opening the settings. It is the same choice as **Item view** in the settings, so every page follows it. On the bars view, the key "bar = got · line = expected" moves under the ITEMS line.
- **Raid pages: four new sections** under History, from the raids On Rate saw. Details: [Boss page](docs/HOW-IT-WORKS.md#boss-page).
  - The button only shows where there are rows it changes: not on the Time and Single drops pages, an empty list, or a boss page whose items are all shared.
- **Raid pages: three new sections** under History, from the raids On Rate saw. Details: [Boss page](docs/HOW-IT-WORKS.md#boss-page).
  - **Purple chance by setup:** your purple chance an hour (and the GP value an hour) per team size and mode, or team size and raid level at Tombs of Amascut. Scaled Chambers raids get their own rows, like **1+2** for one player in a raid scaled for three; raids from before this update say **Trio/scaled**. Your top 5 setups; the top row says **FASTEST PURPLES** or **MOST GP**.
  - **Personal bests:** your fastest raid and most points per mode and team size. The raid that set one says **PB** in History.
  - **Points over time:** your last 40 raids as a graph, all modes together, with the average of the last 10 and the raids that brought a purple.
- **Teams row at group bosses:** under History at Nex, Yama, the Hueycoatl, Corp and Zalcano, a **Teams** row splits your kills by team size (Solo, Duo, Trio...), with the kills from before On Rate counted teams as **Earlier**. Hover it to see what each size counts as and how many of your kills that adds up to: the share your Expected is worked out from. Only counts are shown, no names.
- **Show all on Pets and Recent:** the Pets page's two groups and the Recent tab's latest uniques say "top 5 of N" or "top 10 of N" when there are more, and a click on "show all" lists every one (and again to fold back).
- **Copy my luck:** a link at the bottom of the account overview copies your verdicts as text for Discord or Reddit.
- **Time page** on the Items tab: how long, on average, each item you don't have takes to get.
  - Lists: Items, Ladlor's chart, Pets, Raids.
  - Each row says where its time comes from: **yours**, **wiki** (marked **~**) or **mixed**.
  - Item hovers say how long one copy takes.
  - Details: [The Time page](docs/HOW-IT-WORKS.md#the-time-page).
  - **Wiki guide link:** hover a wiki-based time to see which OSRS Wiki guide the rate comes from, and right-click the row to open it in your browser. Nothing is fetched; the page opens only when you choose it.
- **Kill and raid times:** saved from the game's time messages, shown in History and as an average on the boss page.
  - **Hover the Time row** on a normal boss for the fastest timed kill, the median, the average, the latest and how many kills were timed. The fastest is among the kills in History, not a personal best. Raids point to **Personal bests**; Doom of Mokhaiotl gets a **Time · per level** row with each level's times. Details: [Boss page](docs/HOW-IT-WORKS.md#boss-page).
- **Raids from Raid Data Tracker:** if you use that plugin, On Rate reads your Chambers of Xeric and Tombs of Amascut raids from it: their times, points, team size and raid level (read only, can be turned off with **Import raids from Raid Data Tracker**). They show in the new raid sections straight away, and the points graph names the unique you got in them. Not on Leagues or other seasonal worlds, and only into the account they were read for.
- **Single drops** view on the Items tab: your luckiest single drops and longest waits, each judged by how long it took.
- **"I got this at kill...":** right-click an item you have to add the kill count you got it at.
- **New pages:** The Fractured Archive, Fortis Colosseum, Tempoross and Wintertodt.
- **Leave an item out:** right-click to leave it out of the calculations (a drop you got elsewhere).
- **Shared items per boss:** right-click a shared item to judge it on each boss separately.
- **Remove from History:** right-click a unique in History or Recent to remove that one entry.
- **Last unique** and **drop records** (fastest and longest gap) in the account overview and item hovers.
- **Share of a purple** in raid unique hovers ("5 of 15").
- **Chaos Elemental** has its own page again; it says "All shared" because its uniques are shared.
- **Shared items** show their picture straight away, and hovers say where else an item drops.
- **Clearer sync:** a boss page says "opened but not readable" when a collection log page can't be read.
- **Loot Tracker note:** the panel says so when RuneLite's Loot Tracker is off, because new drops at most bosses then aren't recorded.
- **What's new, once:** if you had On Rate before, the panel shows a short *New in On Rate* card about the Time page and the raid sections, one time only. New players don't get it.
- **Before login:** the panel says *Log in to see your account*, *Loading* or *Couldn't load* instead of showing a new player's setup steps.

*Changed*
- **Trip card and Time page use the boss's own word:** "Last raid just now", "Last chest just now" and the New trip hover follow the boss (kill, raid, chest, delve, run or roll) instead of always saying kill, and the Time page says chest for Barrows and the Moons of Peril. Tempoross and Wintertodt stay at kill there, as their times are per kill.
- **Orange only for selection:** the PB and FASTEST PURPLES / MOST GP tags, the graph key and average line, the "New in On Rate" notice border and the overlay title are no longer orange, so orange is kept for selected tabs, focus and hover and is not mistaken for the dry end of the luck colours. The GP bars in Purple chance by setup are slate like other progress bars.
- **Recent tab:** a coloured unique count also gets a small triangle, up when clearly more than expected and down when clearly fewer, so the colour is not the only sign.
- **Verdict words:** *Extremely dry* and *Extremely spooned* at the far ends, and the middle is split into *A bit dry*, *On rate* and *A bit spooned*.
- **"Too early"** replaces a verdict when less than half a unique is expected on a boss page, and when less than 0.05 of a unique is expected on the trip card and the overlay. A trip with a unique this early reads "Too early" too, not "Very spooned".
- **Verdict tiles** are rounded cards, and the trip card is rounded too.
- **Items tab** has four pages: Items, Pets, Single drops and Time. Items shows the top 5 driest and luckiest, with "show all".
- **Back button** on the account overview returns to the boss you were on, and the account remembers the page you left it on.
- **Shorter welcome guide:** intro, three steps, collection log progress, and the reference part folded.
- **Easier tooltips:** a plain sentence first, a bar of players with fewer, as many and more, and they stay up longer. Item pictures are bigger.
- **Trip card** opens with the trip's verdict and a sentence on how common it is.
- **In-game overlay** (off by default) leads with this trip and is not shown at raids, the Inferno or the Fight Caves.
- **Bosses tab** sorts by most expected, driest, luckiest, longest dry streak or **Luckiest by GP value**.
- **Group bosses** (Nex, Yama, the Hueycoatl, Corp) count only the players who hit the boss with you.
- **Plainer wording** in settings, hovers and chat; *GE value* is now *GP value*; dry milestone messages start at 2x.
- **Keyboard and screen readers:** the tabs, the view button and the footer links can be reached with Tab and used with Enter or Space.
- **Help without hovering:** the ? and About now open as a text block when you click them (or press Enter or Space on them) and close again the same way, with Escape, or when you go to another page. Hovering still works. The welcome guide says "Click the ? for help."
- **Luck graph** stays flat on days you didn't play.
- **README and docs** reorganised, with a short guide for contributors.
- **Reset this boss** says what is gone for good (History, raid points, streaks, drops added by hand...) and what the collection log brings back, and Cancel is the default.
- **Hide guide** keeps you in the On Rate panel instead of opening the settings.
- **Clearer wording:** the Time page says the wiki's kill speed, Tombs of Amascut raids with the game's exact points are no longer called estimated, and boss pages say chests, delves or raids where they used to say kills.
- **Settings:** Raid purple chances is folded by default, and the Raid Data Tracker import no longer lists Theatre of Blood (it never read it).

*Your numbers change*
- **Raids from Raid Data Tracker** use their own purple chance, from their points, instead of the average from the settings. If you use that plugin, your Chambers of Xeric expected purples and verdicts change, and the line "N of M raids use the purple chance from your settings" goes down. Raids On Rate saw itself keep their own numbers. Tombs of Amascut raids from it only show in the raid sections, so Tombs numbers don't change.
- **GP value of untradeable uniques** now counts what they make (halberd pieces, araxyte fang, Elder venator fang, Mokhaiotl cloth, vestiges and more). List: [docs/RATES.md](docs/RATES.md#gp-value-of-untradeable-uniques).
- **Pets** you can only have once no longer count as if you could get several. This changes pet-heavy pages and the account total.
- **Abyssal Sire:** Unsired is no longer counted as a unique of its own.
- **Alchemical Hydra:** dragon thrownaxes and knives are no longer counted.
- **Zalcano:** the crystal tool seed counts the players around you.
- **Group bosses:** older kills use the team of the first kill On Rate counted, not solo.
- **Rates and caps:**
  - Lil' Zik in Hard Mode is 1/500 (was 1/650).
  - Vorkath's head is guaranteed only on the 50th kill while you have none.
  - The dragon axe is judged on the Dagannoth Kings and Wintertodt together.
  - GP value never expects more than one copy of an item you can only have once.

*Fixed*
- **Recent made up dates:** opening a collection log page no longer adds a dated "drop" to History, Recent and Today.
- **Unreadable saved data** is kept as a backup instead of being overwritten.
- **Trip value** at Doom counts the trip's own delve levels.
- **Raid trips** no longer end after 30 minutes without a kill (raids, Inferno and Fight Caves get 2 hours).
- **Last unique** finds uniques saved under another name or case.
- **Pets list** includes the Chaos elemental pet.

**Update of 21 October 2026**

*Your numbers change*
- **A fairer verdict:** you're only dry when fewer than 35% of players at your kill count have this many or fewer, and only spooned when fewer than 35% have this many or more.
  - None of an item at 0.4 to 1 expected is now "On rate", not "Dry".
  - Small counts change most; big counts barely change.
- **Vorkath's head:** the 50th kill always drops one, and On Rate counts that.
- **Exact Tombs of Amascut points** from the game replace On Rate's estimate.

*New*
- **Two verdicts** on every boss page and the account overview: **Uniques** and **GP value**.
- **Recent** starts with **Today** and **Last 7 days**: kills and uniques in total and per boss.
- **Sort** the bosses by **Longest dry streak**.
- **Trip card** shows what the uniques should be worth per hour.
- **Tumeken's guardian** (the Tombs of Amascut pet) is tracked.
- **"2.1× the drop rate without one"** in hovers: how far past the drop rate you are.
- **Clearer words** than "luckier than X%": "74% have none", "1 in 3 as dry", "about average".
- Pets you don't have show your chance of one by now.
- Doom of Mokhaiotl counts in delves; Barrows and Moons in chests.
- Pages with two bosses say which boss dropped an item and at which kill.
- History keeps the last 500 kills per boss (was 200).

*A cleaner look*
- Colours have one job each: green to red is luck, orange is for headers, tabs and links, purple is raid loot.
- Lists show the name, a coloured verdict and a dry-to-spooned scale with a marker.
- Bigger verdicts, item pictures and list text, and an **ITEMS** header that explains the white line.
- Lighter grey hints with an (i) mark, and the same hover layout everywhere.
- No more asterisk on raids and team bosses: "est." marks an estimate.
- Boss page notes are behind one "About these numbers" line.

*Fixed and faster*
- The panel could stop responding to clicks until you opened another plugin.
- The trip card's **Expected** was off on the first raid of a trip.
- Saved raids are stored apart and only written when a raid ends, so many raids no longer slow kills down.
- The overlay updates when raid points arrive after the kill message.

**Update of 19 October 2026**

*Easier for new players*
- A boss that isn't synced says **Not synced**, not "Very dry", and doesn't count in your account until loaded.
- Shorter welcome guide: three steps.
- The collection log line lists the bosses you killed first.
- Royal Titans and Yama pages say when kills count as duo, and where to change it.
- Raid purple chance settings have their own section.
- Clearer notes for shared items, and *Reset this boss* is a small link.

*New*
- **History** on every boss page, folded until you click it.
- The account overview has three tabs: **Bosses**, **Items** and **Recent**.
- **Yama** has a solo/duo setting like the Royal Titans.
- Royal Titans prayer scrolls each have their own row (1/150).
- **Feedback:** an [On Rate Discord](https://discord.gg/gh9fx6T9zn).

*Changed*
- The **trip card** is on by default.
- Shared items only show on the page of each boss that drops them.
- Bosses with 0 kills no longer show in the account overview.
- The **in-game overlay** is wider.
- Tormented Demons and demonic gorillas are no longer tracked (collection log Other tab).
- **Faster:** only what changed is saved after a kill, and GE value luck is worked out about 15 times faster.

**First release, 18 October 2026** (1.0.0)
- Expected vs received uniques for every boss and raid, with a verdict and an account overview.
- GE value luck, raid points, collection log sync, five luck views and optional extras.
- Shared items are judged on all their bosses' kills together.
- God Wars bodyguards count towards their general's uniques.
- Nex, Yama, the Hueycoatl and Corp count your team size at each kill.
- Maggot King.
- Rates checked against the wiki ([docs/RATES.md](docs/RATES.md)).
