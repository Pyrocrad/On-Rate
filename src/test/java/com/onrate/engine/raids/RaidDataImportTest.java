package com.onrate.engine.raids;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import net.runelite.client.util.Filepath;
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

	private static Filepath fp(File f)
	{
		return Filepath.Unchecked.getRooted(f.toPath());
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
	public void chambersTimesInSecondsByMode() throws IOException
	{
		File root = tmp.newFolder("rdt");
		log(root, "cox",
			line(ME, true, 1000, "\"challengeMode\":false,\"raidTime\":1500"),
			line(ME, true, 2000, "\"challengeMode\":true,\"raidTime\":2400"));
		RaidDataImport.Times times = RaidDataImport.read(fp(root), ME);
		Map<String, List<long[]>> cox = times.byBoss().get("Chambers of Xeric");
		assertEquals(1_500_000, cox.get("Normal").get(0)[1]);
		assertEquals(1000, cox.get("Normal").get(0)[0]);
		assertEquals(2_400_000, cox.get("Challenge Mode").get(0)[1]);
		assertEquals(2, times.count());
	}

	@Test
	public void readsTheOlderLayoutAndTheNamedFolder() throws IOException
	{
		File root = tmp.newFolder("raid-data tracker");
		log(root, "cox", line(ME, true, 1000, "\"raidTime\":1500"));
		log(root, "somebody/cox", line(ME, true, 5000, "\"raidTime\":1600"));
		RaidDataImport.Times times = RaidDataImport.read(fp(root), ME);
		assertEquals(2, times.byBoss().get("Chambers of Xeric").get("Normal").size());
		// the other plugin's folder has a space in its name
		assertEquals("raid-data tracker", RaidDataImport.FOLDER);
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
		assertEquals(2, RaidDataImport.read(fp(root), ME).count());
		assertEquals(1, RaidDataImport.read(fp(root), 12345L).count());
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
		assertEquals(1, RaidDataImport.read(fp(root), ME).count());
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
		Map<String, List<long[]>> toa = RaidDataImport.read(fp(root), ME).byBoss().get("Tombs of Amascut");
		assertEquals(2400_000L, toa.get("Expert Mode").get(0)[1]);
		assertEquals(2500_000L, toa.get("Normal").get(0)[1]);
		assertEquals(1500_000L, toa.get("Entry Mode").get(0)[1]);
		assertEquals(3, toa.values().stream().mapToInt(List::size).sum());
	}

	@Test
	public void theatreOfBloodAndTheUnknownFolderAreLeftOut() throws IOException
	{
		File root = tmp.newFolder("rdt");
		// the other plugin doesn't record the Theatre mode, so those times can't be told apart
		log(root, "tob", line(ME, true, 1000, "\"tobCompTime\":1400,\"raidTime\":2000"));
		log(root, "unknown", line(ME, true, 2000, "\"raidTime\":1500"));
		log(root, "cox", line(ME, true, 3000, "\"raidTime\":1500"));
		RaidDataImport.Times times = RaidDataImport.read(fp(root), ME);
		assertNull(times.byBoss().get("Theatre of Blood"));
		assertEquals(1, times.count());
	}

	@Test
	public void noPluginNoTimesAndNothingIsWritten() throws IOException
	{
		assertEquals(0, RaidDataImport.read(fp(new File(tmp.getRoot(), "missing")), ME).count());
		assertEquals(0, RaidDataImport.read(null, ME).count());
		File root = tmp.newFolder("rdt");
		File file = log(root, "cox", line(ME, true, 1000, "\"raidTime\":1500"));
		long size = file.length();
		long changed = file.lastModified();
		assertEquals(0, RaidDataImport.read(fp(root), -1).count());
		RaidDataImport.read(fp(root), ME);
		assertEquals(size, file.length());
		assertEquals(changed, file.lastModified());
		assertNull(RaidDataImport.read(fp(root), ME).byBoss().get("Tombs of Amascut"));
	}

	@Test
	public void wholeRaidsWithPointsTeamLevelAndYourUnique() throws IOException
	{
		File root = tmp.newFolder("rdt");
		log(root, "cox",
			line(ME, true, 1000, "\"challengeMode\":false,\"raidTime\":1301,\"totalPoints\":37189,\"personalPoints\":10102,"
				+ "\"teamSize\":2,\"completionCount\":241,\"specialLoot\":\"Twisted bow\",\"specialLootInOwnName\":true"),
			// a teammate's purple is not yours; a raid without a time still counts for its points
			line(ME, true, 2000, "\"challengeMode\":true,\"totalPoints\":40000,\"personalPoints\":40000,"
				+ "\"teamSize\":1,\"completionCount\":7,\"specialLoot\":\"Kodai insignia\",\"specialLootInOwnName\":false"),
			// no completion count or no points: can't be placed
			line(ME, true, 3000, "\"challengeMode\":false,\"raidTime\":1500,\"personalPoints\":20000,\"teamSize\":1"),
			line(ME, true, 4000, "\"challengeMode\":false,\"raidTime\":1500,\"completionCount\":250,\"teamSize\":1"));
		log(root, "toa",
			line(ME, true, 5000, "\"raidLevel\":225,\"raidTime\":2410,\"totalPoints\":26596,\"personalPoints\":26595,"
				+ "\"teamSize\":3,\"completionCount\":40,\"specialLoot\":\"Osmumten\u0027s fang\",\"specialLootInOwnName\":true"));
		RaidDataImport.Times times = RaidDataImport.read(fp(root), ME);

		List<RaidDataImport.Raid> cox = times.raids().get("Chambers of Xeric");
		assertEquals(2, cox.size());
		RaidDataImport.Raid normal = cox.get(0);
		assertEquals("Normal", normal.part);
		assertEquals(241, normal.kc);
		assertEquals(1000, normal.when);
		assertEquals(1_301_000, normal.millis);
		assertEquals(2, normal.teamSize);
		assertEquals(10_102, normal.personalPoints);
		assertEquals(37_189, normal.totalPoints);
		assertEquals(-1, normal.raidLevel);
		assertEquals("Twisted bow", normal.unique);
		RaidDataImport.Raid cm = cox.get(1);
		assertEquals("Challenge Mode", cm.part);
		assertEquals(-1, cm.millis);
		assertNull(cm.unique);

		RaidDataImport.Raid toa = times.raids().get("Tombs of Amascut").get(0);
		assertEquals("Normal", toa.part);
		assertEquals(225, toa.raidLevel);
		assertEquals("Osmumten's fang", toa.unique);
		// the times are read as before: every Normal line with a time, placed as a whole raid or not
		assertEquals(3, times.byBoss().get("Chambers of Xeric").get("Normal").size());
	}
}
