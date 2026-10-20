package com.expecteddrops.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * One layout for every tooltip: a bold title, a few facts, and an optional grey footnote.
 * <pre>new Tip("Vorkath's head").line("1/50 per kill").note("Counted from ...").wide()</pre>
 */
final class Tip
{
	private final String title;
	private final List<String> lines = new ArrayList<>();
	private final List<String> notes = new ArrayList<>();

	Tip(String title)
	{
		this.title = title;
	}

	Tip line(String html)
	{
		if (html != null && !html.isEmpty())
		{
			lines.add(Texts.html(html));
		}
		return this;
	}

	/** A line in a colour (a verdict). */
	Tip line(String text, Color color)
	{
		return line("<font color='" + Theme.hex(color) + "'>" + text + "</font>");
	}

	/** Grey text at the bottom: where a number comes from, or a caveat. */
	Tip note(String html)
	{
		if (html != null && !html.isEmpty())
		{
			notes.add(Texts.html(html));
		}
		return this;
	}

	/** For details (item and value tooltips). */
	String wide()
	{
		return build(Theme.TIP_WIDE);
	}

	/** For short hints. */
	String small()
	{
		return build(Theme.TIP_SHORT);
	}

	private String build(int width)
	{
		StringBuilder sb = new StringBuilder();
		if (title != null)
		{
			sb.append("<b>").append(title).append("</b>");
		}
		for (String l : lines)
		{
			sb.append(sb.length() > 0 ? "<br>" : "").append(l);
		}
		if (!notes.isEmpty())
		{
			sb.append("<br><font color='").append(Theme.hex(Theme.TEXT_MUTED)).append("'>");
			sb.append(String.join("<br>", notes)).append("</font>");
		}
		return Theme.wrap(sb.toString(), width);
	}
}
