package com.expecteddrops.tracking;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** The kill counts of the drops On Rate saw at one boss, per item. */
public class DropHistory
{
	private final int killCount;
	private final Integer trackingStartKc;
	private final Map<String, List<Integer>> drops;

	DropHistory(int killCount, Integer trackingStartKc, Map<String, List<Integer>> drops)
	{
		this.killCount = killCount;
		this.trackingStartKc = trackingStartKc;
		this.drops = Collections.unmodifiableMap(drops);
	}

	public int getKillCount()
	{
		return killCount;
	}

	/** Kill count when On Rate started following this boss; earlier drops aren't known. */
	public Integer getTrackingStartKc()
	{
		return trackingStartKc;
	}

	/** Item label to the kill counts its drops came at, oldest first. */
	public Map<String, List<Integer>> getDrops()
	{
		return drops;
	}
}
