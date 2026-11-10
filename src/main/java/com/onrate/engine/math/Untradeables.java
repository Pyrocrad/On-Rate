package com.onrate.engine.math;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Araxxor's halberd pieces: untradeable, and no duplicates drop until a full set is made. */
public final class Untradeables
{
	private static final Set<String> HALBERD_PIECES = Collections.unmodifiableSet(new HashSet<>(
		Arrays.asList("noxious point", "noxious blade", "noxious pommel")));

	private Untradeables()
	{
	}

	public static boolean isHalberdPiece(String itemName)
	{
		return itemName != null && HALBERD_PIECES.contains(itemName.toLowerCase(Locale.ROOT));
	}

	public static int halberdPieceCount()
	{
		return HALBERD_PIECES.size();
	}

	/**
	 * GP the halberd pieces you have are worth: only full sets, since a piece alone can't be sold
	 * and duplicates don't drop before the set is done. {@code piecePrice} is a third of a halberd.
	 */
	public static long setValue(int pieces, long piecePrice)
	{
		return (pieces / HALBERD_PIECES.size()) * HALBERD_PIECES.size() * piecePrice;
	}
}
