package com.onrate.engine.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which items deserve a place in the account-wide lists. The thresholds are the ones On Rate has
 * always used, so a given account gets the same lists.
 */
public final class ItemRanking
{
	public enum Mode
	{
		/** Below average with at least one expected, driest first. */
		DRIEST,
		/** Items you have that are above average, luckiest first. */
		LUCKIEST,
		/** The luckiest pets you have, then the ones you would most likely have by now but don't. */
		PETS
	}

	/** Items need at least this many expected to count as dry: 0 of 0.2 says nothing yet. */
	public static final double MIN_EXPECTED_DRY = 1.0;
	/** Pets shown per group (owned, missing). */
	public static final int PETS_SHOWN = 5;
	/** Default cap on a list. */
	public static final int DEFAULT_LIMIT = 15;

	private ItemRanking()
	{
	}

	public static List<ItemHighlight> pick(AccountStats account, Mode mode)
	{
		return pick(account, mode, DEFAULT_LIMIT);
	}

	/** At most {@code limit} items (pets are always {@link #PETS_SHOWN} per group). */
	public static List<ItemHighlight> pick(AccountStats account, Mode mode, int limit)
	{
		List<ItemHighlight> list = new ArrayList<>();
		for (ItemHighlight h : account.getItems())
		{
			RowStats r = h.getRow();
			if (mode == Mode.PETS)
			{
				if (h.isPet() && r.getExpected() > 0)
				{
					list.add(h);
				}
				continue;
			}
			if (Double.isNaN(r.getLuck()))
			{
				continue;
			}
			// driest: below average with at least one expected; luckiest: above average and you have it
			if (mode == Mode.LUCKIEST ? r.getReceived() > 0 && r.getLuck() > 0.5
				: r.getExpected() >= MIN_EXPECTED_DRY && !r.isComplete() && r.getLuck() < 0.5)
			{
				list.add(h);
			}
		}
		if (mode == Mode.PETS)
		{
			List<ItemHighlight> lucky = new ArrayList<>();
			List<ItemHighlight> dry = new ArrayList<>();
			for (ItemHighlight h : list)
			{
				(h.getRow().isComplete() ? lucky : dry).add(h);
			}
			lucky.sort(Comparator.comparingDouble((ItemHighlight h) -> h.getRow().getExpected())
				.thenComparing(h -> h.getRow().getLabel()));
			dry.sort(Comparator.comparingDouble((ItemHighlight h) -> -h.getRow().getChanceAtLeastOne())
				.thenComparing(h -> h.getRow().getLabel()));
			List<ItemHighlight> result = new ArrayList<>(lucky.subList(0, Math.min(PETS_SHOWN, lucky.size())));
			result.addAll(dry.subList(0, Math.min(PETS_SHOWN, dry.size())));
			return result;
		}
		Comparator<ItemHighlight> byLuck = Comparator.comparingDouble(h -> h.getRow().getLuck());
		list.sort(mode == Mode.LUCKIEST ? byLuck.reversed() : byLuck);
		return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
	}
}
