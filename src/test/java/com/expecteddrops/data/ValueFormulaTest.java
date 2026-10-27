package com.expecteddrops.data;

import java.util.Map;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ValueFormulaTest
{
	private static final Map<String, Long> PRICES = Map.of(
		"Amulet of rancour", 41_000_000L,
		"Amulet of torture", 15_000_000L,
		"Noxious halberd", 30_000_000L,
		"Confliction gauntlets", 55_000_000L,
		"Tormented bracelet", 15_000_000L,
		"Demon tear", 250L,
		"Ultor ring", 96_000_000L,
		"Chromium ingot", 42_000L);

	private static long value(String formula)
	{
		return ValueFormula.parse(formula).evaluate(name -> PRICES.getOrDefault(name, 0L));
	}

	@Test
	public void differenceOfTwoItems()
	{
		assertEquals(26_000_000L, value("Amulet of rancour - Amulet of torture"));
	}

	@Test
	public void shareOfAnItem()
	{
		assertEquals(10_000_000L, value("Noxious halberd / 3"));
	}

	@Test
	public void multiplicationBindsTighterThanSubtraction()
	{
		assertEquals(55_000_000L - 15_000_000L - 2_500_000L, value("Confliction gauntlets - Tormented bracelet - 10000 * Demon tear"));
		assertEquals(96_000_000L - 3 * 42_000L, value("Ultor ring - 3 * Chromium ingot"));
	}

	@Test
	public void anItemWithoutAPriceMakesItWorthNothing()
	{
		assertEquals(0L, value("Amulet of rancour - Necklace of anguish"));
	}

	@Test
	public void neverNegative()
	{
		assertEquals(0L, value("Amulet of torture - Amulet of rancour"));
	}

	@Test
	public void listsTheItemsItNeeds()
	{
		assertEquals(java.util.Set.of("Ultor ring", "Chromium ingot"), ValueFormula.parse("Ultor ring - 3 * Chromium ingot").getItemNames());
	}
}
