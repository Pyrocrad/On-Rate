package com.onrate.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.KillTimes;
import java.io.StringReader;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The links to the OSRS Wiki guides that the bundled kill times come from. */
public class KillTimesGuideTest
{
	private static final String BASE = "https://oldschool.runescape.wiki/w/";

	private final KillTimes web = KillTimes.bundled(new Gson());

	@Test
	public void titlesBecomeWikiAddresses()
	{
		assertEquals(BASE + "Killing_Kree'arra", web.guideUrl("Killing Kree'arra"));
		assertEquals(BASE + "Killing_the_Giant_Mole_(Dharok's)", web.guideUrl("Killing the Giant Mole (Dharok's)"));
		assertEquals(BASE + "Shellbane_gryphon/Strategies", web.guideUrl("Shellbane gryphon/Strategies"));
	}

	@Test
	public void otherCharactersArePercentEncoded()
	{
		assertEquals(BASE + "A%26B%3F%23%25%2B_C", web.guideUrl("A&B?#%+ C"));
		assertEquals(BASE + "Caf%C3%A9", web.guideUrl("Café"));
	}

	@Test
	public void noTitleNoLink()
	{
		assertNull(web.guideUrl(null));
		assertNull(web.guideUrl("  "));
	}

	@Test
	public void onlyTheOsrsWikiIsLinked()
	{
		KillTimes other = KillTimes.load(new Gson(), new StringReader("{\"guideUrl\":\"https://example.com/w/\",\"bosses\":{}}"));
		assertNull(other.guideUrl("Killing Zulrah"));
		assertNull(KillTimes.load(new Gson(), new StringReader("{}")).guideUrl("Killing Zulrah"));
	}

	@Test
	public void everyBundledTitleIsAValidAddress() throws Exception
	{
		int titles = 0;
		for (String boss : Arrays.asList("Zulrah", "Kree'arra", "Giant Mole", "Shellbane Gryphon"))
		{
			KillTimes.Estimate e = web.forBoss(boss, null);
			assertNotNull(boss, e);
			assertFalse(boss, e.getGuides().isEmpty());
			for (String title : e.getGuides())
			{
				URI uri = new URI(web.guideUrl(title));
				assertEquals("oldschool.runescape.wiki", uri.getHost());
				assertTrue(uri.getPath().startsWith("/w/"));
				titles++;
			}
		}
		assertTrue(titles >= 4);
	}

	@Test
	public void aPartWithTwoGuidesKeepsBoth()
	{
		List<String> guides = web.forBoss("Tombs of Amascut", "Expert Mode").getGuides();
		assertEquals(2, guides.size());
		for (String title : guides)
		{
			assertNotNull(web.guideUrl(title));
		}
	}
}
