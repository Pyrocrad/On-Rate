package com.expecteddrops.ui;

import com.expecteddrops.ExpectedDropsPlugin;
import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.data.BossDefinition;
import com.expecteddrops.data.DropDefinition;
import com.expecteddrops.stats.AccountStats;
import com.expecteddrops.stats.DropMath;
import com.expecteddrops.stats.ItemHighlight;
import com.expecteddrops.stats.RaidSettings;
import com.expecteddrops.stats.RowStats;
import com.expecteddrops.tracking.Tracker;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The account Items tab's Driest and Luckiest rows say "got / expected". The figure is only text: the
 * filters, the order and every luck value behind them are the ones the verdicts already use.
 */
public class ItemFigureTest
{
	private static RowStats row(double expected, int received)
	{
		return new RowStats("Item", "Item", expected / 1000, expected, received, false);
	}

	@Test
	public void ordinaryItemsShowGotAgainstTheAverage()
	{
		assertEquals("0 / 2.60", Texts.gotOfExpected(row(2.6, 0)));
		assertEquals("1 / 4.00", Texts.gotOfExpected(row(4.0, 1)));
		assertEquals("3 / 1.20", Texts.gotOfExpected(row(1.2, 3)));
		assertEquals("9 / 3.00", Texts.gotOfExpected(row(3.0, 9)));
		// the same rounding as the boss pages: two decimals under 10, one under 100, none from 100
		assertEquals("0 / 12.5", Texts.gotOfExpected(row(12.46, 0)));
		assertEquals("2 / 153", Texts.gotOfExpected(row(153.2, 2)));
		assertEquals("1,204 / 1530", Texts.gotOfExpected(row(1530.2, 1204)));
	}

	@Test
	public void petsShowZeroOrOneAgainstTheUncappedAverage()
	{
		// a pet counts once: got is 0 or 1, expected is still rate x kills (here 2 by now)
		assertEquals("0 / 2.00", Texts.gotOfExpected(row(2.0, 0).once(0)));
		assertEquals("1 / 0.19", Texts.gotOfExpected(row(0.19, 1).once(1)));
		// one more than the cap still counts as the cap
		assertEquals("1 / 2.00", Texts.gotOfExpected(row(2.0, 1).once(1)));
	}

	@Test
	public void cappedItemsShowCopiesThatCountAgainstTheAverage()
	{
		// Warm gloves stop at 3, the Royal Titans scroll row at 2
		assertEquals("1 / 2.00", Texts.gotOfExpected(row(2.0, 1).capped(3, 1)));
		assertEquals("3 / 2.00", Texts.gotOfExpected(row(2.0, 3).capped(3, 3)));
		assertEquals("2 / 0.80", Texts.gotOfExpected(row(0.8, 2).capped(2, 2)));
		RowStats all = row(2.0, 3).capped(3, 3);
		assertTrue(all.isComplete());
		// the figure never says "owed" and never exceeds the cap on the got side
		assertEquals(3, all.getReceived());
	}

	@Test
	public void theFigureIsTheNumbersTheLuckIsBuiltFrom()
	{
		for (RowStats r : new RowStats[]{row(2.6, 0), row(4.0, 1), row(1.2, 3), row(2.0, 0).once(0), row(2.0, 1).capped(3, 1)})
		{
			String[] parts = Texts.gotOfExpected(r).replace(",", "").split(" / ");
			assertEquals(r.getReceived(), Integer.parseInt(parts[0]));
			assertEquals(r.getExpected(), Double.parseDouble(parts[1]), 0.005);
		}
	}

	@Test
	public void verdictsAndLuckBehindTheRowsAreUnchanged()
	{
		// pinned from the maths as it stood before the figure was added
		assertEquals(0.074, row(2.6, 0).getLuck(), 0.001);
		assertEquals("Very dry", DropMath.verdict(row(2.6, 0).getLuck()));
		assertEquals("Very dry", DropMath.verdict(row(4.0, 1).getLuck()));
		assertEquals("Spooned", DropMath.verdict(row(1.2, 3).getLuck()));
		// 9 of 3.0 expected is about 99.6%
		assertEquals("Mega spooned", DropMath.verdict(row(3.0, 9).getLuck()));
		assertEquals("Dry", DropMath.verdict(row(2.0, 0).once(0).getLuck()));
		assertEquals(0.135, row(2.0, 0).once(0).getLuck(), 0.001);
		assertEquals("A bit dry", DropMath.verdict(row(2.0, 1).capped(3, 1).getLuck()));
		assertEquals(0.406, row(2.0, 1).capped(3, 1).getLuck(), 0.001);
	}

