# On Rate: original UI on the V2 engine

Branch `integration/original-ui-v2-engine`, made from `main` (the original, commit `93b8c56`). `main` and `rebuild/on-rate-v2` are not modified.

Goal: the original plugin, looking and behaving exactly as before, running on the V2 technical foundation.

## Stage 1: what was found

**Original architecture.** One plugin class (`ExpectedDropsPlugin`, 1,266 lines: lifecycle, event wiring, prices, item ids, collection log reading, team detection, reward-roll counters, settings adapter), a 6,212-line panel (`ExpectedDropsPanel`) plus 20 small UI classes, and the engine (`data`, `stats`, `tracking`, `raids`).

**The seam between them is clean.** The original UI only touches the engine through public types (`Tracker`, `RowStats`, `BossStats`, `AccountStats`, `KillEntry`, `ItemTime`...), eight static `Tracker` members and 16 `DropMath` functions. V2 kept every one of these types with the same methods, and maps each `DropMath` function to a bit-identical replacement. So the original UI can run on the V2 engine with import changes only.

**V2 parts that depend on the V2 UI** (`ui`, `view.Phrases`, `service.Session/BossView/Snapshot`, `OnRateConfig`, `SettingsMigration`) cannot transfer without changing what the player sees or which settings exist.

## Stage 2: plan, component by component

| Component | Kept from original | Taken from V2 | Adaptation | Risk |
|---|---|---|---|---|
| Panel and all UI classes (`ExpectedDropsPanel`, `Texts`, `Theme`, `Palette`, `Tip`, `VerdictTile`, `LuckGraph`, `LuckGauge`, `LuckRing`, `LuckStrip`, `DropBar`, `TargetBar`, `EffortBar`, `Chip`, `ShareText`...) | **everything**: layout, tabs, five item views, colours, tooltips, text | nothing | imports point at `com.onrate.engine`; `DropMath.x` becomes the identical `Poisson/Luck/Verdict/ValueLuck/Rates/Untradeables` call | low: mechanical, checked by pixel comparison |
| Settings (`ExpectedDropsConfig`, `DuoMode`) | **everything**, same names, descriptions, sections, `itemView` with its 5 views | nothing | none | none |
| Math (`DropMath`) | API names via the mapping | `Poisson`, `Luck`, `Verdict`, `ValueLuck`, `Rates`, `Untradeables` | none | none: 182k bit-exact parity checks |
| Engine (`data`, `stats`, `tracking`, `raids`) | behaviour | `com.onrate.engine.*`, `RateModel` extraction, `Bundled` loader, `ItemRanking` | none | none: replay parity tests |
| Persistence | keys, group, JSON schema | `store.ProfileStore`, `store.Keys` | plugin calls the V2 store | low: cross-load tests |
| Game adapters inside the plugin class | event handling and behaviour | `PriceBook`, `ClogReader`, `TeamDetector`, `RollCounters`, `ConfigRaidSettings`, `RaidPointsReader`, `TeamSpotter`, ToA classes | `ConfigRaidSettings` reads `ExpectedDropsConfig` | low: same code, moved |
| Plugin class | public behaviour, notices, first-run, overlay offer, times notice | split of responsibilities into the adapters above | becomes wiring only | medium: not runnable here (no game) |
| Account calculation | the panel's own numbers | the idea of `SnapshotService`: work out the account off the Swing thread, coalesce bursts | see below | low |
| Overlay (`OnRateOverlay`) | as is | nothing | imports | none |

**Background account processing, adapted.** V2's panel only reads snapshots. The original panel calculates in its draw method, and rewriting that would be the kind of UI refactor this branch avoids. Instead, after a kill the plugin asks a worker thread to work out the account totals and the Time-page lists; the result is handed to the panel for its **next draw only**, then dropped. Every other redraw (a click, a setting, a closed panel opening) calculates as the original did. The numbers are the same objects either way; only the thread changes. If the worker is late or fails, the panel simply calculates itself.

**Deliberately not transferred:** the V2 UI and its Rate Line, `Phrases` (the original wording stays), `OnRateConfig` and `SettingsMigration` (they would remove the five item views and rename settings), V2's `Session`/`BossView`/`Snapshot` (written for the V2 pages).

## Stage 4: how it is verified

1. Engine parity (from V2): math, data, replay, persistence, presentation numbers. The original is still the oracle, moved to package `legacy.expecteddrops` so it can sit next to the original UI classes.
2. The original's own UI tests (16 classes) run against the integrated plugin.
3. **Pixel comparison:** the original panel (oracle) and the integrated panel are drawn from identical event streams, for every page and all five item views, and compared pixel by pixel.
4. Full build.

---

# Results

## 1. Transferred from V2

