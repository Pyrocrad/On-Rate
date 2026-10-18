package com.expecteddrops;

import com.expecteddrops.stats.SharedPool;
import com.expecteddrops.ui.ExpectedDropsPanel;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PanelTextTest
{
	@Test
	public void nextPageIsTheOneMostItemsNeed()
	{
		SharedPool mask = new SharedPool("Virtus mask", "Virtus mask", 0, 0,
			Arrays.asList("Duke Sucellus", "Vardorvis"), Collections.singletonList("Vardorvis"));
		SharedPool ingot = new SharedPool("Chromium ingot", "Chromium ingot", 0, 0,
			Arrays.asList("Duke Sucellus", "Vardorvis", "The Whisperer"), Arrays.asList("The Whisperer", "Vardorvis"));
		SharedPool onyx = new SharedPool("Uncut onyx", "Uncut onyx", 0, 0,
			Arrays.asList("Zulrah", "Skotizo"), Collections.singletonList("Zulrah"));
		assertEquals("Vardorvis", ExpectedDropsPanel.nextPageToOpen(Arrays.asList(mask, ingot, onyx)));
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
}
