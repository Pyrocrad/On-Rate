package com.expecteddrops.ui;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.BossPart;
import com.onrate.engine.data.DropDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The boss search box: which bosses a typed text finds. No Swing in here, so it can be tested.
 *
 * <p>A boss matches on its name, its kill count names, its parts or its short name ("cox").
 * That works from the first letter. From {@link #MIN_ITEM_CHARS} characters a boss also matches
 * on the items it drops (shared items included): every typed word must start a word of one item
 * name, so "ring" finds "Archers ring" but not "Smouldering stone".
 * Items flagged clogShared in the data are never matched.
 */
final class BossSearch
{
	/** Item names are only searched from this many typed characters, to keep the list short. */
	static final int MIN_ITEM_CHARS = 3;

	/** One boss in the result. {@code reason} is the item it was found by, null for a name match. */
	static final class Hit
	{
		private final BossDefinition boss;
		private final String reason;

		Hit(BossDefinition boss, String reason)
		{
			this.boss = boss;
			this.reason = reason;
		}

		BossDefinition boss()
		{
			return boss;
		}

		/** For example "Tanzanite fang +1" (one more item matched); null when found by name. */
		String reason()
		{
			return reason;
		}
	}

	/** An item a boss drops: the names it can be found by. */
	private static final class Item
	{
		private final List<String> names = new ArrayList<>();
	}

	private final BossCatalog catalog;
	private final Map<BossDefinition, List<Item>> items = new HashMap<>();

	BossSearch(BossCatalog catalog)
	{
		this.catalog = catalog;
		for (BossDefinition boss : catalog.getBosses())
		{
			for (DropDefinition drop : boss.getDrops())
			{
				add(boss, drop);
			}
		}
		// shared items are not in boss.getDrops(); they live in the pools
		for (List<BossCatalog.PoolSource> sources : catalog.getPools().values())
		{
			for (BossCatalog.PoolSource src : sources)
			{
				add(src.getBoss(), src.getDrop());
			}
		}
	}

	private void add(BossDefinition boss, DropDefinition drop)
	{
		// pooled items (several bosses drop them) are found on every boss; only the flagged ones are left out
		if (drop.isClogSharedFlag())
		{
			return;
		}
		Item item = new Item();
		for (String name : drop.getAllNames())
		{
			addName(item, name);
		}
		addName(item, drop.getDisplayName());
		if (!item.names.isEmpty())
		{
			items.computeIfAbsent(boss, b -> new ArrayList<>()).add(item);
		}
	}

	private static void addName(Item item, String name)
	{
		if (name == null || name.trim().isEmpty())
		{
			return;
		}
		for (String known : item.names)
		{
			if (known.equalsIgnoreCase(name))
			{
				return;
			}
		}
		item.names.add(name);
	}

	/**
	 * The bosses the text finds, in this order: name matches in catalog order, then bosses whose
	 * item name equals the text, then bosses with an item word starting with it; alphabetical
	 * within each of those two groups. Bosses the panel does not list are filtered by the caller.
	 */
	List<Hit> search(String query)
	{
		String q = simplify(query);
		List<Hit> hits = new ArrayList<>();
		if (q.isEmpty())
		{
			for (BossDefinition boss : catalog.getBosses())
			{
				hits.add(new Hit(boss, null));
			}
			return hits;
		}
		List<BossDefinition> rest = new ArrayList<>();
		for (BossDefinition boss : catalog.getBosses())
		{
			if (matchesName(boss, q))
			{
				hits.add(new Hit(boss, null));
			}
			else
			{
				rest.add(boss);
			}
		}
		List<String> words = words(query);
		if (q.length() < MIN_ITEM_CHARS || words.isEmpty())
		{
			return hits;
		}
		String whole = String.join(" ", words);
		rest.sort(Comparator.comparing(BossDefinition::getName, String.CASE_INSENSITIVE_ORDER));
		List<Hit> exact = new ArrayList<>();
		List<Hit> wordStart = new ArrayList<>();
		for (BossDefinition boss : rest)
		{
			List<Item> own = items.get(boss);
			if (own == null)
			{
				continue;
			}
			String firstExact = null;
			String first = null;
			int count = 0;
			for (Item item : own)
			{
				String found = foundBy(item, words, whole);
				if (found == null)
				{
					continue;
				}
				count++;
				if (first == null)
				{
					first = found;
				}
				if (firstExact == null && String.join(" ", words(found)).equals(whole))
				{
					firstExact = found;
				}
			}
			if (count == 0)
			{
				continue;
			}
			String shown = firstExact != null ? firstExact : first;
			String reason = count > 1 ? shown + " +" + (count - 1) : shown;
			(firstExact != null ? exact : wordStart).add(new Hit(boss, reason));
		}
		hits.addAll(exact);
		hits.addAll(wordStart);
		return hits;
	}

	/** The name of this item that the typed words match (exact name preferred), or null. */
	private static String foundBy(Item item, List<String> words, String whole)
	{
		String match = null;
		for (String name : item.names)
		{
			List<String> nameWords = words(name);
			if (String.join(" ", nameWords).equals(whole))
			{
				return name;
			}
			if (match == null && startsWords(nameWords, words))
			{
				match = name;
			}
		}
		return match;
	}

	/** True when every typed word is the start of some word of the item name. */
	private static boolean startsWords(List<String> nameWords, List<String> typed)
	{
		for (String t : typed)
		{
			boolean found = false;
			for (String w : nameWords)
			{
				if (w.startsWith(t))
				{
					found = true;
					break;
				}
			}
			if (!found)
			{
				return false;
			}
		}
		return true;
	}

	/** Lower case words without apostrophes (straight or curly); dashes split words. */
	static List<String> words(String text)
	{
		List<String> out = new ArrayList<>();
		if (text == null)
		{
			return out;
		}
		String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("['\u2019]", "").replace('-', ' ');
		for (String word : cleaned.split("\\s+"))
		{
			if (!word.isEmpty())
			{
				out.add(word);
			}
		}
		return out;
	}

	/** A boss matches on its name, its kill count names ("CoX" style short names) or its parts. */
	static boolean matchesName(BossDefinition boss, String q)
	{
		if (simplify(boss.getName()).contains(q))
		{
			return true;
		}
		for (String initials : initialsOf(boss.getName()))
		{
			if (q.length() >= 2 && initials.equals(q))
			{
				return true;
			}
		}
		for (String name : boss.getKcNames())
		{
			if (simplify(name).contains(q))
			{
				return true;
			}
		}
		if (boss.hasParts())
		{
			for (BossPart part : boss.getParts())
			{
				if (simplify(part.getName()).contains(q))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Lower case without apostrophes or dashes, so "kril" finds K'ril and "tztok" finds TzTok-Jad. */
	static String simplify(String text)
	{
		return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("['\\-]", "").trim();
	}

	/**
	 * Short names: "cox", "tob", "toa" from all first letters, and "cx", "tb" with "of",
	 * "the" and "and" skipped.
	 */
	static String[] initialsOf(String name)
	{
		StringBuilder all = new StringBuilder();
		StringBuilder main = new StringBuilder();
		for (String word : name.toLowerCase(Locale.ROOT).split("[\\s-]+"))
		{
			if (word.isEmpty())
			{
				continue;
			}
			all.append(word.charAt(0));
			if (!word.equals("of") && !word.equals("the") && !word.equals("and"))
			{
				main.append(word.charAt(0));
			}
		}
		return new String[]{all.toString(), main.toString()};
	}

	/** The reasons by boss name, in list order, for the dropdown (only bosses found by an item). */
	static Map<String, String> reasons(List<Hit> hits)
	{
		Map<String, String> out = new LinkedHashMap<>();
		for (Hit hit : hits)
		{
			if (hit.reason() != null)
			{
				out.put(hit.boss().getName(), hit.reason());
			}
		}
		return out;
	}
}
