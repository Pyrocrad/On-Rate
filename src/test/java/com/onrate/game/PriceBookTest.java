package com.onrate.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.data.ValueFormula;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import org.junit.Before;
import org.junit.Test;

/** Item ids and prices for the tracked items: found once by name, refreshed now and then, 0 when unknown. */
public class PriceBookTest
{
	private static final String BOW = "Twisted bow";
	private static final String HEAD = "Vorkath's head";

	private BossCatalog catalog;
	/** The game's items: id to {name, note id, placeholder id}. */
	private final Map<Integer, Object[]> items = new HashMap<>();
	private int itemCount;
	private int definitionsRead;
	private final Map<Integer, Long> prices = new HashMap<>();
	private int pricesRead;
	private Client client;
	private PriceBook book;

	@Before
	public void setUp()
	{
		catalog = Bundled.catalog(new Gson());
		client = Fakes.of(Client.class, new Fakes.Answers()
			.on("getItemCount", args -> itemCount)
			.on("getItemDefinition", args ->
			{
				definitionsRead++;
				Object[] item = items.get((Integer) args[0]);
				return item == null ? null : composition(item);
			}));
		book = new PriceBook(catalog, id ->
		{
			pricesRead++;
			Long price = prices.get(id);
			if (price == null)
			{
				throw new IllegalStateException("no price for " + id);
			}
			return price;
		});
	}

	private static ItemComposition composition(Object[] item)
	{
		return Fakes.of(ItemComposition.class, new Fakes.Answers()
			.on("getName", item[0]).on("getNote", item[1]).on("getPlaceholderTemplateId", item[2]));
	}

	private void item(int id, String name)
	{
		items.put(id, new Object[]{name, -1, -1});
		itemCount = Math.max(itemCount, id + 1);
	}

	@Test
	public void nothingIsKnownBeforeTheGameWasRead()
	{
		assertFalse(book.isResolved());
		assertEquals(-1, book.idOf(BOW));
		assertEquals(0, book.priceOf(BOW));
		assertEquals(0, book.priceOf(null));
		assertEquals(-1, book.idOf(null));
		// asking for prices before any id is known reads nothing and fails nothing
		book.refresh(true);
		assertEquals(0, pricesRead);
	}

	@Test
	public void itemsAreFoundByNameWhateverTheCaseAndPricedFromRuneLite()
	{
		item(20997, BOW);
		item(21907, HEAD.toUpperCase());
		item(5, "Bones");
		prices.put(20997, 1_500_000_000L);
		prices.put(21907, 0L);
		assertTrue(book.resolve(client));
		assertTrue(book.isResolved());
		assertEquals(20997, book.idOf(BOW));
		assertEquals(20997, book.idOf("twisted BOW"));
		assertEquals(21907, book.idOf(HEAD));
		assertEquals(1_500_000_000L, book.priceOf(BOW));
		// an untradeable item is worth 0, and an item On Rate doesn't track isn't looked up at all
		assertEquals(0, book.priceOf(HEAD));
		assertEquals(-1, book.idOf("Bones"));
		assertEquals(0, book.priceOf("Bones"));
		assertEquals(0, book.priceOf("No such item"));
	}

	@Test
	public void notedAndPlaceholderCopiesAndGapsInTheItemListAreSkipped()
	{
		// a noted copy and a bank placeholder come first, then a gap, an item without a name, and the real item
		items.put(10, new Object[]{BOW, 20997, -1});
		items.put(11, new Object[]{BOW, -1, 14401});
		items.put(13, new Object[]{null, -1, -1});
		item(20997, BOW);
		prices.put(20997, 7L);
		assertTrue(book.resolve(client));
		assertEquals(20997, book.idOf(BOW));
		assertEquals(7, book.priceOf(BOW));
	}

	@Test
	public void theFirstIdOfANameWinsAndOneSeenOnTheLogPageWinsOverTheList()
	{
		item(100, BOW);
		item(200, BOW);
		item(300, HEAD);
		book.learn(HEAD, 999);
		prices.put(100, 5L);
		prices.put(999, 6L);
		book.resolve(client);
		assertEquals(100, book.idOf(BOW));
		assertEquals(999, book.idOf(HEAD));
		assertEquals(6, book.priceOf(HEAD));
	}