	/** A deterministic account: every boss in the catalog synced with seeded random drops. */
	private static AccountStats account() throws Exception
	{
		BossCatalog catalog;
		try (Reader reader = new InputStreamReader(ExpectedDropsPlugin.class.getResourceAsStream("bosses.json"), StandardCharsets.UTF_8))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		Tracker tracker = new Tracker(catalog);
		RaidSettings raids = raid -> 0.03;
		tracker.setSettings(raids);
		Random random = new Random(11);
		for (BossDefinition boss : catalog.getBosses())
		{
			if (boss.getKcNames().isEmpty() || boss.getClogPages().isEmpty())
			{
				continue;
			}
			int kills = 50 + random.nextInt(600);
			List<String> counters = new ArrayList<>();
			for (String name : boss.getKcNames())
			{
				counters.add(name + " kills: " + kills);
			}
			Map<String, Integer> items = new LinkedHashMap<>();
			for (DropDefinition drop : boss.getDrops())
			{
				for (String name : drop.getAllNames())
				{
					items.put(name, random.nextInt(10) < 3 ? 1 + random.nextInt(2) : 0);
				}
			}
			tracker.onClogPage(boss.getClogPages().get(0), counters, items);
		}
		return tracker.accountStats(raids);
	}

	@Test
	public void theDriestAndLuckiestListsKeepTheirFiltersAndOrder() throws Exception
	{
		AccountStats account = account();
		for (int mode : new int[]{ExpectedDropsPanel.TOP_DRIEST, ExpectedDropsPanel.TOP_LUCKIEST})
		{
			boolean lucky = mode == ExpectedDropsPanel.TOP_LUCKIEST;
			// the rule the tab has always had, written out again here
			List<ItemHighlight> expected = new ArrayList<>();
			for (ItemHighlight h : account.getItems())
			{
				RowStats r = h.getRow();
				if (Double.isNaN(r.getLuck()))
				{
					continue;
				}
				if (lucky ? r.getReceived() > 0 && r.getLuck() > 0.5
					: r.getExpected() >= 1.0 && !r.isComplete() && r.getLuck() < 0.5)
				{
					expected.add(h);
				}
			}
			Comparator<ItemHighlight> byLuck = Comparator.comparingDouble(h -> h.getRow().getLuck());
			expected.sort(lucky ? byLuck.reversed() : byLuck);
			List<ItemHighlight> shown = ExpectedDropsPanel.topItems(account, mode);
			assertFalse("some rows to compare in mode " + mode, shown.isEmpty());
			assertEquals(15, ExpectedDropsPanel.TOP_ITEMS);
			assertEquals(Math.min(15, expected.size()), shown.size());
			// the seeded account has enough items for the limit to matter
			assertTrue("items in mode " + mode + ": " + expected.size(), expected.size() > 10);
			// every item clearly inside the top 15 is listed (only equal-luck items at the cut-off may differ)
			if (expected.size() > 15)
			{
				double cutoff = expected.get(14).getRow().getLuck();
				for (ItemHighlight h : expected)
				{
					boolean inside = lucky ? h.getRow().getLuck() > cutoff : h.getRow().getLuck() < cutoff;
					if (inside)
					{
						boolean listed = false;
						for (ItemHighlight s : shown)
						{
							listed |= s.getRow().getLabel().equals(h.getRow().getLabel()) && java.util.Objects.equals(s.getBoss(), h.getBoss());
						}
						assertTrue(h.getRow().getLabel() + " belongs in the top 15", listed);
					}
				}
			}
			for (int i = 0; i < shown.size(); i++)
			{
				// the same luck at every place in the list (equal-luck rows may swap places, nothing else can)
				assertEquals("luck of row " + i, expected.get(i).getRow().getLuck(), shown.get(i).getRow().getLuck(), 0);
			}
			// the order never turns around: driest climbs in luck, luckiest falls
			for (int i = 1; i < shown.size(); i++)
			{
				double before = shown.get(i - 1).getRow().getLuck();
				double after = shown.get(i).getRow().getLuck();
				assertTrue(lucky ? before >= after : before <= after);
			}
			// every listed row can be written as a figure from its own numbers
			for (ItemHighlight h : shown)
			{
				RowStats r = h.getRow();
				assertEquals(String.format("%,d / %s", r.getReceived(), Texts.formatExpected(r.getExpected())), Texts.gotOfExpected(r));
				// a dry row is below average and a lucky one above: the dot's side, whatever the figure says
				assertTrue(lucky ? r.getLuck() > 0.5 : r.getLuck() < 0.5);
			}
		}
	}

