package com.expecteddrops.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * One layout for every tooltip: a bold title, a few facts, and an optional grey footnote.
 * Facts are lines of text, or label / value rows with a thin line between them:
 * <pre>new Tip("Vorkath's head").row("Drop rate", "1/50 per kill").note("Counted from ...").wide()</pre>
 * Lines and rows show in the order they were added; rows next to each other form one table.
 */
final class Tip
{
	private final String title;
	/** Lines (a String) and rows (a String[] of label and value), in order. */
	private final List<Object> parts = new ArrayList<>();
	private final List<String> notes = new ArrayList<>();

	Tip(String title)
	{
		this.title = title;
	}

	Tip line(String html)
	{
		if (html != null && !html.isEmpty())
		{
			parts.add(Texts.html(html));
		}
		return this;
	}

	/** A line in a colour (a verdict, or grey for a subtitle). */
	Tip line(String text, Color color)
	{
		if (text == null || text.isEmpty())
		{
			return this;
		}
		return line("<font color='" + Theme.hex(color) + "'>" + text + "</font>");
	}

	/** A grey label on the left, its value on the right. Skipped when the value is empty. */
	Tip row(String label, String value)
	{
		if (value != null && !value.isEmpty())
		{
			parts.add(new String[]{Texts.html(label), Texts.html(value)});
		}
		return this;
	}

	/** A row whose value is in a colour (a verdict). */
	Tip row(String label, String value, Color color)
	{
		if (value == null || value.isEmpty())
		{
			return this;
		}
		return row(label, "<font color='" + Theme.hex(color) + "'>" + value + "</font>");
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
		String muted = Theme.hex(Theme.TEXT_MUTED);
		// a line above every row: it parts the rows from each other and from the text above
		String cell = "style='border-top: 1px solid " + Theme.hex(Theme.DIVIDER) + "'";
		StringBuilder sb = new StringBuilder();
		if (title != null)
		{
			sb.append("<b>").append(title).append("</b>");
		}
		boolean inTable = false;
		for (Object part : parts)
		{
			if (part instanceof String[])
			{
				if (!inTable)
				{
					sb.append("<table width='100%' cellpadding='2' cellspacing='0' style='margin-top: 3px; margin-bottom: 3px'>");
					inTable = true;
				}
				String[] row = (String[]) part;
				sb.append("<tr><td nowrap ").append(cell).append("><font color='").append(muted).append("'>")
					.append(row[0]).append("</font></td><td align='right' ").append(cell).append(">")
					.append(row[1]).append("</td></tr>");
				continue;
			}
			if (inTable)
			{
				sb.append("</table>");
				inTable = false;
			}
			else if (sb.length() > 0)
			{
				sb.append("<br>");
			}
			sb.append(part);
		}
		if (inTable)
		{
			sb.append("</table>");
		}
		if (!notes.isEmpty())
		{
			// a table ends its own line
			sb.append(sb.length() == 0 || endsWithTable(sb) ? "" : "<br>").append("<font color='").append(muted).append("'>");
			sb.append(String.join("<br>", notes)).append("</font>");
		}
		return Theme.wrap(sb.toString(), width);
	}

	private static boolean endsWithTable(StringBuilder sb)
	{
		return sb.length() >= 8 && sb.substring(sb.length() - 8).equals("</table>");
	}
}