| Improvement | Where it lives now | What it does for the plugin |
|---|---|---|
| Engine split from the UI, no RuneLite imports | `com.onrate.engine` (`data`, `stats`, `tracking`, `raids`) | testable without the game; 297 engine tests run headless |
| Math re-expressed in named units | `engine.math`: `Poisson`, `Luck`, `Verdict`, `ValueLuck`, `Rates`, `Untradeables` | one job per class instead of a 735-line `DropMath` |
| `RateModel` extracted from `Tracker` | `engine.tracking.RateModel` | rate rules (raids, parts, delves, waves, minions) in one pure place |
| Bundled data loader, `ItemRanking` | `engine.data.Bundled`, `engine.stats.ItemRanking` | one way to read the data files |
| Persistence store and frozen key names | `com.onrate.store.ProfileStore`, `Keys` | keys that are the saved format, named and tested in one place |
| Plugin split into adapters | `com.onrate.game`: `PriceBook`, `ClogReader`, `TeamDetector`, `RollCounters`, `ConfigRaidSettings`, `RaidPointsReader` | the plugin class shrank from 1,266 to about 800 lines of wiring; each adapter has one job |
| Background account work | `com.onrate.service.AccountPrecompute` | after a kill the whole account and every item time are worked out on a worker thread; a burst of events runs one calculation; the Swing thread only draws |
| Equivalence harness | `src/test/java/com/onrate/parity` | the original runs next to the plugin on identical input in every build |

## 2. Original components preserved, unchanged

Every UI class (`ExpectedDropsPanel` and its 20 helpers), `ExpectedDropsConfig` (names, descriptions, sections, the five item views), `DuoMode`, `OnRateOverlay`, `OverlayInfo`, the plugin's name, description, tags and every notice (welcome guide, times notice, overlay offer, first-run panel opening, old-setting cleanup). The UI source differs from the original **only in imports and in the names of the math functions it calls**, plus two hooks in the panel (lines that use the background result when present).

## 3. V2 parts deliberately excluded

| Excluded | Why |
|---|---|
| V2 panel, Rate Line, pages, kit | the original UI is the design source of truth |
| `view.Phrases`, `view.Numbers` | the original wording stays; the original `Texts` already prints the same numbers |
| `OnRateConfig`, `SettingsMigration` | would rename settings and fold five item views into two |
| `Session`, `BossView`, `Snapshot`, `SnapshotService` | written for the V2 pages; the original panel needed only the background account work, adapted as `AccountPrecompute` |
| V2 README, HOW-IT-WORKS, CHANGELOG entry for 2.0 | describe the V2 UI |

## 4. Statistical equivalence

Unchanged from V2's verification, now run against this branch: math (182,554 bit-exact comparisons), data (SHA-256 of the three data files, 677 catalogue lines), engine replay (seeded players over the whole catalogue plus targeted raid/delve/wave/roll/team runs, every result compared), saved data loading in both directions. New for this branch: **122,431 calls into the original `Texts` and the integrated `Texts` returned identical strings**, and "Copy my luck" is identical for 10 simulated accounts.

## 5. Feature parity

Nothing was removed. The original's own UI and plugin tests (96 UI tests, plus `PanelTextTest` and `TimesNoticeTest`) pass against the integrated plugin. The original's engine tests run in `com.onrate.engine` (297).

## 6. UI regression

`PanelPixelParityTest` builds the original panel (on the original engine) and the integrated panel from the same simulated player and compares the pictures pixel by pixel: **380 pages, 61.9 million pixels, all identical**. Covered: account Bosses / Items (driest, luckiest, pets, single drops, time) / Recent, the four Time lists, every played boss page with History open, in all five item views, with GP value off, the luck graph open and colour-blind colours on. Side-by-side samples are in `docs/integration/` (left original, right integrated).

Negative control: changing one colour channel by one unit (45 to 46) in the integrated UI fails the test on the first page.

What the comparison does not cover: real item pictures (they need the game's sprites; both sides draw initials), and hover tooltips as pop-ups (their text is covered by the `Texts` comparison and the original tooltip tests).

## 7. Tests

`./gradlew clean build`: **516 tests, 0 failures, 0 errors** (508 at the pre-merge review, plus 6 auto-follow tests and 2 shutdown tests). The 380-page pixel test passes. `Tracker` is 1,529 lines; `ExpectedDropsPlugin` is 928 (927 at the review).

## 8. Build

BUILD SUCCESSFUL (21m 44s). `build/libs/on-rate-1.1.0.jar`, 437 KB; contains the original UI and the new engine, no oracle classes.

## 9. Limitations

1. **Not run in a live RuneLite client** (this environment has no game). Event handling is the original's, moved into adapters; do a smoke test with `./gradlew run`.
2. **One timing difference by design:** after a kill, the panel now redraws when the background calculation finishes (normally a few milliseconds to a few hundred) instead of straight away. Clicks, settings and opening the panel redraw at once, as before.
3. `Tracker` was decomposed afterwards into eight package-private parts with no behaviour change; see `docs/TRACKER-DECOMPOSITION.md`.
4. Found while testing, **not changed** (existing behaviour): when a verdict pill's text wraps, the original lays the tile out again a moment later, so for one frame the second line can be cut. Visible in the original too.

## 10. Pre-merge review fixes

- **F1** No background work for a closed panel; its draws run in order, as the original's did.
- **F2** Numbers worked out before a data change (a click, a setting, another account) are never drawn.
- **F3** Saves are bound to the profile that was loaded, even across an account switch.
- **F4** Raid Data Tracker times are only added to the profile they were read for, and never to Leagues or other seasonal profiles.
- **F5** Damaged saved data loads its readable parts; valid data loads exactly as before.
- **Follow-up:** a merged burst keeps the newest boss to follow even if the last event doesn't follow (`AccountPrecompute`), and `onLootReceived`, `postNotes` and `onVarbitChanged` read `tracker`/`rolls` once so shutdown can't null them mid-event.
