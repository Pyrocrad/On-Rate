package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.expecteddrops.ExpectedDropsPlugin;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import org.junit.Test;

/** The Items tab's Time page: the average time to get each item you don't have. */
public class TimePageTest
{
	// preload bundled data with a Gson, as the plugin does at startup (bundled() alone is empty until then)
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson());
		com.onrate.engine.data.LadlorChart.bundled(new com.google.gson.Gson());
	}

	private static final RaidSettings RAIDS = raid -> 0.03;

	private static Tracker tracker() throws Exception
	{
		BossCatalog catalog;
		try (Reader reader = com.onrate.engine.data.Bundled.reader("bosses.json"))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		Tracker tracker = new Tracker(catalog);
		tracker.setSettings(RAIDS);
		// one synced page, so the panel shows the overview and not its welcome guide
		tracker.onClogPage("Zulrah", java.util.Arrays.asList("Zulrah kills: 300"), java.util.Collections.singletonMap("Tanzanite fang", 0));
		return tracker;
	}

	private static ExpectedDropsPanel panel(Tracker tracker)
	{
		return new ExpectedDropsPanel(tracker, null, () -> RAIDS, s -> -1, () -> { }, name -> 0L,
			() -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
	}

	@SuppressWarnings("unchecked")
	private static String firstSort(ExpectedDropsPanel panel) throws Exception
	{
		Field f = ExpectedDropsPanel.class.getDeclaredField("sortSelect");
		f.setAccessible(true);
		return ((Enum<?>) ((JComboBox<Object>) f.get(panel)).getSelectedItem()).name();
	}

	/** True when some text contains the part (a note may carry an extra sentence, such as the Slayer one). */
	private static boolean anyContains(List<String> texts, String part)
	{
		for (String t : texts)
		{
			if (t.contains(part))
			{
				return true;
			}
		}
		return false;
	}

	private static void texts(Component c, List<String> out)
	{
		if (c instanceof JLabel && ((JLabel) c).getText() != null)
		{
			String text = ((JLabel) c).getText();
			out.add(text);
			// long names and notes wrap with <html> and <br>: also list the plain text a player reads
			if (text.startsWith("<html>"))
			{
				out.add(text.replaceAll("<br\\s*/?>", " ").replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim());
			}
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				texts(child, out);
			}
		}
	}

	/** Opens the account overview's Items tab on the given page, as a saved page would. */
	private static List<String> open(ExpectedDropsPanel panel, int page) throws Exception
	{
		List<String> saved = new ArrayList<>();
		panel.showBoss(ExpectedDropsPanel.ACCOUNT);
		panel.setAccountPageStore("1," + firstSort(panel) + "," + page, saved::add);
		List<String> out = new ArrayList<>();
		texts(panel, out);
		return out;
	}

	@Test
	public void theTimePageListsItemsWithAnEstimateBeforeYouHaveKills() throws Exception
	{
		List<String> texts = open(panel(tracker()), ExpectedDropsPanel.TOP_TIME);
		assertTrue(texts.toString(), texts.contains("QUICKEST"));
		// the fastest items come first, and each is an estimate (~) before you have timed kills
		boolean estimate = false;
		for (String t : texts)
		{
			estimate |= t.startsWith("~");
		}
		assertTrue(texts.toString(), estimate);
		// more than the five shown: the list says how many and offers all of them
		assertTrue(texts.toString(), texts.stream().anyMatch(t -> t.startsWith("top 5 of ") && t.endsWith("show all")));
	}

	@Test
	public void theTimePageIsOneOfTheFourSegments() throws Exception
	{
		List<String> texts = open(panel(tracker()), ExpectedDropsPanel.TOP_TIME);
		for (String segment : new String[]{"Items", "Pets", "Single drops", "Time"})
		{
			assertTrue(segment, texts.contains(segment));
		}
	}

	@Test
	public void theTimePageIsRememberedAndOlderSavedPagesStillOpen() throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker());
		List<String> saved = new ArrayList<>();
		panel.showBoss(ExpectedDropsPanel.ACCOUNT);
		panel.setAccountPageStore("1," + firstSort(panel) + "," + ExpectedDropsPanel.TOP_TIME, saved::add);
		// picking a page saves it; the Time page's number is accepted when it comes back
		List<String> out = new ArrayList<>();
		texts(panel, out);
		assertTrue(out.contains("QUICKEST"));
		ExpectedDropsPanel again = panel(tracker());
		List<String> old = open(again, ExpectedDropsPanel.TOP_DROPS);
		assertFalse(old.contains("QUICKEST"));
		assertEquals(4, ExpectedDropsPanel.TOP_TIME);
	}

	@Test
	public void theSavedTabIsNotReopenedButTheBossesTabIs() throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker());
		List<String> saved = new ArrayList<>();
		panel.showBoss(ExpectedDropsPanel.ACCOUNT);
		panel.setAccountPageStore("1," + firstSort(panel) + "," + ExpectedDropsPanel.TOP_TIME, saved::add);
		List<String> items = new ArrayList<>();
		texts(panel, items);
		assertTrue(items.contains("QUICKEST"));
		// what the plugin does right after: start on Bosses, whatever was saved
		panel.startOnBossesTab();
		List<String> bosses = new ArrayList<>();
		texts(panel, bosses);
		assertFalse(bosses.toString(), bosses.contains("QUICKEST"));
	}

	@Test
	public void itemsYouHaveAreLeftOut() throws Exception
	{
		Tracker tracker = tracker();
		// a Zulrah page with every unique in the log: nothing of Zulrah's is left to wait for
		tracker.onClogPage("Zulrah", java.util.Arrays.asList("Zulrah kills: 300"),
			java.util.Map.of("Tanzanite fang", 1, "Magic fang", 1, "Serpentine visage", 1, "Uncut onyx", 1,
				"Tanzanite mutagen", 1, "Magma mutagen", 1, "Jar of swamp", 1, "Pet snakeling", 1));
		List<com.onrate.engine.stats.ItemTime> all = com.onrate.engine.stats.ItemTimes.compute(tracker,
			com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson()), RAIDS);
		boolean any = false;
		for (com.onrate.engine.stats.ItemTime t : all)
		{
			if (t.getBoss().equals("Zulrah") && t.getLabel().equals("Tanzanite fang"))
			{
				any = true;
				assertTrue("a fang you have is marked as had", t.isHave());
			}
		}
		assertTrue(any);
	}

	private static JComboBox<?> viewBox(Component c)
	{
		if (c instanceof JComboBox && ((JComboBox<?>) c).getItemCount() == 4 && "Ladlor's chart".equals(String.valueOf(((JComboBox<?>) c).getItemAt(1))))
		{
			return (JComboBox<?>) c;
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				JComboBox<?> found = viewBox(child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** Opens the Time page and picks one of its lists from the dropdown. */
	private static List<String> pick(ExpectedDropsPanel panel, String view) throws Exception
	{
		open(panel, ExpectedDropsPanel.TOP_TIME);
		JComboBox<?> box = viewBox(panel);
		org.junit.Assert.assertNotNull("the Time page has a dropdown of lists", box);
		for (int i = 0; i < box.getItemCount(); i++)
		{
			if (box.getItemAt(i).toString().equals(view))
			{
				box.setSelectedIndex(i);
			}
		}
		List<String> out = new ArrayList<>();
		texts(panel, out);
		return out;
	}

	@Test
	public void theDropdownOffersTheLists() throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker());
		open(panel, ExpectedDropsPanel.TOP_TIME);
		JComboBox<?> box = viewBox(panel);
		List<String> options = new ArrayList<>();
		for (int i = 0; i < box.getItemCount(); i++)
		{
			options.add(box.getItemAt(i).toString());
		}
		assertEquals(java.util.Arrays.asList("Items", "Ladlor's chart", "Pets", "Raids"), options);
	}

	@Test
	public void theChartListIsInChartOrder() throws Exception
	{
		List<String> texts = pick(panel(tracker()), "Ladlor's chart");
		assertTrue(texts.toString(), texts.contains("IN CHART ORDER"));
		int first = texts.indexOf("Hallowfell");
		int later = texts.indexOf("Bow of Faerdhinen");
		assertTrue(texts.toString(), first >= 0 && later > first);
	}

	@Test
	public void everyRowSaysWhereItsTimeComesFrom() throws Exception
	{
		List<String> texts = pick(panel(tracker()), "Items");
		// a time from the wiki's rate says it with "~" and has no chip; the word "wiki" isn't on the rows
		assertTrue(texts.toString(), texts.stream().anyMatch(t -> t.startsWith("~")));
		assertFalse(texts.toString(), texts.contains("wiki"));
		assertFalse(texts.toString(), texts.contains("yours"));
		List<String> chart = pick(panel(tracker()), "Ladlor's chart");
		assertTrue(chart.toString(), chart.stream().anyMatch(t -> t.startsWith("~")));
		assertFalse(chart.toString(), chart.contains("wiki"));
	}

	@Test
	public void yourOwnTimesShowAsYours() throws Exception
	{
		Tracker tracker = tracker();
		for (int i = 0; i < 20; i++)
		{
			long at = 1_000_000 + i * 3_600_000L;
			tracker.onChatMessage("Your Mad Angel kill count is: <col=ff0000>" + (i + 1) + "</col>.", at);
			tracker.onChatMessage("Fight duration: <col=ff0000>1:00.00</col>.", at + 500);
		}
		List<String> chart = pick(panel(tracker), "Ladlor's chart");
		assertTrue(chart.toString(), chart.contains("yours"));
		// the chart row says its step and what is missing
		assertTrue(chart.toString(), chart.contains("Step 15 \u00b7 The Mad Angel"));
	}

	@Test
	public void theChartSaysHowMuchIsLeftInAll() throws Exception
	{
		List<String> chart = pick(panel(tracker()), "Ladlor's chart");
		assertTrue(chart.toString(), chart.stream().anyMatch(t -> t.contains("items left, about") && t.contains(" h")));
	}

	@Test
	public void petsHaveTheirOwnListAndItemsLeaveThemOut() throws Exception
	{
		java.util.List<com.onrate.engine.stats.ItemTime> all = com.onrate.engine.stats.ItemTimes.compute(tracker(),
			com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson()), RAIDS);
		String firstPet = null;
		String firstItem = null;
		double petAt = Double.MAX_VALUE;
		double itemAt = Double.MAX_VALUE;
		for (com.onrate.engine.stats.ItemTime t : all)
		{
			if (t.isHave())
			{
				continue;
			}
			if (t.isPet() && t.getSecondsPerDrop() < petAt)
			{
				petAt = t.getSecondsPerDrop();
				firstPet = t.getLabel();
			}
			if (!t.isPet() && t.getSecondsPerDrop() < itemAt)
			{
				itemAt = t.getSecondsPerDrop();
				firstItem = t.getLabel();
			}
		}
		List<String> pets = pick(panel(tracker()), "Pets");
		assertTrue(pets.toString(), pets.contains(firstPet));
		assertFalse(pets.toString(), pets.contains(firstItem));
		List<String> items = pick(panel(tracker()), "Items");
		assertTrue(items.toString(), items.contains(firstItem));
		assertFalse(items.toString(), items.contains(firstPet));
	}

	@Test
	public void theRaidListHasOnlyRaidItems() throws Exception
	{
		String firstRaid = null;
		double at = Double.MAX_VALUE;
		java.util.Set<String> raidLabels = new java.util.HashSet<>();
		for (com.onrate.engine.stats.ItemTime t : com.onrate.engine.stats.ItemTimes.compute(tracker(),
			com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson()), RAIDS))
		{
			if (t.isRaid() && !t.isPet() && !t.isHave())
			{
				raidLabels.add(t.getBoss());
				if (t.getSecondsPerDrop() < at)
				{
					at = t.getSecondsPerDrop();
					firstRaid = t.getLabel();
				}
			}
		}
		List<String> texts = pick(panel(tracker()), "Raids");
		assertTrue(texts.toString(), texts.contains(firstRaid));
		// the boss shown under each item is a raid
		for (String t : texts)
		{
			if (t.equals("Zulrah") || t.equals("Vorkath") || t.equals("Royal Titans"))
			{
				throw new AssertionError("a boss that isn't a raid is in the raid list: " + t);
			}
		}
		assertTrue(raidLabels.contains("Chambers of Xeric") || raidLabels.contains("Tombs of Amascut") || raidLabels.contains("Theatre of Blood"));
	}

	@Test
	public void anItemYouGetLeavesItsList() throws Exception
	{
		Tracker tracker = tracker();
		List<String> before = pick(panel(tracker), "Pets");
		com.onrate.engine.stats.ItemTime first = null;
		double at = Double.MAX_VALUE;
		for (com.onrate.engine.stats.ItemTime t : com.onrate.engine.stats.ItemTimes.compute(tracker,
			com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson()), RAIDS))
		{
			if (t.isPet() && !t.isHave() && t.getSecondsPerDrop() < at)
			{
				at = t.getSecondsPerDrop();
				first = t;
			}
		}
		assertTrue(before.contains(first.getLabel()));
		com.onrate.engine.data.BossDefinition boss = tracker.getCatalog().byName(first.getBoss());
		List<String> counters = new ArrayList<>();
		for (String name : boss.getKcNames())
		{
			counters.add(name + " kills: 50");
		}
		tracker.onClogPage(boss.getClogPages().get(0), counters, java.util.Collections.singletonMap(first.getIconItem(), 1));
		List<String> after = pick(panel(tracker), "Pets");
		assertFalse(after.toString(), after.contains(first.getLabel()));
	}

	@Test
	public void theTimePageSaysWhenSlayerBossesAreHidden() throws Exception
	{
		ExpectedDropsPanel low = panel(tracker());
		low.setSlayerLevel(() -> 80);
		List<String> texts = pick(low, "Items");
		assertTrue(texts.toString(), texts.stream().anyMatch(t -> t.contains("Bosses above your Slayer level (80) are hidden.")));
		// a high enough level hides nothing and says nothing
		ExpectedDropsPanel high = panel(tracker());
		high.setSlayerLevel(() -> 99);
		assertFalse(pick(high, "Items").stream().anyMatch(t -> t.contains("are hidden")));
		// unknown (not logged in): Slayer bosses are always left out, and the list says why
		ExpectedDropsPanel unknown = panel(tracker());
		assertTrue(pick(unknown, "Items").stream().anyMatch(t -> t.contains("Slayer bosses are hidden until On Rate knows your Slayer level")));
	}

	@Test
	public void slayerBossItemsAreLeftOutUntilYouHaveTheLevel() throws Exception
	{
		// Hellpuppy (Cerberus, Slayer 91) is among the quickest pets
		assertTrue(pick(panel(tracker()), "Pets").toString(), pick(panel(tracker()), "Pets").stream().noneMatch(t -> t.equals("Pet smoke devil")));
		ExpectedDropsPanel low = panel(tracker());
		low.setSlayerLevel(() -> 60);
		assertTrue(pick(low, "Pets").stream().noneMatch(t -> t.equals("Pet smoke devil")));
		ExpectedDropsPanel high = panel(tracker());
		high.setSlayerLevel(() -> 99);
		assertTrue(pick(high, "Pets").toString(), pick(high, "Pets").stream().anyMatch(t -> t.equals("Pet smoke devil")));
	}

	@Test
	public void theTimesNoticeIsTakenOnceAndNeverAgain() throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker());
		boolean[] seen = {false};
		int[] marked = {0};
		panel.setTimesNotice(() -> !seen[0], () ->
		{
			seen[0] = true;
			marked[0]++;
		});
		assertTrue(panel.takeTimesNotice());
		assertEquals(1, marked[0]);
		// shown already: opening the panel again, or any redraw, never asks twice
		assertFalse(panel.takeTimesNotice());
		assertFalse(panel.takeTimesNotice());
		assertEquals(1, marked[0]);
	}

	@Test
	public void noNoticeWhenNobodyAsksForIt() throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker());
		assertFalse(panel.takeTimesNotice());
		panel.setTimesNotice(() -> false, () -> { throw new AssertionError("marked without being shown"); });
		assertFalse(panel.takeTimesNotice());
	}

	@Test
	public void theNoticeIsACardInThePanelThatShowsOnceAndCloses() throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker());
		assertFalse(panel.isTimesNoticeVisible());
		boolean[] seen = {false};
		panel.setTimesNotice(() -> !seen[0], () -> seen[0] = true);
		// not due until the panel is on screen
		assertFalse(panel.isTimesNoticeVisible());
		panel.showTimesNoticeOnce();
		assertTrue(panel.isTimesNoticeVisible());
		// the card stays through redraws, and a second time on screen doesn't ask again
		panel.refresh();
		assertTrue(panel.isTimesNoticeVisible());
		panel.showTimesNoticeOnce();
		assertTrue(seen[0]);
		// "Got it" closes it, and it never comes back
		javax.swing.JButton gotIt = find(panel, "Got it");
		gotIt.doClick();
		assertFalse(panel.isTimesNoticeVisible());
		panel.showTimesNoticeOnce();
		assertFalse(panel.isTimesNoticeVisible());
	}

	@Test
	public void theNoticeIsNotTakenWhileThePanelIsNotOnScreenOrNoAccountIsLoaded() throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker());
		int[] marked = {0};
		panel.setTimesNotice(() -> marked[0] == 0, () -> marked[0]++);
		// a panel that is not on screen (as in tests) never takes the notice
		panel.showTimesNoticeWhenReady();
		assertEquals(0, marked[0]);
		assertFalse(panel.isTimesNoticeVisible());
		// nor does one still waiting for an account
		panel.setProfileState(ExpectedDropsPanel.ProfileState.LOADING);
		panel.showTimesNoticeWhenReady();
		assertEquals(0, marked[0]);
		assertFalse(panel.isTimesNoticeVisible());
	}

	@Test
	public void showMeOpensTheTimePage() throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker());
		panel.setTimesNotice(() -> true, () -> { });
		panel.showTimesNoticeOnce();
		find(panel, "Show me").doClick();
		assertFalse(panel.isTimesNoticeVisible());
		List<String> out = new ArrayList<>();
		texts(panel, out);
		assertTrue(out.toString(), out.stream().anyMatch(t -> t.contains("Quickest first")));
	}

	@Test
	public void showMeIsDisabledAndExplainedWhenTheItemsTabIsOff() throws Exception
	{
		ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker(), null, () -> RAIDS, s -> -1, () -> { }, name -> 0L,
			() -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, false));
		panel.setTimesNotice(() -> true, () -> { });
		panel.showTimesNoticeOnce();
		javax.swing.JButton show = find(panel, "Show me");
		assertFalse(show.isEnabled());
		assertTrue(show.getToolTipText(), show.getToolTipText().contains("Items tab"));
		assertTrue(panel.isTimesNoticeVisible());
	}

	@Test
	public void theNoticeSaysWhereTheTimePageIsAndAboutRaidDataTracker()
	{
		String text = String.join(" ", ExpectedDropsPanel.timesNoticeParagraphs());
		assertTrue(text.contains("Time</font> page"));
		assertTrue(text.contains("Raid Data Tracker"));
		assertTrue(text.contains("only shows once"));
		assertTrue(text.contains("purple chance by setup"));
		// the folder is chosen by the player and not kept: the notice must not say raids are read at login
		assertTrue(text.contains("each time RuneLite starts"));
		assertFalse(text.contains("when you log in"));
	}

	private static javax.swing.JButton find(Component c, String text)
	{
		if (c instanceof javax.swing.JButton && text.equals(((javax.swing.JButton) c).getText()))
		{
			return (javax.swing.JButton) c;
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				javax.swing.JButton found = find(child, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static void tooltips(Component c, List<String> out)
	{
		if (c instanceof javax.swing.JComponent && ((javax.swing.JComponent) c).getToolTipText() != null)
		{
			out.add(((javax.swing.JComponent) c).getToolTipText());
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				tooltips(child, out);
			}
		}
	}

	@Test
	public void anItemsHoverOnItsBossPageSaysHowLongOneTakes() throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker());
		panel.showBoss("Zulrah");
		List<String> tips = new ArrayList<>();
		tooltips(panel, tips);
		String fang = tips.stream().filter(t -> t.contains("Tanzanite fang") && t.contains("Time to get one")).findFirst().orElse(null);
		assertTrue(tips.toString(), fang != null);
		// wiki-based here (no timed kills): marked with ~ and says where it comes from
		assertTrue(fang, fang.contains("~"));
		assertTrue(fang, fang.contains("the OSRS Wiki's kill speed"));
	}

	@Test
	public void aWikiBasedTimeRowNamesItsGuideInTheHover() throws Exception
	{
		// before any timed kills every time on the page is the wiki's: each hover names the guide it comes from
		ExpectedDropsPanel panel = panel(tracker());
		open(panel, ExpectedDropsPanel.TOP_TIME);
		List<String> tips = new ArrayList<>();
		tooltips(panel, tips);
		List<String> withGuide = new ArrayList<>();
		for (String t : tips)
		{
			if (t.contains("Wiki guide"))
			{
				withGuide.add(t);
			}
		}
		assertFalse(tips.toString(), withGuide.isEmpty());
		for (String t : withGuide)
		{
			assertTrue(t, t.contains("Time based on"));
			assertTrue(t, t.contains("Right-click to open it on the wiki"));
			assertFalse(t, t.toLowerCase(java.util.Locale.ROOT).contains("recommended"));
		}
	}

	@Test
	public void aTimeRowAlwaysComesWithItsSource() throws Exception
	{
		// whichever way it is: every hover that has the row also says where the time comes from
		ExpectedDropsPanel panel = panel(tracker());
		panel.showBoss("Chaos Fanatic");
		List<String> tips = new ArrayList<>();
		tooltips(panel, tips);
		for (String t : tips)
		{
			assertEquals(t, t.contains("Time to get one") || t.contains("Time to get a unique"), t.contains("Time based on"));
		}
	}

	@Test
	public void theAnyUniqueHoverSaysHowLongAUniqueTakesAndIsQuickerThanAnyItem() throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker());
		panel.showBoss("Zulrah");
		List<String> tips = new ArrayList<>();
		tooltips(panel, tips);
		String any = tips.stream().filter(t -> t.contains("Any unique") && t.contains("Time to get a unique")).findFirst().orElse(null);
		assertTrue(tips.toString(), any != null);
		assertTrue(any, any.contains("the OSRS Wiki's kill speed"));
		// quicker than the quickest single item at that boss (Zulrah's fang is 25.6 h)
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("Time to get a unique[^~\\d]*~?([\\d.,]+) (h|min)").matcher(any.replaceAll("<[^>]*>", " "));
		assertTrue(any, m.find());
		double hours = Double.parseDouble(m.group(1).replace(",", "")) / ("min".equals(m.group(2)) ? 60 : 1);
		assertTrue(any, hours > 0 && hours < 25.6);
		// the single items say "one", not "a unique"
		assertTrue(tips.stream().noneMatch(t -> t.contains("Tanzanite fang") && t.contains("Time to get a unique")));
	}

	@Test
	public void theTimePageSaysKillSpeedNotRate() throws Exception
	{
		// "the wiki's rate" read as the drop rate; the wiki gives a kill speed (audit H3)
		ExpectedDropsPanel panel = panel(tracker());
		List<String> all = open(panel, ExpectedDropsPanel.TOP_TIME);
		tooltips(panel, all);
		String joined = String.join("\n", all);
		assertTrue(joined, joined.contains("kill speed"));
		assertFalse(joined, joined.contains("wiki's rate") || joined.contains("Wiki's rate") || joined.contains("Wiki rate:"));
	}

	@Test
	public void theTimeListIsSavedAsAFourthFieldAndComesBack() throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker());
		List<String> saved = new ArrayList<>();
		panel.showBoss(ExpectedDropsPanel.ACCOUNT);
		panel.setAccountPageStore("1," + firstSort(panel) + "," + ExpectedDropsPanel.TOP_TIME + ",PETS", saved::add);
		// restoring saves nothing, and the saved list is the one the page opens on
		assertTrue(saved.toString(), saved.isEmpty());
		List<String> out = new ArrayList<>();
		texts(panel, out);
		assertTrue(out.toString(), anyContains(out, "Pets you don't have, quickest first."));
		// an unknown list name keeps the usual first list
		ExpectedDropsPanel unknown = panel(tracker());
		unknown.showBoss(ExpectedDropsPanel.ACCOUNT);
		unknown.setAccountPageStore("1," + firstSort(unknown) + "," + ExpectedDropsPanel.TOP_TIME + ",NOPE", saved::add);
		List<String> items = new ArrayList<>();
		texts(unknown, items);
		assertTrue(items.toString(), items.contains("QUICKEST"));
		assertFalse(items.toString(), anyContains(items, "Pets you don't have, quickest first."));
	}
}
