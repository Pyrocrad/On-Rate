package com.expecteddrops.ui;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.stats.RowStats;
import com.onrate.engine.tracking.Tracker;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The line under the verdict takes turns with other true facts. */
public class NotableFactsTest
{
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new Gson());
		com.onrate.engine.data.LadlorChart.bundled(new Gson());
	}

	private static RowStats item(String name, double expected, int received)
	{
		return new RowStats(name, name, expected / 100, expected, received, false);
	}

	@Test
	public void aBossPageHasTheNotableLineFirstThenNeutralFacts()
	{
		List<String> facts = Texts.notableLines(Arrays.asList(item("Magic fang", 4.0, 1), item("Jar of swamp", 0.3, 0),
			item("Twisted bow", 1.0, 1)), null, Double.NaN, Double.NaN, "uniques");
		assertEquals(Arrays.asList(
			"Driest: Magic fang, drier than 91% of players.",
			"Twisted bow: 1 got, 1.00 expected.",
			"You have 2 of the 3 uniques here."), facts);
	}

	@Test
	public void allOfThemIsSaidPlainly()
	{
		List<String> facts = Texts.notableLines(Arrays.asList(item("Kodai insignia", 0.6, 1), item("Elder maul", 0.6, 1)),
			null, Double.NaN, Double.NaN, "purples");
		assertTrue(facts.contains("You have both purples here."));
		facts = Texts.notableLines(Arrays.asList(item("Kodai insignia", 0.6, 1), item("Elder maul", 0.6, 1),
			item("Twisted bow", 0.6, 2)), null, Double.NaN, Double.NaN, "purples");
		assertTrue(facts.contains("You have all 3 purples here."));
	}

	@Test
	public void theTwoVerdictsOnDifferentSidesAreExplained()
	{
		List<RowStats> none = Collections.emptyList();
		assertEquals(Collections.singletonList("Fewer uniques than expected, but more valuable ones."),
			Texts.notableLines(none, null, 0.2, 0.8, "uniques"));
		assertEquals(Collections.singletonList("More purples than expected, but less valuable ones."),
			Texts.notableLines(none, null, 0.8, 0.2, "purples"));
		// same side, or no value tile: nothing to explain
		assertTrue(Texts.notableLines(none, null, 0.8, 0.9, "uniques").isEmpty());
		assertTrue(Texts.notableLines(none, null, 0.2, Double.NaN, "uniques").isEmpty());
	}

	@Test
	public void theAccountSaysHowTheBossesSplitAndWhichGaveTheMost()
	{
		List<RowStats> bosses = Arrays.asList(item("Dagannoth Kings", 6.1, 1), item("General Graardor", 4.0, 4),
			item("Hespori", 1.0, 3), item("Scurrius", 0.2, 0));
		List<String> facts = Texts.notableLines(Collections.emptyList(), bosses, Double.NaN, Double.NaN, "uniques");
		// Scurrius has too little expected to count
		assertEquals(Arrays.asList(
			"Of 3 bosses: 1 drier than average, 1 on rate, 1 luckier.",
			"Most uniques: General Graardor, 4."), facts);
	}

	@Test
	public void tooLittleDataGivesNoFacts()
	{
		// a quarter of a unique expected and none got: not "close to expected", not a split
		assertTrue(Texts.notableLines(Collections.singletonList(item("Jar of swamp", 0.25, 0)), null, Double.NaN, Double.NaN,
			"uniques").isEmpty());
		assertTrue(Texts.notableLines(Collections.emptyList(), Collections.singletonList(item("Vorkath", 3.0, 3)),
			Double.NaN, Double.NaN, "uniques").stream().noneMatch(f -> f.startsWith("Of ")));
	}

	@Test
	public void theRestOfTheLogAsKillsAndHours()
	{
		// the pet still missing: both figures (1,520 kills at 2.5 minutes a kill is about 63 hours)
		assertEquals("Rest of the log, 50% chance: 1,520 more kills (~63 h) with the pet, 1,010 (~42 h) without.",
			Texts.logLeftLine(1520, 1010, true, 150, "kills", null));
		assertEquals("Rest of the log, 50% chance: 1,520 more kills with the pet, 1,010 without.",
			Texts.logLeftLine(1520, 1010, true, Double.NaN, "kills", null));
		// the pet in the log already: one figure
		assertEquals("Rest of the log: 50% chance within 40 more raids (~9.3 h).", Texts.logLeftLine(40, 40, false, 840, "raids", null));
		// only the pet left: named
		assertEquals("Last item for the log, Vorki: 50% chance within 2,080 more kills.",
			Texts.logLeftLine(2080, 0, true, Double.NaN, "kills", "Vorki"));
		// nothing missing, or it can't be done at these chances
		assertEquals(null, Texts.logLeftLine(0, 0, false, 150, "kills", null));
		assertEquals(null, Texts.logLeftLine(com.onrate.engine.stats.LogForecast.NEVER, -1, true, 150, "kills", null));
	}

	@Test
	public void theAveragePaceOfUniques()
	{
		assertEquals("On average one unique every 82 kills (~3.4 h).", Texts.averagePaceLine(1 / 82.0, 150, "kills", "unique"));
		assertEquals("On average one purple every 4.5 raids (~1.1 h).", Texts.averagePaceLine(1 / 4.5, 900, "raids", "purple"));
		assertEquals("On average one unique every 82 kills.", Texts.averagePaceLine(1 / 82.0, Double.NaN, "kills", "unique"));
		assertEquals(null, Texts.averagePaceLine(0, 150, "kills", "unique"));
	}

	@Test
	public void shortTimesAreInMinutes()
	{
		assertEquals(" (~40 min)", Texts.aboutTime(2400));
		assertEquals(" (~1 min)", Texts.aboutTime(5));
		assertEquals("", Texts.aboutTime(Double.NaN));
	}

	@Test
	public void aPagesOwnFactsComeAfterTheNotableLine()
	{
		List<String> facts = Texts.notableLines(Collections.singletonList(item("Magic fang", 4.0, 1)), null, Double.NaN, Double.NaN,
			"uniques", Collections.singletonList("Rest of the log: 50% chance within 1,520 more kills."));
		assertEquals("Driest: Magic fang, drier than 91% of players.", facts.get(0));
		assertEquals("Rest of the log: 50% chance within 1,520 more kills.", facts.get(1));
	}

	@Test
	public void noFactSaysADropIsDueOrComing()
	{
		List<String> facts = Texts.notableLines(Arrays.asList(item("Magic fang", 4.0, 0), item("Twisted bow", 1.0, 1)),
			Arrays.asList(item("Vorkath", 5.0, 1), item("Zulrah", 2.0, 2)), 0.2, 0.8, "uniques");
		for (String f : facts)
		{
			String lower = f.toLowerCase(java.util.Locale.ROOT);
			for (String word : new String[]{"due", "soon", "next", "keep going", "overdue"})
			{
				assertFalse(f, lower.contains(word));
			}
		}
	}

	@Test
	public void theFactMovesOnWhenThePageOpensOrAKillComesInNotOnARedraw() throws Exception
	{
		int[] turns = new int[6];
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				RaidSettings raids = raid -> 0.03;
				ExpectedDropsPanel p = new ExpectedDropsPanel(new Tracker(Bundled.catalog(new Gson())), null, () -> raids,
					s -> -1, () -> { }, () -> false, () -> { }, name -> 0L,
					() -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
				Field select = ExpectedDropsPanel.class.getDeclaredField("bossSelect");
				select.setAccessible(true);
				@SuppressWarnings("unchecked")
				JComboBox<String> bosses = (JComboBox<String>) select.get(p);
				Method turn = ExpectedDropsPanel.class.getDeclaredMethod("factTurn", double.class);
				turn.setAccessible(true);
				bosses.setSelectedItem("Vorkath");
				turns[0] = (int) turn.invoke(p, 1.0);
				// a plain redraw: the same fact
				turns[1] = (int) turn.invoke(p, 1.0);
				// a kill changed the expected count: the next fact
				turns[2] = (int) turn.invoke(p, 1.01);
				bosses.setSelectedItem("Zulrah");
				turns[3] = (int) turn.invoke(p, 2.0);
				// back to Vorkath: opening the page again moves it on
				bosses.setSelectedItem("Vorkath");
				turns[4] = (int) turn.invoke(p, 1.01);
				turns[5] = (int) turn.invoke(p, 1.01);
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
		assertEquals(0, turns[0]);
		assertEquals(0, turns[1]);
		assertEquals(1, turns[2]);
		assertEquals(0, turns[3]);
		assertEquals(2, turns[4]);
		assertEquals(2, turns[5]);
	}
}
