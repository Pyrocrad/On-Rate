# Expected Drops (RuneLite plugin)

A side panel that shows, for every boss, how many of each unique you *should* have by now versus how many you actually got — the same idea as the Delve Calculator plugin for Doom of Mokhaiotl, for 53 bosses (Doom included) and the three raids.

- **Expected tab** (same layout as the Delve Calculator): the number on the right is how many of that item you *should* have by now; the bar is progress towards the next one.
- **Received tab:** the number is how many you actually got; the bar shows how lucky that is compared with other players at the same kill count (50% = exactly average, red = dry, green = lucky).
- Hover a row for the rate, your chance of having at least one by now, and the kill count needed for a 50% and 90% chance.

## Running it locally

You need:

- **Java 11 or newer** (a JDK, for example [Eclipse Temurin 17](https://adoptium.net/)).
- **IntelliJ IDEA Community** (free) — the easiest route.

### Option A: IntelliJ (recommended)

1. Unzip this folder somewhere, then in IntelliJ choose **File → Open** and select the `expected-drops` folder. Let it import the Gradle project (the first time it downloads RuneLite, which takes a few minutes).
2. Open `src/test/java/com/expecteddrops/ExpectedDropsPluginTest.java`.
3. Click the green arrow next to `main` → **Modify Run Configuration**:
   - VM options: `-ea`
   - Program arguments: `--developer-mode`
4. Run it. A RuneLite window opens with the plugin already loaded. The bar-chart icon in the sidebar opens the panel.

### Option B: command line

From inside the folder:

```
gradlew run        (Windows)
./gradlew run      (Mac/Linux)
```

### Logging in with a Jagex account

A client started from IntelliJ or Gradle can't use the Jagex Launcher login on its own. To set it up once:

1. Open the **RuneLite (configure)** app, which is installed alongside RuneLite.
2. In **Client arguments**, add `--insecure-write-credentials` and save.
3. Start RuneLite normally through the Jagex Launcher once. This writes your login to `.runelite/credentials.properties`.
4. From then on, the client you start from IntelliJ logs in automatically.

Delete `credentials.properties` if you ever want to undo this, and never share that file.

## Getting your history in

The plugin needs **Loot Tracker** enabled (it's on by default).

1. **Kill count** updates from the kill count chat message after every kill.
2. **Past drops:** open your collection log and click each boss page once. The plugin reads the kill count and how many of each item you have, and the panel shows "Collection log synced".
3. **New drops** after that are added automatically from the loot tracker. Pets are counted from the "funny feeling" message.

Progress is saved per account in your RuneLite profile.

### Doom of Mokhaiotl

Doom's rates depend on the delve level, so the plugin counts completions per level:

- Each "Delve level: N ... duration" chat message adds one completion at that level (8+ counts each deep delve wave).
- **Open the Doom scoreboard once** to load your full history; it lists completions for every level and replaces the counted numbers.
- Uniques come from the loot tracker when you claim loot, Dom from the pet message, and your history from the Doom collection log page.

The per-level rates and the scoreboard layout come from Speaax's [Delve Calculator](https://github.com/Speaax/Delve-calculator) plugin (BSD 2-Clause). With the same level counts the two plugins show the same expected numbers.

### Exceptions to collection log syncing

Items that drop from several bosses can't be credited to one boss from the collection log, because the log shows one total per item. These are only counted from loot seen while the plugin runs:

- Dragon pickaxe, godsword shards, virtus, chromium ingots, draconic visage, uncut onyx, dragon axe and dragon chainbody.
- Every item from the split wilderness bosses (Callisto and Artio, and so on).

## Settings

- **Follow last kill:** switches the panel to whatever you just killed.
- **Raid purple chances:** the unique chance per raid depends on your points and team, so set your own:
  - **CoX:** every 8,676 personal points is 1%.
  - **ToB:** about 11% for the team, divided by your team size.
  - **ToA:** depends on raid level and points; the wiki has a calculator.

## Changing the rates

The rates are in `src/main/resources/com/expecteddrops/bosses.json`. They are per kill, solo or full contribution, with no diary or combat achievement boosts.

To override them without rebuilding:

1. Copy that file to `.runelite/expected-drops/bosses.json` in your user folder.
2. Edit the copy.
3. Restart the plugin.

The format is explained at the top of the file.

## Known limitations

- **Team bosses** (Nex, Hueycoatl, Royal Titans, Yama in a duo, Corporeal Beast with others) use full-contribution rates. Your real chance is lower in a team.
- **Gauntlet** loot goes to a chest the loot tracker doesn't see. Only first-time drops and pets are picked up there; repeat seeds aren't counted.
- **Not covered:**
  - Colosseum: rates depend on the wave.
  - Team Nightmare.
- **Royal Titans:** if the kill count looks off, check it against the collection log page.

## Tests

`gradlew test` runs the probability maths and the tracking logic. This includes a check that every boss in the data has a valid rate.
