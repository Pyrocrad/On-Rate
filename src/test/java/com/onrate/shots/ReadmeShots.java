package com.onrate.shots;

import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.BossPart;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.data.DropDefinition;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.RaidRecord;
import com.onrate.engine.tracking.Tracker;
import com.expecteddrops.ui.ExpectedDropsPanel;
import com.expecteddrops.ui.ItemView;
import com.expecteddrops.ui.ViewOptions;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Assume;
import org.junit.Test;

/**
 * Draws the README pictures from the real panel code. It does nothing unless the environment variable
 * ONRATE_README_OUT names a folder (so the normal build never runs it).
 *
 * <p>Everything is drawn for one made-up player: the kills and drops below are invented, not anyone's
 * real account. Item pictures are read from a folder of OSRS Wiki inventory icons named in
 * ONRATE_README_ICONS (one file per item, named like the wiki file: {@code Twisted_bow.png}) and
 * handed to the panel in place of the game's item loader. The icons are never part of the repository.
 */
public class ReadmeShots
{
	private static final int WIDTH = 242;
	private static final int SCALE = 2;

	// ---------------------------------------------------------------- the made-up player

	/** One boss of the player: how many kills, and which kill each unique dropped at. */
	private static final class Plan
	{
		final String boss;
		final int kills;
		final String[] drops;

		Plan(String boss, int kills, String... drops)
		{
			this.boss = boss;
			this.kills = kills;
			// "Item=3": three drops at made-up (but fixed) kills; "Item@40": one drop at kill 40
			Random r = new Random(boss.hashCode() * 7L + kills);
			List<String> out = new ArrayList<>();
			for (String d : drops)
			{
				int eq = d.lastIndexOf('=');
				if (eq < 0)
				{
					out.add(d);
					continue;
				}
				int n = Integer.parseInt(d.substring(eq + 1));
				for (int i = 0; i < n; i++)
				{
					out.add(d.substring(0, eq) + "@" + (1 + r.nextInt(kills)));
				}
			}
			this.drops = out.toArray(new String[0]);
		}
	}

	/** Item then the kill (or raid) it dropped at, as "Item@kill". Raid parts are "Item@kill" on the normal mode; "Item@CM3" on Challenge Mode. */
	private static final Plan[] PLAYER = {
		new Plan("Zulrah", 1612, "Tanzanite fang=3", "Magic fang=6", "Serpentine visage=3", "Jar of swamp=1"),
		new Plan("Vorkath", 1058, "Vorkath's head@38", "Vorkath's head=20", "Dragonbone necklace=1"),
		new Plan("Duke Sucellus", 388, "Magus vestige@301", "Eye of the duke@212", "Chromium ingot@57", "Chromium ingot@310"),
		new Plan("Vardorvis", 466, "Ultor vestige@188", "Chromium ingot@401"),
		new Plan("The Leviathan", 301, "Chromium ingot@88"),
		new Plan("The Whisperer", 255, "Siren's staff@244"),
		new Plan("General Graardor", 987, "Bandos chestplate=3", "Bandos tassets=2", "Bandos boots=2", "Bandos hilt=2", "Godsword shard 1=2"),
		new Plan("Kree'arra", 534, "Armadyl helmet=1", "Armadyl chestplate=1", "Armadyl chainskirt=2", "Armadyl hilt=1"),
		new Plan("Phantom Muspah", 623, "Ancient icon=11", "Venator shard=4", "Muphin@402"),
		new Plan("Nex", 214, "Zaryte vambraces@162", "Nihil horn@93", "Torva platebody (damaged)@189"),
		new Plan("Corporeal Beast", 143, "Spirit shield=3", "Holy elixir=2"),
		new Plan("Alchemical Hydra", 702, "Hydra's eye=4", "Hydra's fang=3", "Hydra's heart=3", "Hydra tail=1", "Hydra leather=1",
			"Alchemical hydra heads=4"),
		new Plan("Tempoross", 1159, "Tackle box@402", "Fish barrel@876", "Big harpoonfish@1033", "Tome of water (empty)@311", "Tome of water (empty)@990"),
		new Plan("Wintertodt", 2184, "Warm gloves=3", "Bruma torch=3", "Pyromancer garb=14", "Tome of fire=2"),
		new Plan("The Fractured Archive", 61, "Rondache@23", "Horn of Bran@52", "Archivist's folio (inert)@9", "Archivist's folio (inert)@38",
			"Fragment of living earth@14", "Fragment of living earth@27", "Fragment of living earth@45"),
		new Plan("Chambers of Xeric", 184, "Dexterous prayer scroll@41", "Twisted bow@97", "Ancestral robe top@120", "Dinh's bulwark@150", "Ancestral hat@77",
			"Arcane prayer scroll@166", "Dragon claws@CM9"),
		new Plan("Theatre of Blood", 88, "Avernic defender hilt@71", "Justiciar chestguard@44"),
	};

	/** The Challenge Mode raids at Chambers (of the 184 + 22 completions). */
	private static final int COX_CM_RAIDS = 22;

	private static final Map<String, Long> PRICES = new HashMap<>();

