# How On Rate works

The details behind the [README](../README.md): every part of the panel, every setting, and the assumptions On Rate makes. Where each drop rate comes from is in [RATES.md](RATES.md).

## Reading the panel

Pick a boss from the dropdown, or let the panel follow your kills. To find a boss quickly, type in the search box above the dropdown: part of the name ("vork", "kril") or the usual short name ("cox", "tob", "toa", "dk"). Enter opens the first match. The **Account** button next to it always takes you back to the account overview. The **?** at the top right explains what you're looking at.

### Boss page

- At the top: the boss and your kill count, then two **verdict tiles** side by side. **Uniques** counts how many you got against how many you should have by now; **GP value** weighs them by their GE price, so a twisted bow counts for more than a prayer scroll. Each says Very dry, Dry, A bit dry, On rate, A bit spooned, Spooned or Very spooned, with the numbers and how common your result is among players at the same kill count ("1 in 3 as dry" means about 1 in 3 players has this many or fewer; "74% have none" when you have none yet; "about average" when it says On rate). Hover a tile for it as a percentage ("drier than 67%": 67% of players have more than you; "luckier than 83%": 83% have fewer; far-out results add how rare they are, like "1 in 1,205") and for the full split: how many players have fewer, as many and more than you.
- **Under "On rate":** one sentence about the item furthest from average, when one is far enough out to mention: the driest (more than 8 in 10 players have more), one you don't have that 6 in 10 players would have by now, or the luckiest (more than 8 in 10 have fewer). Left-out items don't count. Nothing shows when nothing stands out.
- **Last unique:** the account overview says how long ago your last unique was, from the drops On Rate saw itself (History). Item hovers show the fastest and longest it took between drops since you installed On Rate.
- **How the verdict is decided:** Dry when fewer than 35% of players at your kill count have this many or fewer (Very dry: under 10%); Spooned when fewer than 35% have this many or more (Very spooned: under 10%); otherwise it's in the middle, split in three by where you stand among players at your kill count (counted from the driest, with players who have exactly as many as you put as close to the middle as they allow): A bit dry from 35% to 45%, On rate from 45% to 55%, A bit spooned from 55% to 65%. So the most common result is never called dry: with no drops at 0.43 expected, 65% of players have none either, and that's on rate. The GP tile needs RuneLite's price list; untradeables (pets, jars, Vorkath's head) count as 0 GP.
- Then one row per item, with **Any unique** first. The bar's fill is how many you got; the **white line** is how many you should have by now. Behind the line is dry, past it is spooned.
- **Hover an item** for its drop rate, your chance of having it by now, how many players have fewer, as many or more than you, for something you don't have yet how far past the drop rate you are ("2.1× the drop rate without one": players call 1× on rate, 2× dry and 3× real pain), how many kills give a 50% or 90% chance, and your **dry streak**: kills since your last copy and how many times the drop rate that is. If your last copy dropped before you installed On Rate, it says "at least".
- At the bottom: **▸ History**, folded away until you click it. It lists the kills On Rate saw on this boss, newest first (the last 500 per boss are kept, saved in RuneLite's own settings like everything else):
  - **Most bosses:** one line per unique: the kill count, the item and how many kills it took, coloured by how that compares with the drop rate ("33 kills (0.7x)"). "your first one" means it was your first ever; "8+ kills" means your previous copy dropped before On Rate, so only a minimum is known. Under it: how many uniques you got in those kills against how many you should have.
  - **Raids:** every raid, with its mode, your points and that raid's purple chance (or the team size and deaths for Theatre of Blood), and the purples in it. Under it: your average purple chance over the raids with points.
  - **Nex, Yama, the Hueycoatl, Corp and Zalcano:** every kill with the team size On Rate counted and your share.
  - **Royal Titans:** every kill with the titan you looted. **Doom of Mokhaiotl:** every delve with its level.

### Account overview

What the panel opens on. It adds up every boss you have kills at. A boss with 0 kills is left out, even if some of its items are in your collection log from somewhere else (dragon thrownaxes, say):

- The two **verdict tiles** at the top are your whole account: **Uniques** (how many) and **GP value** (what they're worth). An account that's dry on the number of drops but hit two twisted bows shows up as spooned on GP value. Hover a tile for the details. Prices come from RuneLite's own price list, so On Rate makes no web requests; untradeable items like pets and jars count as 0 GP. Turn off *GE value* in the settings to keep only the Uniques tile. The tiles stay at the top on every tab.
- Three tabs: **Bosses**, **Items** and **Recent**.
- **Bosses** (the default tab): one row per boss with its verdict. Use **Sort** to put the most expected, driest, luckiest, most killed, longest dry streak or alphabetical first. **Longest dry streak** shows, instead of the verdict, how long since each boss's last unique in times its unique rate ("2.6×"); a "+" means the last one came before On Rate, so it's at least that. Click a boss to open it, or hover it to see its driest item. Bosses with almost nothing expected yet (under 0.05 uniques) aren't listed, because they say nothing about luck; they still count in your account total.
- **Items:** your 10 **driest** or 10 **luckiest** items across every boss, or your **pets**: the 5 luckiest pets you have (got with the fewest kills expected) and the 5 you'd most likely have by now but don't. Driest only lists items with at least 1 expected, so rare items you've barely farmed don't fill it up. Click one to open its boss.
- **Recent:** first **Today** and **Last 7 days**: your kills and uniques, in total and per boss. The number of uniques is green when it's more than you should have had from those kills, orange when it's fewer; hover a line for the expected number. On Rate keeps these counts for two weeks, so they are always complete, however many kills you do. Below that, your 10 latest uniques across every boss: the item, the boss and kill count, when ("today", "3 days ago") and how many kills it took. Click one to open its boss.
- One grey line under the bosses says how many bosses aren't listed (hover it to see which). **Shared items** (dragon pickaxe, godsword shards, Virtus, onyx...) have no row here: you find them on the page of each boss that drops them. They do count in the account's **verdict** and **GE value** at the top, once On Rate knows the kill count of every boss that drops them.

### Other views

Prefer something else than the bars? Click **⇄** next to **ITEMS** (or **BOSSES** on the account overview) for the next view; the one you pick is kept:

<img src="luck-views.png" alt="The other luck views">

- **Target bars** (default): described above.
- **Compact table:** one line per item: got / expected and a luck chip: the share of players with less than you (tie-aware), in its luck colour. Hover a line for it in words ("drier than 67%").
- **Bars with tabs (Delve Calculator):** the classic Expected and Received tabs. On *Expected* the number is how many you should have and the bar your progress to the next one ("Any · 41.8% · 4" means 4.418 uniques by now); on *Received* the number is how many you got and the bar how lucky that is.
- **Chance rings:** each ring fills up as your chance of having the item grows. Once you have it, the ring shows your chance of the next one by now.
- **Luck gauge:** a speedometer from dry to spooned, with every item (or boss) as a dot on the same scale.

### Extras

The trip card is on by default; the overlay is off. Change them in the settings under **Display**. Luck over time opens and closes from the panel itself.

- **Luck over time:** your account's luck over time, one point per day you play, in the account overview. Click its title to open or close it; it stays the way you left it. On Rate saves a point every day even while it's closed, so it has data when you open it.
- **This trip card:** kills, uniques and expected for your current trip on the boss page, plus kills per hour, the drops you got and what the uniques should be worth per hour at that speed ("Unique value: ~1.2m/h expected"). That value only counts uniques, not normal loot like bones or hides. A trip ends when you kill another boss, after 30 minutes without a kill (2 hours for raids, the Inferno and the Fight Caves, where one run can take longer), or with the *Start a new trip* button.
- **In-game overlay:** a small box on the game screen while you kill a boss: verdict, uniques, your driest item and the trip. During a long trip (30 kills or 45 minutes at one boss) the trip card offers it once with a one-line note; click the note to turn the overlay on.
- **Colour-blind colours:** orange to blue instead of red to green.

## Supported bosses

On Rate covers the **Bosses** and **Raids** tabs of the collection log:

Abyssal Sire, Alchemical Hydra, Amoxliatl, Araxxor, Barrows, Brutus, Bryophyta, Callisto and Artio, Cerberus, Chambers of Xeric, Chaos Fanatic, Commander Zilyana, Corporeal Beast, Crazy Archaeologist, Dagannoth Kings, Deranged Archaeologist, Doom of Mokhaiotl, Duke Sucellus, Fortis Colosseum, General Graardor, Giant Mole, Grotesque Guardians, Hespori, Hueycoatl, K'ril Tsutsaroth, Kalphite Queen, King Black Dragon, Kraken, Kree'arra, Maggot King, Moons of Peril, Nex, Obor, Phantom Muspah, Phosani's Nightmare, Royal Titans, Sarachnis, Scorpia, Scurrius, Shellbane Gryphon, Skotizo, Tempoross, The Gauntlet, The Leviathan, The Mad Angel, The Whisperer, Theatre of Blood, Thermonuclear Smoke Devil, Tombs of Amascut, TzKal-Zuk, TzTok-Jad, Vardorvis, Venenatis and Spindel, Vet'ion and Calvar'ion, Vorkath, Wintertodt, Yama, Zalcano and Zulrah.

Bosses that share a collection log page are shown together, just like in the log: **Dagannoth Kings**, **Callisto and Artio**, **Vet'ion and Calvar'ion**, **Venenatis and Spindel**, and **The Gauntlet** (normal and Corrupted). Each boss on the page keeps its own kill count and drop rates.

**Chaos Elemental** has no page of its own: its only uniques are the dragon pickaxe, the dragon 2h sword and a pet it shares with Chaos Fanatic. Its kill count still counts for the shared dragon pickaxe and pet (see [Good to know](#good-to-know)).

**God Wars Dungeon** generals assume you kill all three bodyguards every kill. The bodyguards roll some of the general's uniques (1/16,256 for each Bandos piece, 1/16,129 for each Armadyl piece, 3/16,129 for the Saradomin sword and Zamorakian spear, 1/1,524 for each godsword shard), and the collection log counts those under the general. Bodyguard loot counts for the general but not as a kill.

**Maggot King**: the pet can also come from taking eggs, which On Rate can't count, so its pet row is a low estimate.

**Barrows** assumes you kill all six brothers before every chest, and **Moons of Peril** assumes you defeat all three moons before opening the Lunar Chest.

## Settings

Open the settings with the cog next to **On Rate** in the Configuration panel.

- **Follow last kill:** switch the panel to the boss you just killed. On by default.
- **Show welcome guide:** show the getting-started steps at the top of the panel. *Got it* turns this off; turn it back on here.
- **Sync reminders:** the chat reminder after killing a boss that isn't synced yet. On by default.
- **Drop messages:** a chat line when you get a unique, saying how many kills it took. On by default.
- **Dry milestone messages:** a chat line when you pass 1x, 2x, 3x, 5x or 10x the drop rate dry on an item. On by default.
- **Items tab:** the Items tab on the account overview (your 10 driest and luckiest items). On by default.
- **GE value**, **This trip card**, **In-game overlay** and **Colour-blind colours**: see [Extras](#extras). GE value and This trip card are on by default.
- **Royal Titans:** kills count as **duo** kills unless you change it to **Solo** or **Some of each**. With *Some of each*, also enter how many of your kills were solo; the rest count as duo. In a duo you get about half the uniques of a solo kill, while the pet chance stays the same.
- **Yama:** how you killed Yama before On Rate could count your team: **duo** by default, or **Solo** or **Some of each** (with your number of solo kills). Kills from then on use the counted team size.
- **Count your team:** at Nex, Yama, the Hueycoatl, the Corporeal Beast and Zalcano, On Rate counts the players within 15 tiles of you at each kill and gives you that share of the kill: a quarter with four players, a half in a duo. The boss's page says how many kills it saw and the average team size. Kills from before On Rate counted use the team of the first kill it counted (Yama uses its own setting), and the pet chance doesn't scale (except at Corp, where only the top damager gets loot). At Zalcano only the crystal tool seed is shared; shards and the pet are rolled for each player. On by default.
- **Use raid points:** at the end of each raid, On Rate saves your points with the completion and works out that raid's purple chance from them. On by default. How it gets the points:
  - **Chambers of Xeric:** read straight from the game when "Congratulations - your raid is complete!" appears: your points, the team's points and the team size.
  - **Tombs of Amascut:** at the end of each raid the game sends your exact points, and On Rate uses those (the same number the Tombs of Amascut plugin reads). Only if they don't come does it fall back to its own estimate, the way [Raid Data Tracker](https://github.com/Raitab/raid-data-tracker) and the Tombs of Amascut plugin do: your own hits times a points factor per monster, rooms capped at 20,000 (Crondis 10,000), 64,000 in total, Wardens phase 2 stops counting once it has been downed more than three times, fixed points for puzzle rooms, 300 per player for MVP items, and minus 20% (at least 1,000) per death. The raid level is read from the game. In a team On Rate only knows your own points; if you have the **Tombs of Amascut** plugin installed, its party total is used instead. Without the exact points On Rate needs to see the raid start, so a raid you were already in when you turned it on uses the setting.
  - **Theatre of Blood:** there are no points to read. On Rate saves the team size, your deaths and whether you were MVP from the board at the end, and splits the team purple chance from the settings over that raid's real team size.

  The chance is worked out with the OSRS Wiki's formulas. **CoX:** every 8,676 team points is 1% for a unique, one roll is capped at 570,000 points (65.7%) and more points roll for another unique; you get a unique with your share of the points. **ToA:** 1% per 10,500 - 20 x raid level team points (the raid level counts fully up to 310, a third from 310 to 430 and a sixth above that), capped at 55%; at low raid levels some uniques are rare: below raid level 50 for Osmumten's fang and Lightbearer, below 150 for the shadow, ward and Masori. When the chest picks one of those, you only get it 1 time in 50; otherwise you get normal loot instead.

  Raids done before this update and raids synced from the collection log use the percentages below (ToB with the ToB team size setting). The raid's page says how many raids used your points. Over time, more and more of your raids use their own points.
- **ToB team size:** how many players you usually do Theatre of Blood with (4 by default). Your share of the team's purples is 1 / team size. Raids where On Rate read the end-of-raid board use their real team size.
- **Raid purple chances** (folded away; most players can leave these alone): your chance of a unique per raid depends on your points, team and mode, so every mode has its own setting. The panel counts each mode's completions separately.

  | Raid | Mode | Default | How to work it out |
  |---|---|---|---|
  | Chambers of Xeric | Normal | 3.5% | Every 8,676 of your personal points is 1% (30,000 points is about 3.5%) |
  | Chambers of Xeric | Challenge Mode | 6.6% | Same formula; a Challenge Mode raid is usually 6.3 to 6.9%. It uses its own unique table (prayer scrolls are rarer) |
  | Theatre of Blood | Normal | 11% for the team | A team gets a purple about 1 in 9.1 raids. On Rate splits it over the **ToB team size** setting (4 by default), or over the real team size when it read the end-of-raid board |
  | Theatre of Blood | Hard Mode | 13% for the team | About 1 in 7.7 raids, split the same way |
  | Theatre of Blood | Entry Mode | 0% | Entry Mode has no uniques |
  | Tombs of Amascut | Entry Mode | 0.75% | At low raid levels most rolled uniques turn into other loot |
  | Tombs of Amascut | Normal | 2.5% | Depends on raid level and points; the OSRS Wiki has a calculator |
  | Tombs of Amascut | Expert Mode | 5% | Same as above |

## Good to know

- **Rates are solo / full-contribution base rates**, without diary, Combat Achievement or Slayer task boosts. At Nex, Yama, the Hueycoatl and Corp, On Rate counts your team from the players around you (see [Settings](#settings)); it assumes everyone does the same damage, so a carry or a leech is off. Royal Titans counts as duo kills by default and has its own solo/duo setting. Where every rate comes from, and when it was last checked against the wiki, is in [RATES.md](RATES.md).
- **Leave an item out:** right-click an item on a boss page and choose *Leave out of the calculations*, for a drop you got somewhere else (an Araxyte head from araxytes, an Occult necklace from a regular smoke devil) or items you don't care about (jars, crystal weapon seeds). It no longer counts for that boss's verdicts, the account overview, the Items tab or dry messages, and moves to a greyed *Left out* list at the bottom of the page. Right-click it there to count it again. Items that also drop elsewhere say so when you hover them.
- **Items several bosses drop are counted together**: the dragon pickaxe, godsword shards, Virtus, chromium ingots, draconic visage, dragon axe (Dagannoth Kings and Wintertodt), uncut onyx, dragon chainbody and the Chaos Elemental pet. The collection log has one total for all their bosses, so On Rate can't tell which boss each copy came from. Instead of guessing, every boss that drops one shows it under **Shared with other bosses**, judged on all those bosses' kills together against the one log total: the same numbers on each of those pages. Bosses whose kills On Rate doesn't know yet count as 0 until you open their log page, and the page says which. The **dragon 2h sword** is left out completely, because it also drops from monsters outside the bosses.
- **Gauntlet loot** goes to a chest the Loot Tracker doesn't see. On Rate notices new items through the collection log message; open The Gauntlet's collection log page after a session to update repeat seeds.
- **Pets** are counted from the "funny feeling" message right after a kill. Like the collection log, On Rate counts a pet once: for luck, what matters is whether you have it by now, not how many duplicates you might have rolled. Royal Titans prayer scrolls work the same way, because they stop dropping once read. You loot one titan per kill and each scroll only drops from its own titan (1/75), so On Rate assumes you loot both equally: 1/150 per scroll per kill, 1/300 in a duo.
- **Not covered yet:** the team version of The Nightmare. Fortis Colosseum, Tempoross and Wintertodt are covered (see [RATES.md](RATES.md) for how their runs and reward rolls are counted). Slayer monsters like Tormented Demons and demonic gorillas are in the log's Other tab, so On Rate leaves them out.
- **Chest bosses:** Obor's and Bryophyta's uniques come from their chests, which the Loot Tracker doesn't see. New items show up through the collection log message; open their collection log pages to update repeat drops.
- **No files:** On Rate only uses the rates built into the plugin and saves your progress in RuneLite's own settings, so it doesn't read or write any files on your computer.
- **Made a mistake?** The small **Reset this boss…** link at the bottom of a boss page clears that boss (it asks first). Open its collection log page again to re-sync.

## Running from source

Open the project in IntelliJ IDEA (Java 11 or newer) and run **Tasks → application → run** in the Gradle tool window. To log in with a Jagex account in that window, start RuneLite once through the Jagex Launcher with `--insecure-write-credentials` set in *RuneLite (configure) → Client arguments*. `gradlew test` runs the tests.
