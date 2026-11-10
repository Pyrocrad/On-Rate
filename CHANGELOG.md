# Changelog

Updates come through the Plugin Hub. Each heading is the date of that update; **Next update** is not out yet. How everything works is in [docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md).

**Next update**

*Behind the scenes*
- **Faster after a kill:** your account is worked out in the background, so the panel stays responsive during long sessions. Nothing you see changes: every number is checked against the previous version. Nothing is worked out while the panel is closed, numbers from before a change (a click, a setting, another account) are never drawn, and the panel still follows the newest boss when several events arrive at once.
- **Safer saving:** your progress is always saved to the account it belongs to, even when you switch accounts mid-save. Damaged saved data no longer loses everything: the readable parts load and the damaged parts are left out.
- **Safer stopping:** game events that arrive while On Rate is being turned off no longer cause errors.

*New*
- **Raid pages: three new sections** under History, from the raids On Rate saw. Details: [Boss page](docs/HOW-IT-WORKS.md#boss-page).
  - **Purple chance by setup:** your purple chance an hour (and the GP value an hour) per team size and mode, or team size and raid level at Tombs of Amascut. Scaled Chambers raids get their own rows, like **1+2** for one player in a raid scaled for three; raids from before this update say **Trio/scaled**. Your top 5 setups; the top row says **FASTEST PURPLES** or **MOST GP**.
  - **Personal bests:** your fastest raid and most points per mode and team size. The raid that set one says **PB** in History.
  - **Points over time:** your last 40 raids as a graph, all modes together, with the average of the last 10 and the raids that brought a purple.
- **Copy my luck:** a link at the bottom of the account overview copies your verdicts as text for Discord or Reddit.
- **Time page** on the Items tab: how long, on average, each item you don't have takes to get.
  - Lists: Items, Ladlor chart, Pets, Raids.
  - Each row says where its time comes from: **yours**, **wiki** (marked **~**) or **mixed**.
  - Item hovers say how long one copy takes.
  - Details: [The Time page](docs/HOW-IT-WORKS.md#the-time-page).
- **Kill and raid times:** saved from the game's time messages, shown in History and as an average on the boss page.
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
- **Verdict words:** *Extremely dry* and *Extremely spooned* at the far ends, and the middle is split into *A bit dry*, *On rate* and *A bit spooned*.
- **"Too early"** replaces a verdict when less than half a unique is expected.
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
- **Keyboard and screen readers:** tabs, links and switches can be reached with Tab and used with Enter or Space.
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
