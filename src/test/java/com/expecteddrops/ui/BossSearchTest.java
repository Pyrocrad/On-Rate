package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.Bundled;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/** The boss search box: name matches as before, item names from 3 letters, at word starts. */
public class BossSearchTest
{
	private BossCatalog catalog;
	private BossSearch search;

	@Before
	public void setUp() throws Exception
	{
		try (Reader reader = Bundled.reader("bosses.json"))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		search = new BossSearch(catalog);
	}

	private List<String> names(String query)
	{
		List<String> out = new ArrayList<>();
		for (BossSearch.Hit hit : search.search(query))
		{
			out.add(hit.boss().getName());
		}
		return out;
	}

	private BossSearch.Hit hit(String query, String boss)
	{
		for (BossSearch.Hit hit : search.search(query))
		{
			if (hit.boss().getName().equals(boss))
			{
				return hit;
			}
		}
		return null;
	}

	@Test
	public void emptyQueryListsEveryBossWithoutReasons()
	{
		List<BossSearch.Hit> hits = search.search("");
		assertEquals(catalog.getBosses().size(), hits.size());
		for (BossSearch.Hit hit : hits)
		{
			assertNull(hit.reason());
		}
	}

	@Test
	public void fangFindsTheSixBossesThatDropOne()
	{
		assertEquals(Arrays.asList("Alchemical Hydra", "Araxxor", "Maggot King", "Tombs of Amascut",
			"Venenatis and Spindel", "Zulrah"), names("fang"));
		assertNotNull(hit("fang", "Zulrah").reason());
	}

	@Test
	public void ringMatchesWordStartsOnly()
	{
		List<String> found = names("ring");
		assertEquals(6, found.size());
		assertTrue(found.contains("Dagannoth Kings"));
		assertFalse(found.contains("Cerberus"));
		assertFalse(found.contains("Scorpia"));
		assertFalse(found.contains("Fractured Archive"));
	}

	@Test
	public void visageFindsThreeBosses()
	{
		assertEquals(Arrays.asList("King Black Dragon", "Vorkath", "Zulrah"), names("visage"));
	}

	@Test
	public void tassetsFindsGraardorThenMoonsOfPeril()
	{
		assertEquals(Arrays.asList("General Graardor", "Moons of Peril"), names("tassets"));
	}

	@Test
	public void bossNameMatchesComeFirstAndHaveNoReason()
	{
		List<BossSearch.Hit> hits = search.search("dragon");
		assertEquals("King Black Dragon", hits.get(0).boss().getName());
		assertNull(hits.get(0).reason());
		assertTrue(hits.size() > 1);
		assertNotNull(hits.get(1).reason());
	}

	@Test
	public void shortWordsFindNothingFromItemsEvenIfTheyStartAnItemWord()
	{
		// under 3 letters only the name rules apply: "fa" is no boss name, kill count name or short name
		for (BossSearch.Hit hit : search.search("fa"))
		{
			assertNull(hit.reason());
		}
	}

	@Test
	public void coxStillOpensChambersOfXericFirst()
	{
		List<BossSearch.Hit> hits = search.search("cox");
		assertEquals("Chambers of Xeric", hits.get(0).boss().getName());
		assertNull(hits.get(0).reason());
	}

	@Test
	public void everyTypedWordMustMatchAWordOfTheSameItem()
	{
		List<String> pick = names("dragon pick");
		assertTrue(pick.contains("Kalphite Queen"));
		assertTrue(pick.contains("Chaos Elemental"));
		// a name that is not an item, in any order
		assertTrue(names("pick dragon").containsAll(pick));
		assertTrue(names("dragon zzz").isEmpty());
	}

	@Test
	public void sharedItemsAreFoundOnEveryBossThatDropsThem()
	{
		List<String> onyx = names("uncut onyx");
		assertTrue(onyx.contains("Skotizo"));
		assertTrue(onyx.contains("Zulrah"));
		// godsword shards are several pools; each boss is listed once
		List<String> shard = names("godsword shard");
		assertEquals(shard.size(), new java.util.HashSet<>(shard).size());
		assertFalse(shard.isEmpty());
	}

	@Test
	public void clogSharedItemsMatchNothing()
	{
		assertTrue(names("dragon 2h").isEmpty());
	}

	@Test
	public void extraNamesAndLabelsFindTheBoss()
	{
		assertEquals(Arrays.asList("Fortis Colosseum"), names("cuirass"));
		assertEquals("Sunfire fanatic cuirass", hit("cuirass", "Fortis Colosseum").reason().split(" \\+")[0]);
	}

	@Test
	public void curlyApostrophesAndDashesDoNotGetInTheWay()
	{
		assertEquals(Arrays.asList("jal", "nib", "rek"), BossSearch.words("Jal-Nib-Rek"));
		assertEquals(Arrays.asList("dragons", "pick"), BossSearch.words("Dragon\u2019s pick"));
	}

	@Test
	public void exactItemNameComesBeforeWordStartMatches()
	{
		// "Tanzanite fang" is an exact item name at Zulrah; every other fang hit is a word-start match
		List<BossSearch.Hit> hits = search.search("tanzanite fang");
		assertEquals("Zulrah", hits.get(0).boss().getName());
	}

	@Test
	public void everyBossStillFindsItselfByNameFirst()
	{
		for (BossDefinition boss : catalog.getBosses())
		{
			BossSearch.Hit first = search.search(boss.getName()).get(0);
			// an earlier boss may also contain the whole name, but only as a name match
			assertNull(boss.getName(), first.reason());
			assertTrue(boss.getName(), names(boss.getName()).contains(boss.getName()));
		}
	}

	@Test
	public void reasonsMapHoldsOnlyItemMatches()
	{
		java.util.Map<String, String> reasons = BossSearch.reasons(search.search("cox"));
		assertFalse(reasons.containsKey("Chambers of Xeric"));
		assertTrue(BossSearch.reasons(search.search("fang")).containsKey("Zulrah"));
	}
}
