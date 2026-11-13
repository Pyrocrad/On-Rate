package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.onrate.engine.stats.AccountStats;
import com.onrate.engine.stats.ItemHighlight;
import com.onrate.engine.stats.RowStats;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.Test;

/** The Pets groups and Recent stop at a few entries; "show all" needs the whole lists, in the same order. */
public class CappedListNotesTest
{
	private static ItemHighlight pet(String name, double expected, boolean have)
	{
		RowStats row = new RowStats(name, name, 0.001, expected, have ? 1 : 0, false);
		return new ItemHighlight("Boss", have ? row.once(1) : row, true);
	}

	private static AccountStats account(List<ItemHighlight> items)
	{
		AccountStats account = new AccountStats(new RowStats("Total", "Total", 1, 0, 0, false), new ArrayList<>(), new LinkedHashMap<>());
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

	private static AccountStats sevenAndSix()
	{
		List<ItemHighlight> items = new ArrayList<>();
		for (int i = 1; i <= 7; i++)
		{
			items.add(pet(String.format("Have%d", i), i, true));
		}
		for (int i = 1; i <= 6; i++)
		{
			items.add(pet(String.format("Dry%d", i), i, false));
		}
		// not a pet, and a pet with nothing expected: neither is listed
		items.add(new ItemHighlight("Boss", new RowStats("Item", "Item", 0.001, 2, 0, false), false));
		items.add(pet("NoneExpected", 0, false));
		return account(items);
	}

	@Test
	public void petListHoldsEveryPetOfItsGroup()
	{
		AccountStats account = sevenAndSix();
		List<String> have = labels(ExpectedDropsPanel.petList(account, true));
		assertEquals(7, have.size());
		// the pets you have, the one got with the fewest kills expected first
		assertEquals("Have1", have.get(0));
		assertEquals("Have7", have.get(6));
		assertEquals(6, ExpectedDropsPanel.petList(account, false).size());
	}

	@Test
	public void topPetsStillShowFiveOfEachGroupAsTheStartOfTheFullLists()
	{
		AccountStats account = sevenAndSix();
		List<String> top = labels(ExpectedDropsPanel.topItems(account, ExpectedDropsPanel.TOP_PETS));
		assertEquals(2 * ExpectedDropsPanel.TOP_PETS_SHOWN, top.size());
		List<String> have = labels(ExpectedDropsPanel.petList(account, true));
		List<String> dry = labels(ExpectedDropsPanel.petList(account, false));
		assertEquals(have.subList(0, 5), top.subList(0, 5));
		assertEquals(dry.subList(0, 5), top.subList(5, 10));
		assertTrue(have.size() > 5 && dry.size() > 5);
	}

	@Test
	public void recentKeepsTenBeforeShowAll()
	{
		assertEquals(10, ExpectedDropsPanel.RECENT_SHOWN);
	}
}