	@Test
	public void theGameIsOnlyReadOnce()
	{
		item(20997, BOW);
		prices.put(20997, 5L);
		assertTrue(book.resolve(client));
		int read = definitionsRead;
		assertFalse("already done", book.resolve(client));
		assertEquals(read, definitionsRead);
	}

	@Test
	public void pricesStayAsTheyWereUntilTheyAreOldOrARefreshIsForced()
	{
		item(20997, BOW);
		prices.put(20997, 1_000L);
		book.resolve(client);
		assertEquals(1_000, book.priceOf(BOW));
		// the price moved, but the book was filled a moment ago: a kill doesn't read them all again
		prices.put(20997, 2_000L);
		int read = pricesRead;
		book.refresh(false);
		assertEquals(read, pricesRead);
		assertEquals(1_000, book.priceOf(BOW));
		book.refresh(true);
		assertEquals(2_000, book.priceOf(BOW));
	}

	@Test
	public void anItemWithoutAPriceLeavesTheOthersPricedAndKeepsItsLastPrice()
	{
		item(20997, BOW);
		item(21907, HEAD);
		prices.put(20997, 1_000L);
		prices.put(21907, 50L);
		book.resolve(client);
		// RuneLite has no price for the head any more (the lookup fails): the bow is still read, the head keeps 50
		prices.remove(21907);
		prices.put(20997, 3_000L);
		book.refresh(true);
		assertEquals(3_000, book.priceOf(BOW));
		assertEquals(50, book.priceOf(HEAD));
	}

	@Test
	public void aNegativePriceCountsAsNoPrice()
	{
		item(20997, BOW);
		prices.put(20997, -5L);
		book.resolve(client);
		assertEquals(0, book.priceOf(BOW));
	}

	@Test
	public void anUntradeableItemIsWorthWhatItsFormulaMakesOfTradedPrices()
	{
		// any untradeable the catalogue prices with a formula, and the tradeable items that formula needs
		String untradeable = null;
		ValueFormula formula = null;
		for (String name : catalog.getAllItemNames())
		{
			if (catalog.getValueFormula(name) != null)
			{
				untradeable = name;
				formula = catalog.getValueFormula(name);
				break;
			}
		}
		assertNotNull("the catalogue has an item priced by a formula", untradeable);
		int id = 1_000;
		item(id, untradeable);
		prices.put(id, 0L);
		Map<String, Long> traded = new HashMap<>();
		for (String part : formula.getItemNames())
		{
			item(++id, part);
			long price = 10_000_000L * (id - 999);
			prices.put(id, price);
			traded.put(BossCatalog.normalize(part), price);
		}
		book.resolve(client);
		long expected = formula.evaluate(name -> traded.getOrDefault(BossCatalog.normalize(name), 0L));
		assertEquals(expected, book.priceOf(untradeable));

		// one of the parts loses its price: what the formula then makes of it, and never a negative value
		prices.put(id, 0L);
		traded.put(BossCatalog.normalize(formula.getItemNames().iterator().next()), traded.values().iterator().next());
		book.refresh(true);
		assertTrue(book.priceOf(untradeable) >= 0);
	}

	@Test
	public void clearingForgetsEverythingAndTheGameIsReadAgain()
	{
		item(20997, BOW);
		prices.put(20997, 1_000L);
		book.resolve(client);
		book.clear();
		assertFalse(book.isResolved());
		assertEquals(-1, book.idOf(BOW));
		assertEquals(0, book.priceOf(BOW));
		// the plugin was turned on again: the next login reads the game again
		assertTrue(book.resolve(client));
		assertEquals(1_000, book.priceOf(BOW));
	}

	@Test
	public void anEmptyItemListResolvesToNothing()
	{
		assertTrue(book.resolve(client));
		assertTrue(book.isResolved());
		assertEquals(-1, book.idOf(BOW));
		assertEquals(0, book.priceOf(BOW));
	}
}
