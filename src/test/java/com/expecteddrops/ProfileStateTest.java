package com.expecteddrops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.expecteddrops.ui.ExpectedDropsPanel.ProfileState;
import org.junit.Test;

/** What the panel says after each kind of load attempt (polish follow-up, item 3). */
public class ProfileStateTest
{
	@Test
	public void notLoggedIn()
	{
		// RuneLite started at the login screen: no profile, nothing loaded
		assertEquals(ProfileState.NOT_LOGGED_IN, ExpectedDropsPlugin.profileState(false, false, false, false, false));
	}

	@Test
	public void loadingWhileLoggedInWithoutAProfileYet()
	{
		// logged in, RuneLite hasn't named the account's profile yet
		assertEquals(ProfileState.LOADING, ExpectedDropsPlugin.profileState(false, false, false, false, true));
	}

	@Test
	public void failedNeverSaysLogIn()
	{
		assertEquals(ProfileState.FAILED, ExpectedDropsPlugin.profileState(false, true, false, true, true));
		// still failed when the attempt comes at the login screen
		assertEquals(ProfileState.FAILED, ExpectedDropsPlugin.profileState(false, true, false, false, false));
	}

	@Test
	public void loaded()
	{
		assertEquals(ProfileState.LOADED, ExpectedDropsPlugin.profileState(true, false, true, true, true));
	}

	@Test
	public void anAccountAlreadyLoadedIsLeftAlone()
	{
		// the same profile again, or logged out with the last account's data still shown: no change
		assertNull(ExpectedDropsPlugin.profileState(false, false, true, true, true));
		assertNull(ExpectedDropsPlugin.profileState(false, false, true, false, false));
	}
}
