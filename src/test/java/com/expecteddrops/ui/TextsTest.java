package com.expecteddrops.ui;

import com.expecteddrops.stats.DropMath;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** The words under and behind each verdict. */
public class TextsTest
{
	@Test
	public void noneSaysHowManyHaveNone()
	{
		assertEquals("74% have none", Texts.luckShort(DropMath.shares(0, 0.30), 0));
		assertEquals("only 1 in 5 has none", Texts.luckShort(DropMath.shares(0, 1.65), 0));
		assertEquals(">99% have none", Texts.luckShort(DropMath.shares(0, 0.001), 0));
		assertEquals("74% of players have none by now, 26% have one or more", Texts.sharesLine(DropMath.shares(0, 0.30), 0));
	}

	@Test
	public void countsSayHowCommon()
	{
		// 4 of 5.67: 33% have this many or fewer
		assertEquals("1 in 3 as dry", Texts.luckShort(DropMath.shares(4, 5.67), 4));
		// 1 of 0.16: 15% have one or more
		assertEquals("1 in 7 as lucky", Texts.luckShort(DropMath.shares(1, 0.16), 1));
		// 2 of 1.65: the middle of the range
		assertEquals("about average", Texts.luckShort(DropMath.shares(2, 1.65), 2));
		assertEquals("18% of players have fewer, 15% as many, 67% more", Texts.sharesLine(DropMath.shares(4, 5.67), 4));
	}

	@Test
	public void valueLuckInWords()
	{
		assertEquals("1 in 6 as lucky", Texts.luckShort(0.83));
		assertEquals("1 in 3 as dry", Texts.luckShort(0.30));
		assertEquals("about average", Texts.luckShort(0.5));
		assertEquals("<1 in 10,000 as dry", Texts.luckShort(0.00001));
	}

	@Test
	public void howFarPastTheRate()
	{
		// none at 2.1 expected: 2.1 times the drop rate, the way players say it
		com.expecteddrops.stats.RowStats none = new com.expecteddrops.stats.RowStats("Hydra's claw", null, 1 / 1001.0, 2.1, 0, false);
		assertEquals("2.1\u00d7 the drop rate without one", Texts.rateMultipleLine(none));
		com.expecteddrops.stats.RowStats any = new com.expecteddrops.stats.RowStats("Any", null, 0, 1.25, 0, true);
		assertEquals("1.3\u00d7 the unique rate without a unique", Texts.rateMultipleLine(any));
		// once you have one, the dry streak note says it instead
		com.expecteddrops.stats.RowStats one = new com.expecteddrops.stats.RowStats("Hydra's claw", null, 1 / 1001.0, 2.1, 1, false);
		assertEquals(null, Texts.rateMultipleLine(one));
	}
}
