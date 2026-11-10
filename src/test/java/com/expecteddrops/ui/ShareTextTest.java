package com.expecteddrops.ui;

import com.onrate.engine.math.Verdict;

import com.onrate.engine.stats.AccountStats;
import com.onrate.engine.stats.ItemHighlight;
import com.onrate.engine.stats.RowStats;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The text "Copy my luck" puts on the clipboard. */
public class ShareTextTest
{
	private static RowStats row(String label, double expected, int received)
	{
		return new RowStats(label, null, 0.01, expected, received, false);
	}

	private static AccountStats account(RowStats total, RowStats... bossRows)
	{
		return new AccountStats(total, Arrays.asList(bossRows), new HashMap<>());
	}

	@Test
	public void nothingToShareBeforeABossIsLoaded()
	{
		assertNull(ShareText.account(null, true));
		assertNull(ShareText.account(account(row("Any", 0, 0)), true));
	}

	@Test
	public void firstLineUsesThePanelsWordsAndNumbers()
	{
		RowStats total = row("Any", 87.8, 80);
		String text = ShareText.account(account(total, row("Zulrah", 2, 1)), false);
		String first = text.split("\\R")[0];
		assertTrue(first, first.startsWith("On Rate: " + Verdict.labelOf(total.getLuck()) + " · 80 uniques, 87.8 expected"));
		assertTrue(first, first.contains(Texts.luckShort(total.getShares(), 80)));
	}

	@Test
	public void singularForOneUniqueAndOneBoss()
	{
		String text = ShareText.account(account(row("Any", 1.5, 1), row("Zulrah", 1.5, 1)), false);
		assertTrue(text, text.contains("1 unique, 1.50 expected"));
		assertTrue(text, text.endsWith("1 boss"));
	}

	@Test
	public void gpValueLineOnlyWhenTheSettingIsOnAndThereIsAValue()
	{
		AccountStats a = account(row("Any", 10, 12), row("Zulrah", 10, 12));
		a.setValue(1_230_000_000d, 4_000_000_000L, 0.995);
		String on = ShareText.account(a, true);
		assertTrue(on, on.contains("GP value: Extremely spooned · 4.00b got, 1.23b expected"));
		assertFalse(ShareText.account(a, false).contains("GP value"));

		AccountStats none = account(row("Any", 10, 12), row("Zulrah", 10, 12));
		assertFalse(ShareText.account(none, true).contains("GP value"));
	}

	@Test
	public void countsBosses()
	{
		String text = ShareText.account(account(row("Any", 5, 5), row("A", 1, 1), row("B", 1, 1), row("C", 1, 1)), false);
		assertTrue(text, text.endsWith("3 bosses"));
	}

	@Test
	public void luckiestItemOnlyWhenItIsPastSpooned()
	{
		RowStats bow = row("Twisted bow", 0.21, 2);
		RowStats dry = row("Dexterous prayer scroll", 1.49, 0);
		assertTrue(bow.getLuck() > 0.9);

		AccountStats a = account(row("Any", 10, 10), row("Chambers of Xeric", 10, 10));
		a.setItems(Arrays.asList(new ItemHighlight("Chambers of Xeric", dry), new ItemHighlight("Chambers of Xeric", bow)));
		String text = ShareText.account(a, false);
		assertTrue(text, text.contains("Luckiest: Twisted bow (Chambers of Xeric), " + Verdict.labelOf(bow.getLuck())));
		assertFalse(text, text.contains("Dexterous"));

		// only dry items, or items you don't have: no brag
		AccountStats b = account(row("Any", 10, 10), row("Chambers of Xeric", 10, 10));
		b.setItems(Collections.singletonList(new ItemHighlight("Chambers of Xeric", dry)));
		assertFalse(ShareText.account(b, false).contains("Luckiest"));
	}

	@Test
	public void threeLinesAtMostForTheMostCommonCase()
	{
		AccountStats a = account(row("Any", 10, 12), row("Zulrah", 10, 12));
		a.setValue(1_000_000d, 2_000_000L, 0.9);
		assertEquals(3, ShareText.account(a, true).split("\\R").length);
	}
}
