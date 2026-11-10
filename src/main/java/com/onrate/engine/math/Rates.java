package com.onrate.engine.math;

import java.util.Locale;

/** Reading and writing drop rates ("1/50", "3/512", "0.02") and GP amounts. */
public final class Rates
{
	private Rates()
	{
	}

	/** Parses "1/50", "3/512" or a plain decimal like "0.02". Returns 0 for null or blank. */
	public static double parse(String rate)
	{
		if (rate == null || rate.trim().isEmpty())
		{
			return 0;
		}
		String s = rate.replace(",", "").trim();
		int slash = s.indexOf('/');
		if (slash < 0)
		{
			return Double.parseDouble(s);
		}
		double num = Double.parseDouble(s.substring(0, slash).trim());
		double den = Double.parseDouble(s.substring(slash + 1).trim());
		if (den <= 0)
		{
			throw new IllegalArgumentException("Bad rate: " + rate);
		}
		return num / den;
	}

	/** "1/181" style text for a per-kill rate. */
	public static String format(double rate)
	{
		if (rate <= 0)
		{
			return "unknown";
		}
		double inverse = 1.0 / rate;
		if (inverse < 10)
		{
			return String.format(Locale.ROOT, "1/%.2f", inverse);
		}
		if (Math.abs(inverse - Math.rint(inverse)) < 0.05 || inverse >= 1000)
		{
			return String.format(Locale.ROOT, "1/%,d", Math.round(inverse));
		}
		return String.format(Locale.ROOT, "1/%,.1f", inverse);
	}

	/** "1.24b", "345.6m", "12.3k" or "950" coins. */
	public static String gp(double gp)
	{
		double abs = Math.abs(gp);
		if (abs >= 1_000_000_000)
		{
			return String.format(Locale.ROOT, "%.2fb", gp / 1_000_000_000);
		}
		if (abs >= 1_000_000)
		{
			return String.format(Locale.ROOT, "%.1fm", gp / 1_000_000);
		}
		if (abs >= 1_000)
		{
			return String.format(Locale.ROOT, "%.1fk", gp / 1_000);
		}
		return String.format(Locale.ROOT, "%.0f", gp);
	}
}
