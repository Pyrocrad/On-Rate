# Decomposing `Tracker`: proposal for review

Status: **done** (all eight steps, see [Results](#results) at the end). Branch `integration/original-ui-v2-engine`.

## What `Tracker` is today

`com.onrate.engine.tracking.Tracker`: 4,309 lines, about 160 methods, 35 instance fields, one lock (every public method is `synchronized`). It was carried over from the original with package renames and one extraction (`RateModel`).

Its callers are the original panel, the plugin, the adapters in `com.onrate.game`, `AccountPrecompute`, about 300 engine tests and the parity harness. Together they use roughly 70 of its public methods.

### Responsibilities found

Measured with a script over the source (line spans, fields touched, internal calls):

| # | Responsibility | Main methods | Lines | Own state (fields) |
|---|---|---|---|---|
| A | **Statistics** (boss, account, shared pools) | `stats` (289), `accountStats` (168), `sharedPools` (67), `perKill` (78), `partKills`, `combine`, `iconFor`, `cachedStats` | ~700 | none: reads `state`, `catalog` |
| B | **Shared item pools** (dragon pickaxe, visage...; per-boss split, "not from a boss") | `splitPool` x2, `unsplitPool`, `splitPoolOf`, `activeSplit`, `olderCopies`, `poolSeenAt`, `recordElsewhere`, `onOtherLoot`, `canSplit`, `validWhere`... | ~250 | none: reads/writes pool maps in `state` |
| C | **History** (the last 500 kills) and **past drops** ("I got this at kill...") | `addKill`, `addDropToHistory`, `history`, `recentDrops`, `removeFromHistory`, `dropsOf`, `dropsByLuck`, `lastUnique`, `addPastDrop` (230), `pastDropLimit`, `pastDropParts`, `pastDropNotRankedReason` | ~520 | none: per-boss kill lists in `state` |
| D | **Day counts** (Today, Last 7 days) | `dayCount`, `fillDaysFromHistory`, `uniqueNames`, `countKillToday`, `countUniqueToday`, `shiftToday`, `recentDays` | ~200 | `daysFilled` |
| E | **Fight times** (matching "Fight duration" messages to kills, Raid Data Tracker import) | `onTimeMessage`, `offerDuration`, `onDuration`, `setDuration`, `importRaidTimes`, `fillLiveRaid`, `killTimes`, `levelTimes`, `importedRaidTimes` | ~200 | 6 pending-match fields |
| F | **Raid records** (points arriving before or after the completion message, ToA team totals, Fractured Archive share) | `onRaidRecord`, `onToaPoints`, `onPartyPoints`, `applyPendingParty`, `onRaidCompletion`, `saveRecord`, `raidFamily`, `measuredRaids`, `raidRecords`, `averagePurpleChance`, `raidWaitExpected` | ~260 | 13 pending-match fields |
| G | **Trips** | `beforeKill`, `afterKill`, `continuesTrip`, `tripStats`, `tripValue`, `resetTrip`, `tripIdleLimitMillis`, `summarizeTrip`, `isLongRun` | ~250 | `trip`, `beforeThisKill` |
| H | **Collection log sync** | `onClogPage` (131), `wouldZeroSaved`, `coversPage`, unreadable-page tracking, `syncChecklist`, `syncReminder`, `bossWithMostKills`, `findQuantity`, `labelNamesBoss` | ~350 | `unreadablePages`, `headerUnreadable` |
| I | **Event intake and drop recording** (chat kill counts, loot, pets, delves, waves, reward rolls, the drop message, dry milestones) | `onChatMessage` (108), `onLoot` (79), `recordDrop` (73), `checkMilestones` (83), `onPet`, `onNewClogItem`, `onDelveLevel`, `onDelveScoreboard`, waves, rolls, `markKill`, `currentKc`, `startTracking` | ~700 | `lastBoss`, `lastPart`, `lastKillMillis`, `pendingWave`, `outbox` |
| J | **Lifecycle, settings, persistence glue, team share** | constructor, `setState`, `getState`, `saveData`, `changedKills`, `setKills`, `setRaids`, `setSettings`, `setTeamSizeDetector*`, `drainNotes`, `setLeftOut`, `reset`, `teamKills`, `olderShare`, `sharedKills`, `rememberOlderShare` | ~300 | `settings`, `teamSize`, dirty sets |
| K | Small lookups used everywhere | `dropNamed`, `rowOfSaved`, `findDrop`, `partForName`, `unit`, `unitOne`, `counts` | ~120 | none |

### Coupling that matters

- **Events read statistics.** `beforeKill`, `afterKill`, `countKillToday`, `checkMilestones`, `currentKc`, `rateOf`, `anyExpected` and `pastDropLimit` all call `stats()`. So A must be callable from the event side, under the same lock.
- **Everything shares `state`, `catalog`, `settings` and the "changed since last save" sets.** `setState` replaces the whole `TrackerState` object, so a component that kept a reference to the old one would silently go stale.
- **"Read" methods write.** `state.peek()` fills in missing maps on a saved record (`ensureMaps`), which changes what the next save writes. Statistics code must keep calling the same accessors in the same order, or saved JSON could differ.
- **Persisted classes are package-private** (`BossProgress`, `TrackerState`, `KillEntry` fields), so every component must stay in the `tracking` package. Their field names are the saved format and must not change.
- **Transient state is reset in two places** (`setState` resets 20+ fields; `reset(boss)` clears the trip and day caches). After the split, each component must reset the same fields at the same moment.

## Proposed design

**`Tracker` stays the public front door, unchanged.** Same class, same public methods, same signatures, same `synchronized` lock. The UI, the plugin, the adapters, the 300 engine tests and the parity harness keep compiling and calling exactly what they call today. Each public method keeps its lock and delegates to a component.

**Components are package-private, unsynchronized classes in `tracking`,** reached only through `Tracker` while it holds the lock, so the threading model does not change.

**One small shared holder**, `TrackerContext` (package-private): the catalog, the current `TrackerState` (swapped by `setState`), the current `RaidSettings`, and the two "changed since last save" sets with `markKills(boss)` / `markRaids(boss)`. This is the one new abstraction. It exists because `setState` replaces the state object, so components cannot hold the state directly. The alternative, components calling back into `Tracker`, would recreate the tangle.

**No interfaces, no event bus, no dependency injection.** Each component is one concrete class, built once by `Tracker`'s constructor. There is never a second implementation, so an interface would add nothing.

### The components

| Component | From | Responsibility | Depends on | Size |
|---|---|---|---|---|
| `FightTimes` | E | match time messages to kills and raids; imported raid times; average times | context | ~200 |
| `SharedItems` | B | shared pools: what was seen where, older copies, per-boss splits | context | ~250 |
| `DayCounts` | D | Today and Last 7 days | context, `Statistics` | ~200 |
| `Trips` | G | the current trip and its summary | context, `Statistics`, `SharedItems` | ~250 |
| `RaidRecords` | F | pending raid data matched to completions; raid records and averages | context, `DayCounts`, `Statistics` | ~260 |
| `ClogSync` | H | reading collection log pages, unreadable pages, sync checklist and reminders | context, `SharedItems` | ~350 |
| `HistoryBook` | C | the kill list: adding, querying, removing, past drops | context, `Statistics` (only for `pastDropLimit`) | ~520 |
| `Statistics` | A + K lookups | boss, account and shared-pool numbers | context, `SharedItems` | ~750 |
| *stays in `Tracker`* | I + J | event intake (`onChatMessage`, `onLoot`...) orchestrating the components in today's order; drop recording; milestones; lifecycle and persistence glue | all of the above | ~1,000 |

**Event intake deliberately stays in `Tracker`.** `onChatMessage` and `onLoot` are the orchestration: they call trips, history, day counts, raid matching, times and milestones in a precise order. That order is behaviour, so keeping it visible in one place is the maintainable choice. Splitting it out would only move the coordinator somewhere else.

`recordDrop` and `checkMilestones` (chat message text mixed with state changes) also stay. Separating the wording from the bookkeeping would mean editing logic, not moving it.

## How each step is done

1. **Move code, don't rewrite it.** Methods move with their bodies unchanged; only the receiver changes (`state` becomes `ctx.state()`, `stats(...)` becomes `statistics.stats(...)`).
2. **One component per commit**, in the order below, each fully verified before the next, each revertible on its own.
3. **Public methods stay on `Tracker`** and become one-line delegates.
4. **Transient state** moves with its owner, and `setState` / `reset` call each owner's `clear()` / `forget(boss)`, which reset exactly the fields `Tracker` resets today.

## Order, risks and benefits

| Step | Change | Risk | Main hazard | Benefit |
|---|---|---|---|---|
| 0 | **Close the test gaps first** (no production change): extend the replay simulator with ToA points, party points, time-only messages, both message orders, imported raid times, History removals and `setState` mid-session | none | none | the equivalence net covers every path the later steps move |
| 1 | `FightTimes` | low | the 20-second match window and "keep the better time" rank must survive the move | a self-contained state machine with its 6 fields, readable on its own |
| 2 | `SharedItems` | low | `activeSplit` is read by statistics and trips; it must keep reading the live state | the most intricate domain rule (per-boss split, "not from a boss") in one place |
| 3 | `DayCounts` | low-medium | `countKillToday` reads the "before this kill" trip snapshot, so call order matters | Today / 7 days logic separate from kill handling |
| 4 | `Trips` | medium | `beforeKill`/`afterKill` snapshot statistics around a kill; idle timeouts; long-run bosses | trip behaviour testable on its own |
| 5 | `RaidRecords` | medium | 13 pending fields with 1-, 2- and 5-minute windows; data can arrive in either order; changes day counts after the fact | the hardest timing logic isolated with its own state |
| 6 | `ClogSync` | medium | `onClogPage` writes kill counts, received counts, tracking start and shared totals; "never zero saved drops" guard | log reading separate from live tracking |
| 7 | `HistoryBook` | medium | `addPastDrop` (230 lines) has many rejection rules and dry-streak side effects; 500-kill trimming | history and past drops in one place |
| 8 | `Statistics` | medium | biggest move; `peek()` side effects and call order must be identical, or saved JSON changes | **the core numbers in one ~750-line class with no event handling mixed in**, the main maintainability win |

Steps 1 to 7 are independent of each other apart from what is listed under "Depends on". Step 8 comes last because it is the largest and benefits from the others being out of the way. **Stopping after any step leaves a working, verified plugin.**

## Verification for every step

All existing, reused as is:

- **Engine replay parity:** original vs this plugin on seeded players, every public result and **the saved JSON** compared, plus 50 targeted runs. This is the main guard for both behaviour and save compatibility.
- **Cross-version persistence:** saves load both ways.
- **Math, data and `Texts` parity.**
- **Pixel parity:** 380 pages identical. This guards "no UI output change".
- **The original's tests:** 297 engine tests, about 100 UI and plugin tests.
- A **negative control** after the first step, to show the extended harness catches a one-line change in a moved component.

Plus step 0's simulator extensions.

## Expected outcome

- `Tracker` around 1,000 lines: public API, event orchestration and lifecycle.
- Eight components of 200 to 750 lines, each owning one job and the transient state that goes with it.
- The statistics code (`Statistics`) holds no event handling.
- No change to statistics, UI output, settings or saved data, proven by the same tests that proved the integration.

## What I would not do

- Change the public API or split `Tracker` into several public services. The UI and tests depend on it, and that churn has no player benefit.
- Touch `TrackerState`, `BossProgress` or `KillEntry` field names (the saved format).
- Add interfaces, factories or an event system.
- Rewrite logic while moving it, even where it could be tidier. Clean-up, if wanted, would be separate commits after the move, each with its own parity run.

## Open questions for you

1. Is `TrackerContext` (one shared holder for catalog, state, settings and save marks) acceptable? The alternative is passing these four things into every component method.
2. Should event intake stay in `Tracker` as proposed, or do you want it split further?
3. Go through all eight steps, or stop after a subset? Steps 1, 2 and 8 give most of the benefit.

---

# Results

Implemented as approved, one commit per step, each passing the full build before the next was started.

## Where things are now

| Class | Lines | Responsibility | Depends on |
|---|---|---|---|
| `Tracker` | 1,529 | public API (58 one-line delegates, same javadoc, signatures and lock), event intake and its order (`onChatMessage`, `onLoot`, pets, delves, waves, reward rolls), drop recording, milestones, `countKillToday`/`countUniqueToday`, `addDropToHistory`, team share, lifecycle and save glue (`setState`, `saveData`, `setKills`, `setRaids`, `reset`) | all parts |
| `TrackerContext` | 64 | catalogue, current account state, settings, team counter, "changed since save" marks | none |
| `Statistics` | 966 | boss, account and shared-pool numbers; row and name lookups; `currentKc`, `anyExpected`, `rateOf`; `syncReminder` (it decides which bosses the account leaves out); `startTracking` (the kill count it counts from) | context, `SharedItems` |
| `HistoryBook` | 726 | History (500 kills per boss), its queries and removals, past drops and their rules, the luck graph's daily points | context, `Statistics`, `RaidRecords` |
| `ClogSync` | 405 | reading collection log pages, unreadable pages, sync checklist, boss with most kills | context, `Statistics` |
| `RaidRecords` | 370 | raid points and completions matched in either order (1, 2 and 5 minute windows), ToA and party points, Fractured Archive share, raid records and averages | context, `Statistics`, `DayCounts` |
| `FightTimes` | 308 | time messages matched to kills, Raid Data Tracker imports, kill and delve-level times | context |
| `Trips` | 302 | the current trip, the snapshot before each kill, idle limits, trip summary | context, `Statistics`, `SharedItems`, Tracker's outbox list |
| `SharedItems` | 262 | shared item pools: copies seen per boss and elsewhere, older copies, per-boss split | context |
| `DayCounts` | 248 | Today and Last 7 days | context, `Statistics` |

Tracker's 1,529 lines are 813 lines of its own logic in 40 methods, 232 lines of delegates, and fields, constants and javadoc. The proposal estimated about 1,000; the difference is the delegates, which keep every public javadoc where callers see it.

No part holds the account state, the settings or the team counter in a field: they ask `ctx` each time, so an account switch is seen everywhere at once. No part calls back into `Tracker`. No interfaces, no event bus.

## Differences from the proposal

1. **Order.** `Statistics` was moved third instead of last. DayCounts, Trips, RaidRecords, ClogSync and HistoryBook all call it, so moving it last would have meant those parts calling back into `Tracker` in the meantime. Step 1 was split in two: 1a `TrackerContext`, 1b `FightTimes`.
2. **`syncReminder` and `startTracking` went to `Statistics`**, not `ClogSync`: `accountStats` uses the first, and the second needs `currentKc` and is called by event handling as well as by log reading. Putting them in ClogSync would have made Statistics and ClogSync depend on each other.
3. **The team-size counter moved into `TrackerContext`.** `setTeamSizeDetector` replaces it, and both RaidRecords and Tracker read it, so it is held like the settings.
4. **Stayed in Tracker:** `countKillToday`, `countUniqueToday` and `addDropToHistory`. They read event state (the snapshot just before the kill, the last kill time), and keeping them next to the event code keeps the order of operations visible.
5. Each part that `setState` or `reset(boss)` used to reset field by field now has `clear()` / `forget(boss)` holding exactly those lines (DayCounts, Trips, RaidRecords, ClogSync).

## How each move was checked

- Bodies moved by a script and compared line by line with the previous commit: identical apart from the receiver of a call (`stats(` to `statistics.stats(`) and modifiers on the signature line. Every flagged line was reviewed by hand.
- Full build after every step: **460 tests, 0 failures** each time. Engine parity: 28 scenarios, 32,245 steps, 492 full-state comparisons including the saved JSON. Texts parity: 122,431 calls identical. Pixel parity: 380 pages, 62,368,650 pixels identical. Cross-version save loading passes.
- **Negative control** (step 1b): adding 1 ms to a matched time inside `FightTimes` fails 3 of the 4 engine parity tests, naming the step and the History field.
- **Caught by the tests in step 1a:** the first attempt rewrote `setSettings`' own parameter, so the tracker ignored the raid settings. 8 tests failed; the cause was found and the line fixed. No expected result was changed at any point.

## Remaining technical debt (not changed: existing behaviour)

1. **Old-account references after a switch.** `setState` never reset `FightTimes`' pending time match (`durationEntry` and the five fields with it) or `RaidRecords`' `pendingRecordMillis`, `lastRaidPart`, `lastRaidKc` and the latest Fractured Archive raid. `durationEntry` and `latestFractured` can still point at the previous account's kill or raid record for their 20-second and few-minute windows. It is the original's behaviour and has been kept; a fix should be its own change with its own test.
2. **Repeated small pieces**, left as moved: the local-day calculation (`Instant...toLocalDate().toEpochDay()`) appears five times in `DayCounts`; "the latest kill" is looked up by hand in `DayCounts.shiftToday`, `addDropToHistory` and kill handling, each slightly differently; `SharedItems` walks the same pool sources in four methods (`poolOnce`, `poolAlsoFrom`, `olderCopies`, `poolBosses`); `lastKillMillis > 0 ? lastKillMillis : System.currentTimeMillis()` appears twice in Tracker.
3. **Some javadoc and comments sit on the wrong method** (they were already misplaced in the original), for example the comment above `currentKc`.
4. **Large methods remain large:** `stats` (289 lines), `addPastDrop` (230), `onClogPage` (131), `onChatMessage` (108).
5. `Statistics` reads can still change saved data (`peek` fills in missing maps), as before. The order of reads was kept exactly.
6. `Tracker.MAX_KILLS`, `UNKNOWN_PART_LIMIT`, `NOT_FROM_A_BOSS`, `DELVE_SCOREBOARD`, `unit`, `roundMultiple` stay on Tracker because the UI and tests use them; the parts import them from there.

After the pre-merge review (F1–F5 fixed, see `INTEGRATION.md` section 10): `Tracker` 1,529 lines, full build 516 tests, 0 failures.
