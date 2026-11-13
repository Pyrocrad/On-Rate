package com.onrate.engine.data;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * The data files bundled with the plugin. On Rate reads nothing else and makes no web requests:
 * drop rates, kill times and the Ladlor chart all ship inside the jar.
 */
public final class Bundled
{
	/** Where the bundled files live inside the jar. */
	public static final String BASE = "/com/onrate/";

	private Bundled()
	{
	}

	/** A bundled file as UTF-8 text. The caller closes it. */
	public static Reader reader(String file)
	{
		InputStream in = Bundled.class.getResourceAsStream(BASE + file);
		if (in == null)
		{
			throw new IllegalStateException("Missing bundled file " + file);
		}
		return new InputStreamReader(in, StandardCharsets.UTF_8);
	}

	/** The boss catalog from {@code bosses.json}. */
	public static BossCatalog catalog(Gson gson)
	{
		try (Reader reader = reader("bosses.json"))
		{
			return BossCatalog.load(gson, reader);
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Could not read bosses.json", e);
		}
	}
}
