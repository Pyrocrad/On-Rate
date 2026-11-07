package com.expecteddrops.raids;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Reading raid times from the Raid Data Tracker plugin's files. */
public class RaidDataImportTest
{
	private static final long ME = -3099035743809195948L;
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static String line(long account, boolean complete, long date, String extra)
	{
		return "{\"accountHash\":" + account + ",\"raidComplete\":" + complete + ",\"date\":" + date + "," + extra + "}";
	}

	private File log(File root, String folder, String... lines) throws IOException
	{
		File dir = new File(root, folder);
		assertTrue(dir.mkdirs() || dir.isDirectory());
		File file = new File(dir, "raid_tracker_data.log");
		Files.write(file.toPath(), String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
		return file;
	}

	@Test
	public void chambersTimesInTicksByMode() throws IOException
	{
		File root = tmp.newFolder("rdt");
		log(root, "cox",
			line(ME, true, 1000, "\"challengeMode\":false,\"raidTime\":1500"),
			line(ME, true, 2000, "\"challengeMode\":true,\"raidTime\":2400"));
		RaidDataImport.Times times = RaidDataImport.read(root, ME);
		Map<String, List<long[]>> cox = times.byBoss().get("Chambers of Xeric");
		assertEquals(900_000, cox.get("Normal").get(0)[1]);
		assertEquals(1000, cox.get("Normal").get(0)[0]);
		assertEquals(1_440_000, cox.get("Challenge Mode").get(0)[1]);
		assertEquals(2, times.count());
	}

	@Test
	public void readsTheOlderLayoutAndTheNamedFolder() throws IOException
	{
		File root = tmp.newFolder("rdt");
		log(root, "cox", line(ME, true, 1000, "\"raidTime\":1500"));
		log(root, "somebody/cox", line(ME, true, 5000, "\"raidTime\":1600"));
		log(root, "somebody/tob", line(ME, true, 6000, "\"raidTime\":2156,\"tobCompTime\":1430"));
		RaidDataImport.Times times = RaidDataImport.read(root, ME);
		assertEquals(2, times.byBoss().get("Chambers of Xeric").get("Normal").size());
		assertEquals(2156 * 600L, times.byBoss().get("Theatre of Blood").get("Normal").get(0)[1]);
	}

	@Test
	public void onlyThisAccountsFinishedRaids() throws IOException
	{
		File root = tmp.newFolder("rdt");
		log(root, "cox",
			line(ME, true, 1000, "\"raidTime\":1500"),
			line(ME, false, 2000, "\"raidTime\":1500"),
			line(12345L, true, 3000, "\"raidTime\":1500"),
			"not json at all",
			"{\"accountHash\":\"wrong type\"}",
			"",
			line(ME, true, 4000, "\"raidTime\":1500"));
		assertEquals(2, RaidDataImport.read(root, ME).count());
		assertEquals(1, RaidDataImport.read(root, 12345L).count());
	}

	@Test
	public void leavesOutRaidsWithNoPlausibleTime() throws IOException
	{
		File root = tmp.newFolder("rdt");
		log(root, "cox",
			line(ME, true, 1000, "\"raidTime\":-1"),
			line(ME, true, 2000, "\"raidTime\":100"),
			line(ME, true, 3000, "\"raidTime\":99999"),
			line(ME, true, 4000, "\"raidTime\":1500"));
		assertEquals(1, RaidDataImport.read(root, ME).count());
	}

	@Test
	public void tombsUsesTheTotalTimeAndModeFromTheRaidLevel() throws IOException
	{
		File root = tmp.newFolder("rdt");
		log(root, "toa",
			line(ME, true, 1000, "\"raidLevel\":300,\"raidTime\":2400,\"toaCompTime\":2100"),
			line(ME, true, 2000, "\"raidLevel\":150,\"raidTime\":2500,\"toaCompTime\":2200"),
			line(ME, true, 3000, "\"raidLevel\":50,\"raidTime\":1500,\"toaCompTime\":1300"),
			// only a challenge time, no total: left out
			line(ME, true, 4000, "\"raidLevel\":300,\"raidTime\":-1,\"toaCompTime\":2100"),
			// no raid level: the mode can't be told
			line(ME, true, 5000, "\"raidLevel\":-1,\"raidTime\":2400,\"toaCompTime\":2100"));
		Map<String, List<long[]>> toa = RaidDataImport.read(root, ME).byBoss().get("Tombs of Amascut");
		assertEquals(2400 * 600L, toa.get("Expert Mode").get(0)[1]);
		assertEquals(2500 * 600L, toa.get("Normal").get(0)[1]);
		assertEquals(1500 * 600L, toa.get("Entry Mode").get(0)[1]);
		assertEquals(3, toa.values().stream().mapToInt(List::size).sum());
	}

	@Test
	public void theatreHardModeAndUnknownFolder() throws IOException
	{
		File root = tmp.newFolder("rdt");
		log(root, "tob", line(ME, true, 1000, "\"challengeMode\":true,\"tobCompTime\":1400,\"raidTime\":2000"));
		log(root, "unknown", line(ME, true, 2000, "\"raidTime\":1500"));
		RaidDataImport.Times times = RaidDataImport.read(root, ME);
		assertEquals(2000 * 600L, times.byBoss().get("Theatre of Blood").get("Hard Mode").get(0)[1]);
		assertEquals(1, times.count());
	}

	@Test
	public void noPluginNoTimesAndNothingIsWritten() throws IOException
	{
		assertEquals(0, RaidDataImport.read(new File(tmp.getRoot(), "missing"), ME).count());
		assertEquals(0, RaidDataImport.read(null, ME).count());
		File root = tmp.newFolder("rdt");
		File file = log(root, "cox", line(ME, true, 1000, "\"raidTime\":1500"));
		long size = file.length();
		long changed = file.lastModified();
		assertEquals(0, RaidDataImport.read(root, -1).count());
		RaidDataImport.read(root, ME);
		assertEquals(size, file.length());
		assertEquals(changed, file.lastModified());
		assertNull(RaidDataImport.read(root, ME).byBoss().get("Tombs of Amascut"));
	}
}