	private static ItemHighlight item(String name, double expected, int received)
	{
		return new ItemHighlight("Boss", new RowStats(name, name, expected / 1000, expected, received, false));
	}

	private static ItemHighlight itemOf(RowStats row, boolean pet)
	{
		return new ItemHighlight("Boss", row, pet);
	}

	private static AccountStats accountOf(List<ItemHighlight> items)
	{
		AccountStats account = new AccountStats(row(1, 0), new ArrayList<>(), new LinkedHashMap<>());
		account.setItems(items);
		return account;
	}

	private static List<String> labels(List<ItemHighlight> list)
	{
		List<String> names = new ArrayList<>();
		for (ItemHighlight h : list)
		{
			names.add(h.getRow().getLabel());
		}
		return names;
	}

	@Test
	public void fifteenDriestAndLuckiestInExactOrder()
	{
		List<ItemHighlight> items = new ArrayList<>();
		// 20 dry items, none got: the more expected, the drier (D20 driest)
		for (int i = 1; i <= 20; i++)
		{
			items.add(item(String.format("D%02d", i), 1.0 + i * 0.5, 0));
		}
		// 20 lucky items, one got each: the less expected, the luckier (L01 luckiest). Up to 0.6 expected a
		// single drop is still above average; past about 0.69 it would no longer count as lucky.
		for (int i = 1; i <= 20; i++)
		{
			items.add(item(String.format("L%02d", i), 0.03 * i, 1));
		}
		// left out of both lists: under 1 expected, nothing got and nothing to compare yet
		items.add(item("TooFew", 0.8, 0));
		// a pet you have and a capped item you completed are never "driest"
		items.add(itemOf(row(3.0, 1).once(1), true));
		items.add(itemOf(row(6.0, 3).capped(3, 3), false));
		AccountStats account = accountOf(items);

		List<String> driest = labels(ExpectedDropsPanel.topItems(account, ExpectedDropsPanel.TOP_DRIEST));
		List<String> expectedDriest = new ArrayList<>();
		for (int i = 20; i >= 6; i--)
		{
			expectedDriest.add(String.format("D%02d", i));
		}
		assertEquals(expectedDriest, driest);
		assertEquals(15, driest.size());
		assertFalse(driest.contains("TooFew"));

		List<String> luckiest = labels(ExpectedDropsPanel.topItems(account, ExpectedDropsPanel.TOP_LUCKIEST));
		assertEquals(15, luckiest.size());
		// the luckiest first, in order: L01 .. L15 (the pet and the completed capped item rank below them)
		for (int i = 0; i < 15; i++)
		{
			assertEquals(String.format("L%02d", i + 1), luckiest.get(i));
		}
		assertFalse(luckiest.contains("D20"));
	}

	@Test
	public void cappedAndPetRowsKeepTheirPlaceAndNumbers()
	{
		// Warm gloves 1 of 3, expecting 6: dry and not complete, so it is listed with the figure "1 / 6.00"
		RowStats gloves = new RowStats("Warm gloves", "Warm gloves", 0.006, 6.0, 1, false).capped(3, 1);
		// a pet you have, 1 of 0.19 expected: lucky, listed on Luckiest as "1 / 0.19"
		RowStats pet = new RowStats("Pet", "Pet", 0.00019, 0.19, 1, false).once(1);
		AccountStats account = accountOf(java.util.Arrays.asList(itemOf(gloves, false), itemOf(pet, true)));
		List<ItemHighlight> driest = ExpectedDropsPanel.topItems(account, ExpectedDropsPanel.TOP_DRIEST);
		List<ItemHighlight> luckiest = ExpectedDropsPanel.topItems(account, ExpectedDropsPanel.TOP_LUCKIEST);
		assertEquals(java.util.Collections.singletonList("Warm gloves"), labels(driest));
		assertEquals("1 / 6.00", Texts.gotOfExpected(driest.get(0).getRow()));
		assertEquals(java.util.Collections.singletonList("Pet"), labels(luckiest));
		assertEquals("1 / 0.19", Texts.gotOfExpected(luckiest.get(0).getRow()));
		// the pets list is its own list and keeps its own limit of 5 + 5
		assertEquals(java.util.Collections.singletonList("Pet"),
			labels(ExpectedDropsPanel.topItems(account, ExpectedDropsPanel.TOP_PETS)));
	}

