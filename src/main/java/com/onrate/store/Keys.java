package com.onrate.store;

/**
 * Where On Rate keeps things in RuneLite's settings. These names are the saved-data format: they
 * must not change, or players lose their history. (The group is still "expecteddrops" from the
 * first release for exactly that reason.)
 */
public final class Keys
{
	/** RuneLite config group. */
	public static final String GROUP = "expecteddrops";
	/** The main saved data of an account (one JSON document). */
	public static final String DATA = "progress";
	/** Each boss's History is saved under its own key: this plus the boss name. */
	public static final String KILLS = "kills.";
	/** Each raid's saved raids (with points) likewise. */
	public static final String RAIDS = "raids.";
	/** Unreadable data is kept under its key plus this, so the next save can't destroy it. */
	public static final String CORRUPT_SUFFIX = ".corrupt";

	/** Per account: the last real Slayer level seen. */
	public static final String SLAYER_LEVEL = "slayerLevel";
	/** The tab and page the panel was left on. */
	public static final String LAST_PAGE = "accountPage";

	private Keys()
	{
	}

	public static String killsKey(String bossName)
	{
		return KILLS + bossName.replaceAll("[^A-Za-z0-9]", "");
	}

	public static String raidsKey(String bossName)
	{
		return RAIDS + bossName.replaceAll("[^A-Za-z0-9]", "");
	}
}
