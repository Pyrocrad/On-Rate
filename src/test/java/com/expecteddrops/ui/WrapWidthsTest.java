package com.expecteddrops.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/**
 * Wrap widths come from Theme, not from numbers typed into the panel, so a width changes in one place.
 */
public class WrapWidthsTest
{
	@Test
	public void widthsKeepTheirValues()
	{
		Assert.assertEquals(165, Theme.TEXT_WIDTH);
		Assert.assertEquals(148, Theme.CARD_TEXT_WIDTH);
		Assert.assertEquals(240, Theme.TIP_WIDE);
		Assert.assertEquals(240, Theme.DIALOG_WIDTH);
		Assert.assertTrue(Theme.CARD_TEXT_WIDTH < Theme.TEXT_WIDTH);
	}

	@Test
	public void panelHasNoTypedOutWidthsLeftForThemedSizes() throws Exception
	{
		Path source = Paths.get("src/main/java/com/expecteddrops/ui/ExpectedDropsPanel.java");
		if (!Files.exists(source))
		{
			return; // run from another directory
		}
		String text = new String(Files.readAllBytes(source), java.nio.charset.StandardCharsets.UTF_8);
		for (String typed : new String[] {"width: 165px", "width: 240px", "width: 220px", "width: 260px"})
		{
			Assert.assertFalse(typed + " should use a Theme width", text.contains(typed));
		}
		Assert.assertFalse(text.contains("Theme.wrap(paragraphs[i], 148)"));
	}
}
