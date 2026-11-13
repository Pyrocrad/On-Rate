package com.expecteddrops.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The notes on the Single drops and Time pages are as wide as the panel's text, so they do not clip. */
public class NoteWidthTest
{
	private static final Path SOURCE = Paths.get("src", "main", "java", "com", "expecteddrops", "ui", "ExpectedDropsPanel.java");

	private static String source() throws Exception
	{
		return new String(Files.readAllBytes(SOURCE), StandardCharsets.UTF_8);
	}

	@Test
	public void noNoteIsSetToAFixedWidthWiderThanThePanel() throws Exception
	{
		String source = source();
		assertFalse("a note still uses width: 190px", source.contains("width: 190px"));
		assertFalse("a note still uses width: 170px", source.contains("width: 170px"));
	}

	@Test
	public void notesUseTheTextWidthForPlayers() throws Exception
	{
		String source = source();
		assertTrue(source.contains("private boolean fixedNoteWidths = true;"));
		assertTrue(source.contains("return fixedNoteWidths ? Theme.TEXT_WIDTH : legacyPx;"));
		assertTrue(Theme.TEXT_WIDTH < 170);
	}
}