	private static void layoutAll(java.awt.Component c)
	{
		if (c instanceof java.awt.Container)
		{
			// a parent gives its children their sizes, so it goes first
			c.invalidate();
			((java.awt.Container) c).doLayout();
			for (java.awt.Component child : ((java.awt.Container) c).getComponents())
			{
				layoutAll(child);
			}
		}
	}

	private static void strips(java.awt.Component c, List<LuckStrip> out)
	{
		if (c instanceof LuckStrip)
		{
			out.add((LuckStrip) c);
		}
		if (c instanceof java.awt.Container)
		{
			for (java.awt.Component child : ((java.awt.Container) c).getComponents())
			{
				strips(child, out);
			}
		}
	}

	private static void set(Object target, String field, Object value) throws Exception
	{
		java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}

	/** The account panel with the Items tab (or the Bosses tab) open, laid out at the panel's 225px. */
	private static ExpectedDropsPanel laidOut(Tracker tracker, int tab, int mode) throws Exception
	{
		RaidSettings raids = raid -> 0.03;
		ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker, null, () -> raids, s -> -1, () -> { }, () -> false, () -> { },
			s -> 1_000_000L, () -> new ViewOptions(ItemView.TARGET_BARS, true, false, false, false, true));
		set(panel, "accountTab", tab);
		set(panel, "topMode", mode);
		panel.showBoss(ExpectedDropsPanel.ACCOUNT);
		panel.refresh();
		panel.setSize(225, 4000);
		for (int i = 0; i < 4; i++)
		{
			layoutAll(panel);
		}
		return panel;
	}

	private static Tracker seededTracker() throws Exception
	{
		BossCatalog catalog;
		try (Reader reader = new InputStreamReader(ExpectedDropsPlugin.class.getResourceAsStream("bosses.json"), StandardCharsets.UTF_8))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		Tracker tracker = new Tracker(catalog);
		tracker.setSettings(raid -> 0.03);
		Random random = new Random(11);
		for (BossDefinition boss : catalog.getBosses())
		{
			if (boss.getKcNames().isEmpty() || boss.getClogPages().isEmpty())
			{
				continue;
			}
			int kills = 50 + random.nextInt(600);
			List<String> counters = new ArrayList<>();
			for (String name : boss.getKcNames())
			{
				counters.add(name + " kills: " + kills);
			}
			Map<String, Integer> items = new LinkedHashMap<>();
			for (DropDefinition drop : boss.getDrops())
			{
				for (String name : drop.getAllNames())
				{
					items.put(name, random.nextInt(10) < 3 ? 1 + random.nextInt(2) : 0);
				}
			}
			tracker.onClogPage(boss.getClogPages().get(0), counters, items);
		}
		return tracker;
	}

	@Test
	public void theDotsLineUpAndTheRowsAreNoTallerWithTheFigure() throws Exception
	{
		Tracker tracker = seededTracker();
		for (int mode : new int[]{ExpectedDropsPanel.TOP_DRIEST, ExpectedDropsPanel.TOP_LUCKIEST})
		{
			ExpectedDropsPanel panel = laidOut(tracker, 1, mode);
			List<LuckStrip> strips = new ArrayList<>();
			strips(panel, strips);
			assertEquals(ExpectedDropsPanel.TOP_ITEMS, strips.size());
			int width = strips.get(0).getWidth();
			int x = javax.swing.SwingUtilities.convertPoint(strips.get(0), 0, 0, panel).x;
			assertTrue("a track wide enough to read: " + width, width >= 110);
			for (LuckStrip strip : strips)
			{
				// one track length and one starting point on every row: dots are comparable down the list
				assertEquals(width, strip.getWidth());
				assertEquals(x, javax.swing.SwingUtilities.convertPoint(strip, 0, 0, panel).x);
			}
		}
		// the same kind of row without a figure (the Bosses list) is no shorter: the figure shares the strip's line
		ExpectedDropsPanel items = laidOut(tracker, 1, ExpectedDropsPanel.TOP_DRIEST);
		ExpectedDropsPanel bosses = laidOut(tracker, 0, ExpectedDropsPanel.TOP_DRIEST);
		List<LuckStrip> itemStrips = new ArrayList<>();
		List<LuckStrip> bossStrips = new ArrayList<>();
		strips(items, itemStrips);
		strips(bosses, bossStrips);
		int itemRow = itemStrips.get(0).getParent().getParent().getParent().getHeight();
		java.awt.Container bossRowParent = bossStrips.get(0).getParent().getParent();
		assertEquals(bossRowParent.getHeight(), itemRow);
	}
}
