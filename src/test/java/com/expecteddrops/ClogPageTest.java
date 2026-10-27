package com.expecteddrops;

import com.expecteddrops.tracking.ClogPage;
import com.expecteddrops.tracking.ClogPage.Item;
import com.expecteddrops.tracking.ClogPage.Problem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Reading a collection log page from plain values, no client needed. */
public class ClogPageTest
{
	private static final int OWNED = ClogPage.OBTAINED_OPACITY;
	private static final int FADED = 175;

	private static List<Item> vorkath()
	{
		return Arrays.asList(new Item("Vorkath's head", 7, OWNED), new Item("Draconic visage", 1, OWNED),
			new Item("Skeletal visage", 0, FADED), new Item("Vorki", 1, OWNED));
	}

	@Test
	public void normalPageStripsTagsAndReadsOwnedItems()
	{
		ClogPage.Result r = ClogPage.parse("<col=ff9040>Vorkath</col>",
			Arrays.asList("<col=ffffff>Vorkath kills: <col=ff9040>350</col>"), vorkath());
		assertNull(r.getProblem());
		assertEquals("Vorkath", r.getTitle());
		assertEquals(Collections.singletonList("Vorkath kills: 350"), r.getCounters());
		assertEquals(7, (int) r.getItems().get("Vorkath's head"));
		assertEquals(1, (int) r.getItems().get("Vorki"));
		assertFalse(r.isAllFaded());
	}

	@Test
	public void fadedItemsAreNotObtained()
	{
		ClogPage.Result r = ClogPage.parse("Vorkath", Collections.emptyList(), vorkath());
		assertEquals(0, (int) r.getItems().get("Skeletal visage"));
		// the quantity of a faded item is not trusted
		ClogPage.Result faded = ClogPage.parse("Vorkath", Collections.emptyList(),
			Collections.singletonList(new Item("Vorki", 5, FADED)));
		assertEquals(0, (int) faded.getItems().get("Vorki"));
	}

	@Test
	public void stacksKeepTheirQuantityAndOwnedItemsCountAtLeastOne()
	{
		ClogPage.Result r = ClogPage.parse("Zulrah", Collections.emptyList(), Arrays.asList(
			new Item("Zulrah's scales", 250, OWNED), new Item("Tanzanite fang", 0, OWNED)));
		assertEquals(250, (int) r.getItems().get("Zulrah's scales"));
		assertEquals(1, (int) r.getItems().get("Tanzanite fang"));
	}

	@Test
	public void emptyPageHasNoItems()
	{
		assertEquals(Problem.NO_ITEMS, ClogPage.parse("Vorkath", Collections.emptyList(), new ArrayList<>()).getProblem());
		assertEquals(Problem.NO_ITEMS, ClogPage.parse("Vorkath", Collections.emptyList(), null).getProblem());
		// items without a name can't be matched either
		assertEquals(Problem.NO_ITEMS, ClogPage.parse("Vorkath", Collections.emptyList(),
			Collections.singletonList(new Item("", 1, OWNED))).getProblem());
	}

	@Test
	public void missingOrEmptyHeaderHasNoTitle()
	{
		assertEquals(Problem.NO_TITLE, ClogPage.parse(null, Collections.emptyList(), vorkath()).getProblem());
		assertEquals(Problem.NO_TITLE, ClogPage.parse("", Collections.emptyList(), vorkath()).getProblem());
		assertEquals(Problem.NO_TITLE, ClogPage.parse("<col=ff9040></col>", Collections.emptyList(), vorkath()).getProblem());
	}

	@Test
	public void aTitleThatIsACounterMeansTheHeaderChanged()
	{
		assertEquals(Problem.TITLE_IS_A_COUNTER,
			ClogPage.parse("Vorkath kills: 1,234", Collections.emptyList(), vorkath()).getProblem());
		assertEquals(Problem.TITLE_IS_A_COUNTER,
			ClogPage.parse("<col=ffffff>Vorkath kills: <col=ff9040>350</col>", Collections.emptyList(), vorkath()).getProblem());
		// a real title with no number after a colon is fine
		assertNull(ClogPage.parse("Tombs of Amascut", Collections.emptyList(), vorkath()).getProblem());
	}

	@Test
	public void combinedPageKeepsEveryCounterInOrder()
	{
		ClogPage.Result r = ClogPage.parse("Dagannoth Kings", Arrays.asList(
			"Dagannoth Rex kills: 436", "", null, "Dagannoth Prime kills: 10", "Dagannoth Supreme kills: 9"),
			Arrays.asList(new Item("Berserker ring", 2, OWNED), new Item("Seers ring", 1, FADED)));
		assertNull(r.getProblem());
		assertEquals(Arrays.asList("Dagannoth Rex kills: 436", "Dagannoth Prime kills: 10", "Dagannoth Supreme kills: 9"),
			r.getCounters());
		assertEquals(Arrays.asList("Berserker ring", "Seers ring"), new ArrayList<>(r.getItems().keySet()));
	}

	@Test
	public void allFadedOnlyWhenEveryItemIsMissing()
	{
		assertTrue(ClogPage.parse("Vorkath", Collections.emptyList(), Arrays.asList(
			new Item("Vorkath's head", 0, FADED), new Item("Vorki", 0, FADED))).isAllFaded());
		assertFalse(ClogPage.parse("Vorkath", Collections.emptyList(), vorkath()).isAllFaded());
		assertFalse(ClogPage.parse("Vorkath", Collections.emptyList(), new ArrayList<>()).isAllFaded());
	}
}
