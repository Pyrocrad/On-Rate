package com.expecteddrops.ui;

import com.onrate.engine.math.Rates;
import com.onrate.engine.math.Verdict;

import com.onrate.engine.stats.AccountStats;
import com.onrate.engine.stats.ItemHighlight;
import com.onrate.engine.stats.RowStats;
import java.util.Locale;

/**
 * The plain text behind "Copy my luck": your account's verdicts in a few lines you can paste into
 * Discord or Reddit. It uses the same words and numbers as the panel and nothing else, and is never
 * sent anywhere: the player pastes it themselves.
 */
final class ShareText
{
	/** An item counts as a brag when it is past "Spooned" (more than 65% of players have fewer). */
	private static final double BRAG_ABOVE = 0.65;

	private ShareText()
	{
	}

	/**
	 * The account's verdicts as text, or null when there is nothing to share yet (no boss loaded,
	 * or no verdict).
	 *
	 * @param showValue whether the GP value setting is on, as for the tiles in the panel
	 */
	static String account(AccountStats account, boolean showValue)
	{
		if (account == null || account.getRows().isEmpty())
		{
			return null;
		}
		RowStats total = account.getTotal();
		String verdict = Verdict.labelOf(total.getLuck());
		if (verdict.isEmpty())
		{
			return null;
		}

		StringBuilder out = new StringBuilder();
		out.append(String.format(Locale.ROOT, "On Rate: %s · %,d unique%s, %s expected", verdict, total.getReceived(),
			total.getReceived() == 1 ? "" : "s", Texts.formatExpected(total.getExpected())));
		String how = Texts.luckShort(total.getShares(), total.getReceived());
		if (how != null && !how.isEmpty())
		{
			out.append(" (").append(how).append(')');
		}

		// the GP value verdict, under the same rule as the GP value tile
		if (showValue && account.getExpectedValue() > 0 && !Double.isNaN(account.getValueLuck()))
		{
			String valueVerdict = Verdict.labelOf(account.getValueLuck());
			if (!valueVerdict.isEmpty())
			{
				out.append(String.format(Locale.ROOT, "%nGP value: %s · %s got, %s expected", valueVerdict,
					Rates.gp(account.getReceivedValue()), Rates.gp(account.getExpectedValue())));
			}
		}

		int bosses = account.getRows().size();
		out.append(String.format(Locale.ROOT, "%n%,d boss%s", bosses, bosses == 1 ? "" : "es"));
		ItemHighlight best = luckiest(account);
		if (best != null)
		{
			out.append(" · Luckiest: ").append(best.getRow().getLabel()).append(" (").append(best.getBoss()).append("), ")
				.append(Verdict.labelOf(best.getRow().getLuck()));
		}
		return out.toString();
	}

	/** The item you got that is furthest on the lucky side, if any is past "Spooned". */
	private static ItemHighlight luckiest(AccountStats account)
	{
		ItemHighlight best = null;
		for (ItemHighlight h : account.getItems())
		{
			RowStats row = h.getRow();
			double luck = row.getLuck();
			if (row.getReceived() <= 0 || Double.isNaN(luck) || luck <= BRAG_ABOVE)
			{
				continue;
			}
			if (best == null || luck > best.getRow().getLuck())
			{
				best = h;
			}
		}
		return best;
	}
}
