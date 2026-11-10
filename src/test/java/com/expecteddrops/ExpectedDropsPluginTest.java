package com.expecteddrops;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Starts RuneLite with this plugin loaded. Run it from your IDE or with ./gradlew run.
 */
public class ExpectedDropsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ExpectedDropsPlugin.class);
		RuneLite.main(args);
	}
}
