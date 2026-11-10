# Contributing to On Rate

Thanks for helping. The most useful thing you can do is tell us when a number is wrong.

## A drop rate looks wrong

Open a [Wrong drop rate](https://github.com/Pyrocrad/On-Rate/issues/new/choose) issue with the boss, the item, the rate On Rate uses (hover the item to see it) and a link to the OSRS Wiki page that states the rate.

Want to fix it yourself? Rates live in `src/main/resources/com/expecteddrops/bosses.json`.

1. Look the item up on the [OSRS Wiki](https://oldschool.runescape.wiki) and change its `rate`.
2. Add a row to **Fixed in this check** in [docs/RATES.md](docs/RATES.md).
3. Run the tests (below) and open a pull request that links the wiki page.

Rates are solo base rates without diary, Combat Achievement or Slayer boosts, so a boosted rate from the wiki is not a fix. See [docs/RATES.md](docs/RATES.md) for how each rate was checked.

## Something else is wrong, or you have an idea

Use the [bug](https://github.com/Pyrocrad/On-Rate/issues/new/choose) or idea templates, or ask in the [On Rate Discord](https://discord.gg/gh9fx6T9zn). For a bug, a screenshot helps a lot, and so does saying whether you opened that boss's collection log page first.

## Code changes

1. Open the project in IntelliJ IDEA (Java 11 or newer).
2. Run **Tasks → application → run** in the Gradle tool window to start RuneLite with the plugin, or `./gradlew test` to run the tests.
3. Keep a change small and say what it fixes. If it changes any number a player sees, say so in your pull request so it can go under *Changes that affect your numbers* in [CHANGELOG.md](CHANGELOG.md).

Every push is built and tested automatically. A green tick means it builds and passes its tests.

How the panel and the maths work is explained in [docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md).

By contributing you agree your work is released under the [BSD 2-Clause licence](LICENSE).