	static
	{
		long[][] none = {};
		Object[][] p = {
			{"Twisted bow", 1_380_000_000L}, {"Dragon claws", 62_000_000L}, {"Dinh's bulwark", 21_000_000L},
			{"Ancestral robe top", 83_000_000L}, {"Dexterous prayer scroll", 38_000_000L}, {"Arcane prayer scroll", 33_000_000L},
			{"Avernic defender hilt", 36_000_000L}, {"Tanzanite fang", 4_000_000L}, {"Magic fang", 5_500_000L},
			{"Serpentine visage", 3_000_000L}, {"Vorkath's head", 2_000_000L}, {"Dragonbone necklace", 1_600_000L},
			{"Magus vestige", 21_000_000L}, {"Ultor vestige", 34_000_000L}, {"Chromium ingot", 1_400_000L},
			{"Siren's staff", 4_000_000L}, {"Bandos chestplate", 17_000_000L}, {"Bandos tassets", 28_000_000L},
			{"Godsword shard 1", 1_000_000L}, {"Ancient icon", 3_000_000L}, {"Venator shard", 9_000_000L},
			{"Zaryte vambraces", 43_000_000L}, {"Nihil horn", 55_000_000L}, {"Torva platebody", 190_000_000L},
			{"Torva platebody (damaged)", 190_000_000L}, {"Spirit shield", 1_500_000L}, {"Hydra's eye", 1_000_000L},
			{"Hydra leather", 2_500_000L}, {"Alchemical hydra heads", 2_000_000L}, {"Tackle box", 1_000_000L},
			{"Fish barrel", 1_000_000L}, {"Big harpoonfish", 1_500_000L}, {"Tome of fire", 4_000_000L},
			{"Warm gloves", 200_000L}, {"Bruma torch", 200_000L}, {"Pyromancer garb", 600_000L}, {"Rondache", 8_000_000L},
			{"Horn of Bran", 30_000_000L}, {"Archivist's folio (inert)", 2_000_000L}, {"Fragment of living earth", 2_000_000L},
			{"Scythe of vitur (uncharged)", 650_000_000L}, {"Ghrazi rapier", 150_000_000L}, {"Sanguinesti staff (uncharged)", 95_000_000L},
			{"Justiciar faceguard", 45_000_000L}, {"Justiciar chestguard", 60_000_000L}, {"Justiciar legguards", 60_000_000L},
			{"Armadyl helmet", 8_000_000L}, {"Armadyl chestplate", 28_000_000L}, {"Armadyl chainskirt", 27_000_000L},
			{"Armadyl hilt", 12_000_000L}, {"Bandos hilt", 7_000_000L}, {"Bandos boots", 4_000_000L}, {"Hydra's claw", 8_000_000L},
			{"Hydra tail", 4_500_000L}, {"Hydra's heart", 1_500_000L}, {"Holy elixir", 7_000_000L}, {"Spectral sigil", 60_000_000L},
			{"Arcane sigil", 80_000_000L}, {"Elysian sigil", 300_000_000L}, {"Elder maul", 120_000_000L}, {"Kodai insignia", 190_000_000L},
			{"Dragon hunter crossbow", 80_000_000L}, {"Twisted buckler", 25_000_000L}, {"Ancestral hat", 40_000_000L},
			{"Ancestral robe bottom", 60_000_000L}, {"Eye of the duke", 20_000_000L}, {"Venator vestige", 30_000_000L}, {"Bellator vestige", 25_000_000L},
			{"Executioner's axe head", 9_000_000L}, {"Leviathan's lure", 15_000_000L}, {"Zorya's tome (uncharged)", 6_000_000L},
			{"Ascension crossbows", 20_000_000L}, {"Obligator (uncharged)", 25_000_000L}, {"Tonalztics of ralos (uncharged)", 90_000_000L}};
		for (Object[] e : p)
		{
			PRICES.put((String) e[0], (Long) e[1]);
		}
	}

	private static long price(String name)
	{
		Long v = PRICES.get(name);
		return v != null ? v : 1_500_000L;
	}

	/** An invented duration for one kill of a boss, in seconds, with a little spread. */
	private static int seconds(String boss, Random r)
	{
		switch (boss)
		{
			case "Zulrah": return 118 + r.nextInt(40);
			case "Vorkath": return 92 + r.nextInt(30);
			case "Duke Sucellus": return 105 + r.nextInt(35);
			case "Vardorvis": return 88 + r.nextInt(30);
			case "The Leviathan": return 130 + r.nextInt(40);
			case "The Whisperer": return 150 + r.nextInt(45);
			case "General Graardor": return 70 + r.nextInt(25);
			case "Kree'arra": return 60 + r.nextInt(25);
			case "Phantom Muspah": return 100 + r.nextInt(30);
			case "Nex": return 240 + r.nextInt(80);
			case "Corporeal Beast": return 95 + r.nextInt(40);
			case "Alchemical Hydra": return 135 + r.nextInt(35);
			default: return 90 + r.nextInt(60);
		}
	}

	private static final class Ev
	{
		final long at;
		final int order;
		final Runnable run;

		Ev(long at, int order, Runnable run)
		{
			this.at = at;
			this.order = order;
			this.run = run;
		}
	}

