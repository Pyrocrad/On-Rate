package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** The panel's Feedback link points at the GitHub issue chooser. */
public class ReportLinkTest
{
	@Test
	public void feedbackLinkOpensTheGithubIssueChooser()
	{
		assertEquals("https://github.com/Pyrocrad/On-Rate/issues/new/choose", ExpectedDropsPanel.REPORT_URL);
	}
}
