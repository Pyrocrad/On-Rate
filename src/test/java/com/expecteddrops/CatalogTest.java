package com.expecteddrops;

import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.data.BossDefinition;
import com.expecteddrops.data.DropDefinition;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/** Typos in bosses.json: names that clash, shared items that don't line up. Rates are checked in TrackerTest. */
public class CatalogTest
{
	private BossCatalog catalog;

	@Before
	public void setUp() throws Exception
	{
		try (Reader reader = new InputStreamReader(
			ExpectedDropsPlugin.class.getResourceAsStream("bosses.json"), StandardCharsets.UTF_8))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
	}

	@Test
	public void dropNamesAreUniqueWithinABoss()
	{
		for (BossDefinition boss : catalog.getBosses())
		{
			Set<String> seen = new HashSet<>();
			for (DropDefinition drop : boss.getDrops())
			{
				for (String name : drop.getAllNames())
				{
					assertTrue(boss.getName() + " lists " + name + " twice", seen.add(BossCatalog.normalize(name)));
				}
			}
		}
	}

	@Test
	public void everyKillCountMessageNameBelongsToOneBoss()
	{
		Map<String, String> owner = new HashMap<>();
		for (BossDefinition boss : catalog.getBosses())
		{
			for (String kc : boss.getKcNames())
			{
				String key = BossCatalog.normalize(kc);
				String previous = owner.put(key, boss.getName());
				assertTrue("'" + kc + "' counts for both " + previous + " and " + boss.getName(),
					previous == null || previous.equals(boss.getName()));
				assertSame(boss, catalog.byKcName(kc));
			}
			assertSame(boss, catalog.byName(boss.getName()));
		}
	}

	@Test
	public void everyBossHasAUsableLootOrKillCountName()
	{
		for (BossDefinition boss : catalog.getBosses())
		{
			for (String loot : boss.getLootNames())
			{
				assertNotNull(boss.getName() + " loot " + loot, catalog.byLootName(loot));
			}
		}
	}

	@Test
	public void sharedItemsAreDroppedByAtLeastTwoBosses()
	{
		assertFalse(catalog.getPools().isEmpty());
		for (Map.Entry<String, List<BossCatalog.PoolSource>> e : catalog.getPools().entrySet())
		{
			Set<String> bosses = new HashSet<>();
			for (BossCatalog.PoolSource src : e.getValue())
			{
				bosses.add(src.getBoss().getName());
				// a pooled drop is looked up again by the bosses that own it
				assertEquals(e.getKey(), catalog.poolOf(src.getBoss(), src.getDrop().getName()));
				assertTrue(catalog.poolsOf(src.getBoss()).containsKey(e.getKey()));
				// the boss page leaves pooled items out: they only show in the account pool
				for (DropDefinition drop : src.getBoss().getDrops())
				{
					assertFalse(src.getBoss().getName() + " shows pooled " + e.getKey(),
						BossCatalog.normalize(drop.getName()).equals(e.getKey()));
				}
			}
			assertTrue("pool " + e.getKey() + " has only " + bosses, bosses.size() >= 2);
		}
	}

	@Test
	public void sharedAndExcludedItemsAreLeftOffTheBossPages()
	{
		for (BossDefinition boss : catalog.getBosses())
		{
			for (DropDefinition drop : boss.getDrops())
			{
				assertFalse(boss.getName() + " page shows " + drop.getName(),
					catalog.getExcludedBosses().contains(boss.getName()));
			}
		}
	}

	@Test
	public void everyValueFormulaParsesAndNamesItsItems()
	{
		int found = 0;
		for (BossDefinition boss : catalog.getBosses())
		{
			for (DropDefinition drop : boss.getDrops())
			{
				if (drop.getValueFrom() != null)
				{
					found++;
					assertFalse(drop.getName(), com.expecteddrops.data.ValueFormula.parse(drop.getValueFrom()).getItemNames().isEmpty());
					assertNotNull(drop.getName(), catalog.getValueFormula(drop.getName()));
				}
			}
		}
		assertTrue(found >= 18);
	}

	@Test
	public void hiddenBossesStayOutOfTheCatalog()
	{
		assertEquals(1, catalog.getHiddenBosses().size());
		BossDefinition archive = catalog.getHiddenBosses().get(0);
		assertEquals("The Fractured Archive", archive.getName());
		assertTrue(archive.isHidden());
		assertEquals(15.0, archive.getTotalWeight(), 0.0001);
		assertEquals(null, catalog.byName("The Fractured Archive"));
		assertEquals(null, catalog.byKcName("The Fractured Archive"));
		assertEquals(null, catalog.byLootName("The Fractured Archive"));
		assertTrue(catalog.byClogPage("The Fractured Archive").isEmpty());
		assertFalse(catalog.getBosses().contains(archive));
		assertFalse(catalog.getAllItemNames().contains("rondache"));
	}
}
