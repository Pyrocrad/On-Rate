package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.expecteddrops.ui.ExpectedDropsPanel.ProfileState;
import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import javax.swing.SwingUtilities;
import org.junit.Test;

/**
 * Until an account is loaded the panel says why (not logged in, loading, or loading failed) instead
 * of a new player's setup steps (audit H1 and its follow-up).
 */
public class PreLoginTest
{
	// preload bundled data with a Gson, as the plugin does at startup (bundled() alone is empty until then)
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson());
		com.onrate.engine.data.LadlorChart.bundled(new com.google.gson.Gson());
	}

	private static final RaidSettings RAIDS = raid -> 0.03;

	/** The account page's note, its big word, and whether setup steps show, in this state. */
	private static Object[] shown(ProfileState... states) throws Exception
	{
		Object[] out = new Object[3];
		SwingUtilities.invokeAndWait(() ->
		{
			// the welcome guide is on, as for a new player
			ExpectedDropsPanel p = new ExpectedDropsPanel(new Tracker(Bundled.catalog(new Gson())), null, () -> RAIDS, s -> -1,
				() -> { }, () -> true, () -> { }, name -> 0L,
				() -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
			p.onActivate();
			for (ProfileState state : states)
			{
				p.setProfileState(state);
			}
			p.showBoss(ExpectedDropsPanel.ACCOUNT);
			out[0] = p.noteText();
			out[1] = p.verdictText();
			out[2] = p.isSetupShown();
		});
		return out;
	}

	@Test
	public void notLoggedInSaysToLogIn() throws Exception
	{
		Object[] s = shown(ProfileState.NOT_LOGGED_IN);
		assertTrue((String) s[0], ((String) s[0]).contains("Log in to see your account."));
		assertFalse("no setup steps before login", ((String) s[0]).contains("collection log"));
		assertEquals("Not logged in", s[1]);
		assertFalse("no welcome guide or checklist before login", (Boolean) s[2]);
	}

	@Test
	public void loadingSaysSo() throws Exception
	{
		Object[] s = shown(ProfileState.NOT_LOGGED_IN, ProfileState.LOADING);
		assertTrue((String) s[0], ((String) s[0]).contains("Loading your account"));
		assertFalse(((String) s[0]).contains("Log in"));
		assertEquals("Loading", s[1]);
		assertFalse((Boolean) s[2]);
	}

	@Test
	public void aFailedLoadNeverSaysLogIn() throws Exception
	{
		Object[] s = shown(ProfileState.LOADING, ProfileState.FAILED);
		assertTrue((String) s[0], ((String) s[0]).contains("couldn't load this account's saved data"));
		assertFalse((String) s[0], ((String) s[0]).contains("Log in to see"));
		assertEquals("Couldn't load", s[1]);
		assertFalse("no setup steps over data that didn't load", (Boolean) s[2]);
	}

	@Test
	public void afterLoadingTheSetupStepsAreBack() throws Exception
	{
		Object[] s = shown(ProfileState.NOT_LOGGED_IN, ProfileState.LOADING, ProfileState.LOADED);
		// a new account after login: the real setup instructions
		assertTrue((String) s[0], ((String) s[0]).contains("Open your collection log"));
		assertFalse(((String) s[0]).contains("Log in"));
		assertEquals("No kills yet", s[1]);
		assertTrue("welcome guide and checklist after login", (Boolean) s[2]);
	}

	@Test
	public void aPanelNobodyTellsShowsTheAccount() throws Exception
	{
		Object[] s = shown();
		assertEquals("No kills yet", s[1]);
	}
}