	private static String commas(int n)
	{
		return String.format(Locale.ROOT, "%,d", n);
	}

	private static final RaidSettings RAID_SETTINGS = new RaidSettings()
	{
		@Override
		public double uniqueChance(String raid)
		{
			switch (raid)
			{
				case "cox": return 0.035;
				case "coxCm": return 0.05;
				case "tob": return 0.027;
				case "tobHm": return 0.032;
				case "toaEntry": return 0.0075;
				case "toa": return 0.025;
				case "toaExpert": return 0.05;
				case "fractured": return 1 / 28.0;
				default: return 0.0;
			}
		}

		@Override
		public int teamSize(String raid)
		{
			return raid.startsWith("tob") ? 4 : "fractured".equals(raid) ? 2 : 1;
		}
	};

	/** Plays the made-up player into a fresh tracker. Collection log pages are read only for {@code logPages}. */
	private static Tracker play(long now, Set<String> logPages) throws Exception
	{
		BossCatalog catalog = Bundled.catalog(new Gson());
		Tracker tracker = new Tracker(catalog);
		tracker.setSettings(RAID_SETTINGS);
		tracker.setTeamSizeDetectorFor(b -> "Nex".equals(b) ? 3 : 1);
		if (logPages.contains("The Gauntlet"))
		{
			// the player's Gauntlet runs are only in the collection log (the Gauntlet has no loot to track):
			// a full set of crystal armour seeds, so the armour is off the Ladlor chart's list
			Map<String, Integer> page = new LinkedHashMap<>();
			page.put("Crystal armour seed", 7);
			page.put("Crystal weapon seed", 3);
			page.put("Enhanced crystal weapon seed", 0);
			page.put("Youngllef", 0);
			tracker.onClogPage("The Gauntlet", java.util.Arrays.asList("Gauntlet kills: 74", "Corrupted Gauntlet kills: 38"), page);
		}

		long span = 75L * 86_400_000L;
		long start = now - span;
		List<Ev> events = new ArrayList<>();
		int order = 0;
		Map<String, Map<String, Integer>> got = new LinkedHashMap<>();
		Live live = new Live();

		for (Plan plan : PLAYER)
		{
			BossDefinition boss = catalog.byName(plan.boss);
			if (boss == null)
			{
				throw new IllegalStateException("No boss " + plan.boss);
			}
			Random r = new Random(plan.boss.hashCode());
			Map<String, Integer> counts = got.computeIfAbsent(boss.getName(), k -> new LinkedHashMap<>());
			for (String d : plan.drops)
			{
				int at = d.lastIndexOf('@');
				String item = d.substring(0, at);
				boolean known = false;
				for (DropDefinition def : boss.getDrops())
				{
					known |= def.getAllNames().contains(item);
				}
				for (DropDefinition def : catalog.poolsOf(boss).values())
				{
					known |= def.getName().equals(item);
				}
				if (!known)
				{
					throw new IllegalStateException(plan.boss + " has no drop " + item);
				}
				counts.merge(item, 1, Integer::sum);
			}
			// kill number -> items of that kill
			Map<String, List<String>> lootAt = new HashMap<>();
			for (String d : plan.drops)
			{
				int at = d.lastIndexOf('@');
				lootAt.computeIfAbsent(d.substring(at + 1), k -> new ArrayList<>()).add(d.substring(0, at));
			}
			String bossName = boss.getName();
			boolean rolls = boss.getRollsPerKill() > 0;
			boolean raid = boss.getRaid() != null;
			int cmRaids = "Chambers of Xeric".equals(bossName) ? COX_CM_RAIDS : 0;
			int normal = plan.kills;
			int total = normal + cmRaids;
			// the raid count is spread over the span; the first events of each raid are early
			double[] timeline = new double[total];
			double t = 0;
			for (int i = 0; i < total; i++)
			{
				t += 0.3 + r.nextDouble();
				timeline[i] = t;
			}
			for (int i = 0; i < total; i++)
			{
				final int index = i;
				long when = start + (long) (timeline[i] / t * (span - 3_600_000L * 2)) + r.nextInt(60_000);
				boolean cm = index >= normal;
				int partIndex = cm ? 1 : 0;
				int number = cm ? index - normal + 1 : index + 1;
				String key = cm ? "CM" + number : String.valueOf(number);
				List<String> items = new ArrayList<>(lootAt.getOrDefault(key, Collections.emptyList()));
				int kcNow = number;
				final List<String> loot = items;
				final int ord = order++;
				final long at = when;
				events.add(new Ev(at, ord, () ->
				{
					live.add(bossName, loot, cm);
					List<Tracker.LootItem> lootItems = new ArrayList<>();
					for (String item : loot)
					{
						lootItems.add(new Tracker.LootItem(item, 1));
					}
					if (rolls)
					{
						tracker.onRewardRolls(bossName, 1, at);
						tracker.onLoot(boss.getLootNames().get(0), 1, lootItems, at + 100);
					}
					else if (raid)
					{
						playRaid(tracker, boss, cm, kcNow, loot, at, r_seed(bossName, index));
					}
					else
					{
						String kcName = boss.getKcNames().get(0);
						tracker.onChatMessage(String.format(Locale.ROOT, "Your %s kill count is: <col=ff0000>%s</col>.", kcName, commas(kcNow)), at);
						int secs = seconds(bossName, new Random(r_seed(bossName, index)));
						tracker.onChatMessage(String.format(Locale.ROOT, "Fight duration: <col=ff0000>%d:%02d.40</col>.", secs / 60, secs % 60), at + 100);
						tracker.onLoot(boss.getLootNames().get(0), 1, lootItems, at + 300);
						for (String item : loot)
						{
							for (DropDefinition def : boss.getDrops())
							{
								if (def.isPet() && def.getAllNames().contains(item))
								{
									tracker.onChatMessage("You have a funny feeling like you're being followed.", at + 400);
								}
							}
						}
					}
					if (index == 2 && logPages.contains(bossName))
					{
						// the player opens this boss's collection log page early on
						live.sync(tracker, catalog, boss);
					}
				}));
			}
		}

		events.sort((a, b) -> a.at != b.at ? Long.compare(a.at, b.at) : Integer.compare(a.order, b.order));
		long lastDay = -1;
		for (Ev e : events)
		{
			long day = e.at / 86_400_000L;
			if (lastDay >= 0 && day != lastDay)
			{
				tracker.recordHistory(lastDay, RAID_SETTINGS);
			}
			lastDay = day;
			e.run.run();
		}
		tracker.recordHistory(lastDay, RAID_SETTINGS);

		// the collection log pages the player opened, read once more at the end
		for (Plan plan : PLAYER)
		{
			if (logPages.contains(plan.boss))
			{
				live.sync(tracker, catalog, catalog.byName(plan.boss));
			}
		}
		return tracker;
	}

