package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.data.DropDefinition;
import java.awt.FontMetrics;
import java.io.Reader;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JLabel;
import org.junit.Test;

/** Long item names break at spaces (measured with the real row font) instead of being cut off with "...". */
public class NameWrapTest
{
	/** Room for a name beside the picture and a figure, with and without the scrollbar. */
	private static final int[] ROOMS = {110, 100};

	private static FontMetrics rowMetrics()
	{
		JLabel l = new JLabel();
		l.setFont(Theme.rowFont());
		return l.getFontMetrics(l.getFont());
	}

	private static Set<String> bundledNames() throws Exception
	{
		Set<String> names = new TreeSet<>();
		try (Reader reader = Bundled.reader("bosses.json"))
		{
			BossCatalog catalog = BossCatalog.load(new Gson(), reader);
			for (BossDefinition boss : catalog.getBosses())
			{
				for (DropDefinition drop : boss.getDrops())
				{
					names.add(drop.getName());
				}
			}
		}
		return names;
	}

	private static String[] lines(String shown)
	{
		String body = shown.replace("<html>", "").replace("</html>", "");
		return body.split("<br>");
	}

	private static String plain(String shown)
	{
		return shown.replace("<html>", "").replace("</html>", "").replace("<br>", " ").replace("&amp;", "&");
	}

	@Test
	public void aNameThatFitsStaysPlain()
	{
		assertEquals("Abyssal whip", VerdictTile.wrapIfLong("Abyssal whip", rowMetrics(), 1000, 0));
		assertEquals("Abyssal whip", VerdictTile.wrapIfLong("Abyssal whip", rowMetrics(), 1000, 3));
	}

	@Test
	public void aLongNameBreaksAtSpaces()
	{
		FontMetrics fm = rowMetrics();
		String shown = VerdictTile.wrapIfLong("Tonalztics of ralos (uncharged)", fm, 110, 0);
		assertTrue(shown, shown.startsWith("<html>") && shown.contains("<br>"));
		assertEquals("Tonalztics of ralos (uncharged)", plain(shown));
	}

	@Test
	public void theLastLineEndsInAnEllipsisWhenLinesRunOut()
	{
		FontMetrics fm = rowMetrics();
		String shown = VerdictTile.wrapIfLong("Tonalztics of ralos (uncharged)", fm, 50, 2);
		String[] parts = lines(shown);
		assertEquals(shown, 2, parts.length);
		assertTrue(shown, parts[1].endsWith("..."));
		for (String part : parts)
		{
			assertTrue(part + " is too wide", fm.stringWidth(part.replace("&amp;", "&")) <= 50);
		}
	}

	@Test
	public void ampersandsAreEscapedWhenBroken()
	{
		String shown = VerdictTile.wrapIfLong("Fire & water cape of the long name", rowMetrics(), 60, 0);
		assertTrue(shown, shown.contains("&amp;") || !shown.startsWith("<html>"));
	}

	@Test
	public void everyBundledNameKeepsItsTextAndFitsItsLines() throws Exception
	{
		FontMetrics fm = rowMetrics();
		int needMoreThanThree = 0;
		for (int room : ROOMS)
		{
			for (String name : bundledNames())
			{
				String shown = VerdictTile.wrapIfLong(name, fm, room, 0);
				assertEquals(name, plain(shown));
				boolean html = shown.startsWith("<html>");
				for (String line : html ? lines(shown) : new String[]{shown})
				{
					String text = line.replace("&amp;", "&");
					// a line is within the room unless it is one single word
					assertTrue(name + " at " + room + ": " + text, fm.stringWidth(text) <= room || !text.contains(" "));
				}
				if (html && lines(shown).length > 3)
				{
					needMoreThanThree++;
				}
				// with a cap, no line is wider than the room at all, and the name is only shortened by "..."
				String capped = VerdictTile.wrapIfLong(name, fm, room, 3);
				boolean cappedHtml = capped.startsWith("<html>");
				for (String line : cappedHtml ? lines(capped) : new String[]{capped})
				{
					assertTrue(name + " at " + room + ": " + line, fm.stringWidth(line.replace("&amp;", "&")) <= room);
				}
				assertFalse(name, cappedHtml && lines(capped).length > 3);
			}
		}
		// information only: how many names would be shortened at three lines (the tooltip has the full name)
		System.out.println("names needing more than three lines: " + needMoreThanThree);
	}
}
