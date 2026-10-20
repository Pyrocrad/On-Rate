package com.expecteddrops;

import com.expecteddrops.ui.ExpectedDropsPanel;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PanelTextTest
{
	@Test
	public void luckInPlainWords()
	{
		assertEquals("About 1 in 10 players is this dry (or drier)", ExpectedDropsPanel.plainLuck(0.1));
		assertEquals("About 1 in 4 players is this lucky (or luckier)", ExpectedDropsPanel.plainLuck(0.75));
		assertTrue(ExpectedDropsPanel.plainLuck(0.5).contains("average"));
		// 1/50 per kill: about 87% chance in the next 100 kills
		assertEquals(1 - Math.exp(-2.0), ExpectedDropsPanel.nextChance(1 / 50.0, 100), 1e-12);
	}

	@Test
	public void overlayNamesFit()
	{
		assertEquals("Vorkath's head", ExpectedDropsPlugin.shortName("Vorkath's head"));
		assertEquals("Deadeye prayer...", ExpectedDropsPlugin.shortName("Deadeye prayer scroll"));
		assertTrue(ExpectedDropsPlugin.shortName("Sanguinesti staff (uncharged)").length() <= 18);
	}

	@Test
	public void teamNoteSaysWhatWasSeen()
	{
		Map<Integer, Integer> seen = new TreeMap<>();
		assertTrue(ExpectedDropsPanel.teamSizeNote(seen, 50).contains("next kill"));
		seen.put(2, 10);
		seen.put(4, 10);
		String note = ExpectedDropsPanel.teamSizeNote(seen, 120);
		assertTrue(note, note.contains("20 of your kills") && note.contains("3.0 players") && note.contains("100 earlier kills"));
	}

	@Test
	public void namesAreJoinedWithAnd()
	{
		assertEquals("Vorkath", ExpectedDropsPanel.joinAnd(java.util.Collections.singletonList("Vorkath")));
		assertEquals("Vorkath and Zulrah", ExpectedDropsPanel.joinAnd(java.util.Arrays.asList("Vorkath", "Zulrah")));
		assertEquals("Vorkath, Zulrah and Cerberus", ExpectedDropsPanel.joinAnd(java.util.Arrays.asList("Vorkath", "Zulrah", "Cerberus")));
	}
}