	/** What the player has so far, so a collection log page can be read at any point of the story. */
	private static final class Live
	{
		final Map<String, Map<String, Integer>> got = new LinkedHashMap<>();
		final Map<String, Integer> kills = new HashMap<>();
		final Map<String, Integer> cmKills = new HashMap<>();

		void add(String boss, List<String> items, boolean cm)
		{
			(cm ? cmKills : kills).merge(boss, 1, Integer::sum);
			Map<String, Integer> m = got.computeIfAbsent(boss, k -> new LinkedHashMap<>());
			for (String item : items)
			{
				m.merge(item, 1, Integer::sum);
			}
		}

		void sync(Tracker tracker, BossCatalog catalog, BossDefinition boss)
		{
			Map<String, Integer> pooled = new HashMap<>();
			for (Map<String, Integer> m : got.values())
			{
				for (Map.Entry<String, Integer> e : m.entrySet())
				{
					pooled.merge(e.getKey(), e.getValue(), Integer::sum);
				}
			}
			Map<String, Integer> page = new LinkedHashMap<>();
			Map<String, Integer> mine = got.getOrDefault(boss.getName(), Collections.emptyMap());
			for (DropDefinition d : boss.getDrops())
			{
				page.put(d.getName(), mine.getOrDefault(d.getName(), 0));
			}
			for (DropDefinition d : catalog.poolsOf(boss).values())
			{
				page.put(d.getName(), pooled.getOrDefault(d.getName(), 0));
			}
			int total = kills.getOrDefault(boss.getName(), 0);
			List<String> lines = new ArrayList<>();
			if (boss.hasParts())
			{
				for (int i = 0; i < boss.getParts().size(); i++)
				{
					BossPart part = boss.getParts().get(i);
					int n = i == 0 ? total : cmKills.getOrDefault(boss.getName(), 0);
					lines.add(("The Fractured Archive".equals(boss.getName()) ? "Fractured Archive" : part.getKcNames().get(0)) + " completions: " + commas(n));
				}
			}
			else if (boss.getRollsPerKill() > 0)
			{
				lines.add("Rewards claimed: " + commas(total));
			}
			else
			{
				lines.add(boss.getName() + " kills: " + commas(total));
			}
			tracker.onClogPage(boss.getClogPages().get(0), lines, page);
		}
	}

	private static long r_seed(String boss, int index)
	{
		return boss.hashCode() * 31L + index;
	}

