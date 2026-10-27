package com.expecteddrops.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;

/**
 * What an untradeable unique is worth, written in terms of tradeable items:
 * {@code "Amulet of rancour - Amulet of torture"}, {@code "Noxious halberd / 3"}.
 * Operators ({@code + - * /}) have spaces around them; a term is an item name or a number,
 * and {@code *} and {@code /} bind tighter than {@code +} and {@code -}.
 */
public final class ValueFormula
{
	private static final Pattern SUM_SPLIT = Pattern.compile("\\s([+-])\\s");
	private static final Pattern PRODUCT_SPLIT = Pattern.compile("\\s([*/])\\s");

	private final List<Character> signs = new ArrayList<>();
	private final List<List<String>> terms = new ArrayList<>();
	private final List<List<Character>> productOps = new ArrayList<>();
	private final Set<String> itemNames = new LinkedHashSet<>();

	private ValueFormula()
	{
	}

	public static ValueFormula parse(String text)
	{
		ValueFormula f = new ValueFormula();
		java.util.regex.Matcher sums = SUM_SPLIT.matcher(text);
		int start = 0;
		char sign = '+';
		while (true)
		{
			boolean found = sums.find();
			f.addTerm(sign, text.substring(start, found ? sums.start() : text.length()).trim());
			if (!found)
			{
				break;
			}
			sign = sums.group(1).charAt(0);
			start = sums.end();
		}
		return f;
	}

	private void addTerm(char sign, String term)
	{
		List<String> factors = new ArrayList<>();
		List<Character> ops = new ArrayList<>();
		java.util.regex.Matcher m = PRODUCT_SPLIT.matcher(term);
		int start = 0;
		while (m.find())
		{
			factors.add(term.substring(start, m.start()).trim());
			ops.add(m.group(1).charAt(0));
			start = m.end();
		}
		factors.add(term.substring(start).trim());
		for (String factor : factors)
		{
			if (factor.isEmpty())
			{
				throw new IllegalArgumentException("Empty term in value formula: " + term);
			}
			if (!isNumber(factor))
			{
				itemNames.add(factor);
			}
		}
		signs.add(sign);
		terms.add(factors);
		productOps.add(ops);
	}

	private static boolean isNumber(String s)
	{
		return s.matches("\\d+(\\.\\d+)?");
	}

	/** The item names the formula looks up a price for. */
	public Set<String> getItemNames()
	{
		return itemNames;
	}

	/**
	 * The value in GP, never below 0. 0 as well when any item in it has no price, since the
	 * rest of the formula would then be a wrong number rather than a small one.
	 */
	public long evaluate(ToLongFunction<String> price)
	{
		double total = 0;
		for (int t = 0; t < terms.size(); t++)
		{
			List<String> factors = terms.get(t);
			double value = 0;
			for (int f = 0; f < factors.size(); f++)
			{
				double x;
				String factor = factors.get(f);
				if (isNumber(factor))
				{
					x = Double.parseDouble(factor);
				}
				else
				{
					long p = price.applyAsLong(factor);
					if (p <= 0)
					{
						return 0;
					}
					x = p;
				}
				if (f == 0)
				{
					value = x;
				}
				else if (productOps.get(t).get(f - 1) == '*')
				{
					value *= x;
				}
				else
				{
					value /= x;
				}
			}
			total += signs.get(t) == '+' ? value : -value;
		}
		return Math.max(0, (long) total);
	}
}
