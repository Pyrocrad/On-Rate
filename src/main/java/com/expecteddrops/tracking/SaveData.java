package com.expecteddrops.tracking;

import java.util.HashMap;
import java.util.Map;

/**
 * What to write when saving, put together in one go under the tracker's lock so a kill on the
 * client thread can't change the data halfway through. A null value means: remove that key.
 */
public class SaveData
{
	/** The main data (kill counts, received items, days, the luck graph). */
	public final String progress;
	/** Per boss, its History, for the bosses whose History changed. */
	public final Map<String, String> kills = new HashMap<>();
	/** Per raid, its saved raids with points, for the raids that changed. */
	public final Map<String, String> raids = new HashMap<>();

	SaveData(String progress)
	{
		this.progress = progress;
	}
}