	private static void playRaid(Tracker tracker, BossDefinition boss, boolean cm, int kc, List<String> names, long at, long seed)
	{
		Random r = new Random(seed);
		int partIndex = cm ? 1 : 0;
		BossPart part = boss.getParts().get(partIndex);
		RaidRecord record = null;
		switch (boss.getRaid())
		{
			case "cox":
				int team = 1 + r.nextInt(5);
				int mine = 14_000 + r.nextInt(30_000);
				record = RaidRecord.cox(mine, mine + (team - 1) * (12_000 + r.nextInt(16_000)), team);
				break;
			case "tob":
				record = RaidRecord.tob(4, r.nextInt(2), r.nextInt(3), r.nextBoolean());
				break;
			case "fractured":
				record = RaidRecord.fractured(1 + r.nextInt(3), r.nextInt(2), -1.0);
				break;
			default:
				break;
		}
		if (record != null)
		{
			tracker.onRaidRecord(record, at);
		}
		tracker.onChatMessage(String.format(Locale.ROOT, "Your completed %s count is: <col=ff0000>%s</col>.", part.getKcNames().get(0), commas(kc)), at + 800);
		int minutes = "cox".equals(boss.getRaid()) ? (cm ? 34 : 24) + r.nextInt(14) : "tob".equals(boss.getRaid()) ? 22 + r.nextInt(10) : 13 + r.nextInt(8);
		String text = "cox".equals(boss.getRaid())
			? String.format(Locale.ROOT, "Congratulations - your raid is complete! Team size: 3 players Duration: %d:%02d.20", minutes, r.nextInt(60))
			: String.format(Locale.ROOT, "%s total completion time: %d:%02d.40", boss.getName(), minutes, r.nextInt(60));
		tracker.onTimeMessage(text, at + 1_200);
		List<Tracker.LootItem> items = new ArrayList<>();
		for (String n : names)
		{
			items.add(new Tracker.LootItem(n, 1));
		}
		tracker.onLoot(boss.getLootNames().get(0), 1, items, at + 2_500);
		for (String item : names)
		{
			for (DropDefinition def : boss.getDrops())
			{
				if (def.isPet() && def.getAllNames().contains(item))
				{
					tracker.onChatMessage("You have a funny feeling like you're being followed.", at + 3_000);
				}
			}
		}
	}

	// ---------------------------------------------------------------- item pictures

	private static final Map<String, Integer> IDS = new LinkedHashMap<>();
	private static final Map<Integer, String> NAMES = new HashMap<>();
	private static final Set<String> NEEDED = new TreeSet<>();
	private static final Set<String> MISSING = new TreeSet<>();
	private static File iconDir;

	private static synchronized int idFor(String item)
	{
		if (item == null)
		{
			return -1;
		}
		Integer id = IDS.get(item);
		if (id == null)
		{
			id = 1000 + IDS.size();
			IDS.put(item, id);
			NAMES.put(id, item);
		}
		return id;
	}

	/** The wiki's file name for an item: first letter capital, spaces as underscores. */
	/** Items the game names differently from the wiki's inventory icon files (pets, and an outfit shown by one piece). */
	private static final Map<String, String> ICON_FILE = new HashMap<>();

	static
	{
		ICON_FILE.put("Lil' zik", "Lil'_Zik.png");
		ICON_FILE.put("Muphin", "Muphin_(melee).png");
		ICON_FILE.put("Ikkle hydra", "Ikkle_Hydra_(serpentine).png");
		ICON_FILE.put("Pyromancer outfit pieces", "Pyromancer_garb.png");
	}

	static String wikiFile(String item)
	{
		String alias = ICON_FILE.get(item);
		if (alias != null)
		{
			return alias;
		}
		String s = item.replace(' ', '_');
		return Character.toUpperCase(s.charAt(0)) + s.substring(1) + ".png";
	}

	private static String phase = "";
	private static final Map<String, Set<String>> WHERE = new TreeMap<>();

	private static AsyncBufferedImage picture(String item)
	{
		NEEDED.add(item);
		WHERE.computeIfAbsent(item, k -> new TreeSet<>()).add(phase);
		BufferedImage icon = null;
		if (iconDir != null)
		{
			File f = new File(iconDir, wikiFile(item));
			if (f.isFile())
			{
				try
				{
					icon = ImageIO.read(f);
				}
				catch (Exception e)
				{
					throw new IllegalStateException(e);
				}
			}
		}
		if (icon == null)
		{
			MISSING.add(item);
		}
		AsyncBufferedImage image = new AsyncBufferedImage(null, 36, 32, BufferedImage.TYPE_INT_ARGB)
		{
			@Override
			public void onLoaded(Runnable r)
			{
				r.run();
			}

			@Override
			public void addTo(javax.swing.JLabel label)
			{
				label.setIcon(new javax.swing.ImageIcon(this));
			}

			@Override
			public void addTo(javax.swing.JButton button)
			{
				button.setIcon(new javax.swing.ImageIcon(this));
			}
		};
		if (icon != null)
		{
			Graphics2D g = image.createGraphics();
			g.drawImage(icon, (36 - icon.getWidth()) / 2, (32 - icon.getHeight()) / 2, null);
			g.dispose();
		}
		else
		{
			// a missing icon is bright magenta, so a picture that shows one is easy to find
			Graphics2D g = image.createGraphics();
			g.setColor(new Color(255, 0, 255));
			g.fillRect(0, 0, 36, 32);
			g.dispose();
		}
		return image;
	}

	/** An ItemManager that hands out the wiki icons: no game client, no network. */
	private static ItemManager fakeItemManager() throws Exception
	{
		Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
		ItemManager manager = (ItemManager) unsafe.allocateInstance(ItemManager.class);
		Field cacheField = ItemManager.class.getDeclaredField("itemImages");
		cacheField.setAccessible(true);
		com.google.common.cache.LoadingCache<Object, AsyncBufferedImage> cache = com.google.common.cache.CacheBuilder.newBuilder().build(
			new com.google.common.cache.CacheLoader<Object, AsyncBufferedImage>()
			{
				@Override
				public AsyncBufferedImage load(Object key) throws Exception
				{
					Field f = key.getClass().getDeclaredField("itemId");
					f.setAccessible(true);
					return picture(NAMES.get((Integer) f.get(key)));
				}
			});
		unsafe.putObject(manager, unsafe.objectFieldOffset(cacheField), cache);
		return manager;
	}

	// ---------------------------------------------------------------- the panel

	private static void onSwing(Runnable r) throws Exception
	{
		SwingUtilities.invokeAndWait(r);
	}

	private static void settle() throws Exception
	{
		for (int i = 0; i < 4; i++)
		{
			SwingUtilities.invokeAndWait(() ->
			{
			});
		}
	}

	private static void set(Object target, String field, Object value) throws Exception
	{
		Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void setEnum(Object target, String field, String nested, String value) throws Exception
	{
		Class<?> type = Class.forName(target.getClass().getName() + "$" + nested);
		set(target, field, Enum.valueOf((Class<Enum>) type, value));
	}

	private static ExpectedDropsPanel panel(Tracker tracker, ItemManager items, ItemView view) throws Exception
	{
		ExpectedDropsPanel[] out = new ExpectedDropsPanel[1];
		onSwing(() ->
		{
			ViewOptions options = new ViewOptions(view, true, true, false, false, true);
			out[0] = new ExpectedDropsPanel(tracker, items, () -> RAID_SETTINGS, (ToIntFunction<String>) ReadmeShots::idFor, () ->
			{
			}, (ToLongFunction<String>) ReadmeShots::price, () -> options);
			out[0].onActivate();
		});
		return out[0];
	}

	/** Doubles every pixel: the panel is laid out and drawn at 1x (so every text fits as in the client) and shown at 2x. */
	private static BufferedImage up(BufferedImage in)
	{
		BufferedImage out = new BufferedImage(in.getWidth() * SCALE, in.getHeight() * SCALE, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < out.getHeight(); y++)
		{
			for (int x = 0; x < out.getWidth(); x++)
			{
				out.setRGB(x, y, in.getRGB(x / SCALE, y / SCALE));
			}
		}
		return out;
	}

	private static void layoutAll(Component c)
	{
		if (c instanceof Container)
		{
			((Container) c).doLayout();
			for (Component child : ((Container) c).getComponents())
			{
				layoutAll(child);
			}
		}
	}

	/** Draws the panel at 2x as the client does (dark theme, RuneLite's own fonts), top {@code maxHeight} pixels (at 1x) of it. */
	private static BufferedImage draw(ExpectedDropsPanel panel, int maxHeight) throws Exception
	{
		BufferedImage[] shot = new BufferedImage[1];
		settle();
		onSwing(() ->
		{
			panel.setSize(WIDTH, 6000);
			layoutAll(panel);
			int height = Math.max(200, Math.min(6000, panel.getPreferredSize().height));
			panel.setSize(WIDTH, height);
			layoutAll(panel);
			layoutAll(panel);
		});
		settle();
		onSwing(() ->
		{
			int height = Math.max(200, Math.min(6000, panel.getPreferredSize().height));
			panel.setSize(WIDTH, height);
			layoutAll(panel);
			int h = Math.min(height, maxHeight);
			BufferedImage image = new BufferedImage(WIDTH, h, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setColor(ColorScheme.DARK_GRAY_COLOR);
			g.fillRect(0, 0, image.getWidth(), image.getHeight());
			panel.printAll(g);
			g.dispose();
			shot[0] = up(image);
		});
		return shot[0];
	}

	private static void showBoss(ExpectedDropsPanel panel, String boss) throws Exception
	{
		phase = "boss " + boss;
		onSwing(() ->
		{
			try
			{
				set(panel, "historyOpen", true);
				panel.showBoss(boss);
				panel.refresh();
			}
			catch (Exception e)
			{
				throw new IllegalStateException(e);
			}
		});
	}

	private static void showAccount(ExpectedDropsPanel panel, int tab, int top) throws Exception
	{
		phase = "account tab " + tab + " top " + top;
		onSwing(() ->
		{
			try
			{
				set(panel, "accountTab", tab);
				set(panel, "topMode", top);
				panel.showBoss("Account overview");
				panel.refresh();
			}
			catch (Exception e)
			{
				throw new IllegalStateException(e);
			}
		});
	}

	/** The tooltip of the first component whose tip has this item as its title, drawn as RuneLite draws a tooltip. */
	private static BufferedImage tooltip(ExpectedDropsPanel panel, String title) throws Exception
	{
		String[] text = new String[1];
		JComponent[] owner = new JComponent[1];
		onSwing(() -> find(panel, title, text, owner));
		if (text[0] == null)
		{
			throw new IllegalStateException("No tooltip for " + title);
		}
		BufferedImage[] out = new BufferedImage[1];
		onSwing(() ->
		{
			JToolTip tip = new JToolTip();
			tip.setComponent(owner[0]);
			tip.setTipText(text[0]);
			tip.setFont(FontManager.getRunescapeSmallFont());
			tip.setForeground(Color.WHITE);
			tip.setBackground(new Color(45, 45, 45));
			tip.setBorder(javax.swing.BorderFactory.createCompoundBorder(new LineBorder(new Color(70, 70, 70)), new EmptyBorder(3, 6, 3, 6)));
			java.awt.Dimension d = tip.getPreferredSize();
			tip.setSize(d);
			layoutAll(tip);
			BufferedImage image = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			tip.printAll(g);
			g.dispose();
			out[0] = up(image);
		});
		return out[0];
	}

	private static void find(Component c, String title, String[] text, JComponent[] owner)
	{
		if (text[0] != null)
		{
			return;
		}
		if (c instanceof JComponent)
		{
			String tip = ((JComponent) c).getToolTipText();
			if (tip != null && tip.contains("<b>" + title + "</b>"))
			{
				text[0] = tip;
				owner[0] = (JComponent) c;
				return;
			}
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				find(child, title, text, owner);
			}
		}
	}

	// ---------------------------------------------------------------- the pictures

	private static void save(BufferedImage image, File dir, String name) throws Exception
	{
		ImageIO.write(image, "png", new File(dir, name));
		System.out.println("wrote " + name + " " + image.getWidth() + "x" + image.getHeight());
		int magenta = 0;
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if ((image.getRGB(x, y) & 0xFFFFFF) == 0xFF00FF)
				{
					magenta++;
				}
			}
		}
		if (magenta > 0)
		{
			System.out.println("MAGENTA " + name + " " + magenta);
		}
	}

	@Test
	public void drawReadmePictures() throws Exception
	{
		String out = System.getenv("ONRATE_README_OUT");
		Assume.assumeTrue("set ONRATE_README_OUT to draw the README pictures", out != null && !out.isEmpty());
		File dir = new File(out);
		dir.mkdirs();
		String icons = System.getenv("ONRATE_README_ICONS");
		iconDir = icons == null || icons.isEmpty() ? null : new File(icons);

		onSwing(() -> net.runelite.client.ui.laf.RuneLiteLAF.setup());
		com.onrate.engine.data.KillTimes.bundled(new Gson());
		com.onrate.engine.data.LadlorChart.bundled(new Gson());

		long now = System.currentTimeMillis();
		Set<String> all = new TreeSet<>();
		for (Plan p : PLAYER)
		{
			all.add(p.boss);
		all.add("The Gauntlet");
		}
		Tracker full = play(now, all);
		// first run: the player has killed all of this, but has opened only a few collection log pages
		Set<String> opened = new TreeSet<>(java.util.Arrays.asList("Alchemical Hydra", "Chambers of Xeric", "Corporeal Beast", "Duke Sucellus", "General Graardor", "Kree'arra"));
		Tracker firstRun = play(now, opened);

		ItemManager items = fakeItemManager();
		for (Plan plan : PLAYER)
		{
			com.onrate.engine.stats.BossStats s = full.stats(plan.boss, RAID_SETTINGS);
			System.out.printf(Locale.ROOT, "STAT %-24s kills %5d  got %2d  expected %6.2f  luck %.2f%n", plan.boss, s.getKillCount(), s.getAnyRow().getReceived(), s.getAnyRow().getExpected(), s.getAnyRow().getLuck());
		}

		// 1. Time page, Ladlor chart
		ExpectedDropsPanel p = panel(full, items, ItemView.TARGET_BARS);
		onSwing(() ->
		{
			try
			{
				setEnum(p, "timeView", "TimeView", "CHART");
			}
			catch (Exception e)
			{
				throw new IllegalStateException(e);
			}
		});
		showAccount(p, 1, 4);
		BufferedImage time = draw(p, 700);
		save(time, dir, "shot-time.png");

		// 2. Items > Single drops
		showAccount(p, 1, 3);
		BufferedImage drops = draw(p, 860);
		save(drops, dir, "shot-single-drops.png");

		// 4. The Fractured Archive
		showBoss(p, "The Fractured Archive");
		BufferedImage tfa = draw(p, 700);
		save(tfa, dir, "shot-fractured-archive.png");

		// hero parts: the account overview, Chambers of Xeric and the Twisted bow hover
		showAccount(p, 0, 0);
		BufferedImage account = draw(p, 420);
		showBoss(p, "Chambers of Xeric");
		BufferedImage cox = draw(p, 420);
		BufferedImage hover = tooltip(p, "Twisted bow");
		save(account, dir, "part-account.png");
		save(cox, dir, "part-cox.png");
		save(hover, dir, "part-hover.png");
		save(hero(account, cox, hover), dir, "hero.png");

		// the four other item views, on the same boss page
		BufferedImage[] views = new BufferedImage[4];
		ItemView[] order = {ItemView.COMPACT, ItemView.GAUGE, ItemView.RINGS, ItemView.DELVE_BARS};
		for (int i = 0; i < order.length; i++)
		{
			ExpectedDropsPanel v = panel(full, items, order[i]);
			showBoss(v, "Chambers of Xeric");
			views[i] = draw(v, 520);
		}
		save(views(views), dir, "luck-views.png");

		try (java.io.PrintWriter w = new java.io.PrintWriter(new File(dir, "needed-icons.txt"), "UTF-8"))
		{
			for (String n : NEEDED)
			{
				w.println(n + "\t" + wikiFile(n) + "\t" + (MISSING.contains(n) ? "MISSING" : "ok") + "\t" + WHERE.get(n));
			}
		}
		System.out.println("icons needed: " + NEEDED.size() + ", missing: " + MISSING.size());
		if (System.getenv("ONRATE_README_STRICT") != null && !MISSING.isEmpty())
		{
			throw new IllegalStateException("Missing item icons: " + MISSING);
		}
	}

	// ---------------------------------------------------------------- hero and gallery composition

	private static Font sans(int style, float size)
	{
		return new Font("Segoe UI", style, 1).deriveFont(style, size);
	}

	private static BufferedImage views(BufferedImage[] panels)
	{
		int gap = 24;
		int w = 0;
		int h = 0;
		for (BufferedImage b : panels)
		{
			w += b.getWidth();
			h = Math.max(h, b.getHeight());
		}
		w += gap * (panels.length + 1);
		String[] names = {"Compact table", "Luck gauge", "Chance rings", "Bars with tabs"};
		int head = 64;
		BufferedImage image = new BufferedImage(w, h + head + gap, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(18, 18, 18));
		g.fillRect(0, 0, w, image.getHeight());
		int x = gap;
		for (int i = 0; i < panels.length; i++)
		{
			g.setColor(new Color(235, 235, 235));
			g.setFont(sans(Font.BOLD, 28));
			g.drawString(names[i], x, 42);
			g.drawImage(panels[i], x, head, null);
			x += panels[i].getWidth() + gap;
		}
		g.dispose();
		return image;
	}

	private static BufferedImage hero(BufferedImage account, BufferedImage cox, BufferedImage hover)
	{
		int w = 1800;
		int panelsTop = 560;
		int h = panelsTop + Math.max(Math.max(account.getHeight(), cox.getHeight()), hover.getHeight() + 40) + 60;
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(17, 17, 17));
		g.fillRect(0, 0, w, h);
		g.setPaint(new GradientPaint(0, 0, new Color(48, 24, 20), w, 0, new Color(18, 30, 22)));
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
		g.fillRect(0, 0, w, 260);
		g.setComposite(AlphaComposite.SrcOver);

		centered(g, "FREE RUNELITE PLUGIN  ·  NO ACCOUNT  ·  NO WEB REQUESTS", sans(Font.BOLD, 26), new Color(165, 165, 165), w / 2, 84, 5f);
		centered(g, "Are you on rate?", sans(Font.BOLD, 150), Color.WHITE, w / 2, 230, -4f);
		centered(g, "Your real luck at every boss and raid, in one word.", sans(Font.PLAIN, 40), new Color(200, 200, 200), w / 2, 305, 0f);

		// the verdict bar
		int barX = 350;
		int barW = 1100;
		float[] stops = {0f, 0.25f, 0.5f, 0.75f, 1f};
		Color[] colours = {new Color(220, 60, 40), new Color(235, 140, 30), new Color(235, 220, 40), new Color(150, 215, 60), new Color(70, 215, 90)};
		g.setPaint(new LinearGradientPaint(barX, 0, barX + barW, 0, stops, colours));
		g.fillRoundRect(barX, 346, barW, 14, 14, 14);
		g.setFont(sans(Font.BOLD, 22));
		String[] labels = {"Extremely dry", "Dry", "On rate", "Spooned", "Extremely spooned"};
		float[] at = {0.04f, 0.27f, 0.5f, 0.74f, 0.94f};
		Color[] lc = {colours[0], colours[1], Color.WHITE, colours[3], colours[4]};
		for (int i = 0; i < labels.length; i++)
		{
			g.setColor(lc[i]);
			FontMetrics fm = g.getFontMetrics();
			g.drawString(labels[i], barX + (int) (barW * at[i]) - fm.stringWidth(labels[i]) / 2, 392);
		}

		int pad = 40;
		int colW = (w - pad * 4) / 3;
		int x1 = pad;
		int x2 = x1 + account.getWidth() + 44;
		int x3 = x2 + cox.getWidth() + 44;
		column(g, x1, panelsTop, "YOUR WHOLE ACCOUNT", "One verdict, every boss driest first");
		column(g, x2, panelsTop, "EVERY BOSS AND RAID", "Got against expected, drop by drop");
		column(g, x3, panelsTop, "HOVER ANY DROP", "How lucky it was, in plain words");
		g.drawImage(account, x1, panelsTop, null);
		g.drawImage(cox, x2, panelsTop, null);
		g.drawImage(hover, x3, panelsTop, null);
		g.dispose();
		return image;
	}

	private static void column(Graphics2D g, int x, int top, String title, String sub)
	{
		g.setColor(Color.WHITE);
		g.setFont(sans(Font.BOLD, 30));
		g.drawString(title, x, top - 52);
		g.setColor(new Color(170, 170, 170));
		g.setFont(sans(Font.PLAIN, 24));
		g.drawString(sub, x, top - 20);
	}

	private static void centered(Graphics2D g, String text, Font font, Color colour, int cx, int baseline, float tracking)
	{
		g.setFont(font.deriveFont(java.util.Collections.singletonMap(java.awt.font.TextAttribute.TRACKING, tracking / font.getSize2D())));
		g.setColor(colour);
		FontMetrics fm = g.getFontMetrics();
		g.drawString(text, cx - fm.stringWidth(text) / 2, baseline);
	}
}
