package com.onrate.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.raids.RaidDataImport;
import com.onrate.engine.tracking.Tracker;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.runelite.client.config.RuneScapeProfile;
import net.runelite.client.config.RuneScapeProfileType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Raid Data Tracker times are only ever added to the profile they were read for (audit F4). */
public class RaidTimesImportTest
{
	private static final long ME = 123456789L;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Runs handed-over tasks only when the test says so: the worker, or the client thread. */
	private static final class Tasks
	{
		final List<Runnable> list = new ArrayList<>();

		synchronized void add(Runnable r)
		{
			list.add(r);
		}

		void runAll()
		{
			List<Runnable> now;
			synchronized (this)
			{
				now = new ArrayList<>(list);
				list.clear();
			}
			now.forEach(Runnable::run);
		}
	}

	private Tasks worker;
	private Tasks clientThread;
	private AtomicReference<String> loaded;
	private RaidTimesImport importer;
	private Tracker tracker;
	private List<String> changed;
	private Supplier<RaidDataImport.Times> read;

	@Before
	public void setUp() throws IOException
	{
		worker = new Tasks();
		clientThread = new Tasks();
		loaded = new AtomicReference<>("A");
		importer = new RaidTimesImport(worker::add, clientThread::add, loaded::get);
		tracker = new Tracker(Bundled.catalog(new Gson()));
		changed = new ArrayList<>();
		// two Chambers raids of this account in Raid Data Tracker's log
		File root = tmp.newFolder("raid-data tracker");
		File dir = new File(root, "cox");
		assertTrue(dir.mkdirs());
		Files.write(new File(dir, "raid_tracker_data.log").toPath(), String.join("\n",
			"{\"accountHash\":" + ME + ",\"raidComplete\":true,\"date\":1000000,\"challengeMode\":false,\"raidTime\":1500}",
			"{\"accountHash\":" + ME + ",\"raidComplete\":true,\"date\":2000000,\"challengeMode\":false,\"raidTime\":1800}")
			.getBytes(StandardCharsets.UTF_8));
		read = () -> RaidDataImport.read(root, ME);
	}

	private int imported()
	{
		return tracker.importedRaidTimes("Chambers of Xeric", null);
	}

	@Test
	public void addedWhenTheSameProfileIsStillLoaded()
	{
		importer.start("A", read, tracker, changed::add);
		assertEquals("nothing happens on the client thread before the files are read", 0, imported());
		worker.runAll();
		assertEquals("nothing is added on the worker thread", 0, imported());
		clientThread.runAll();
		assertEquals(2, imported());
		assertEquals(Collections.singletonList("Chambers of Xeric"), changed);
	}

	@Test
	public void droppedWhenAnotherAccountWasLoadedMeanwhile()
	{
		importer.start("A", read, tracker, changed::add);
		worker.runAll();
		// A logs out and B logs in before the times reach the client thread; the tracker now holds B
		loaded.set("B");
		clientThread.runAll();
		assertEquals(0, imported());
		assertTrue(changed.isEmpty());
	}

	@Test
	public void droppedWhenTheSameAccountMovedToALeaguesProfile()
	{
		importer.start("main", read, tracker, changed::add);
		loaded.set("leagues");
		worker.runAll();
		clientThread.runAll();
		assertEquals(0, imported());
		assertTrue(changed.isEmpty());
	}

	@Test
	public void onlyAStandardProfileTakesImports()
	{
		List<RuneScapeProfile> profiles = Arrays.asList(
			new RuneScapeProfile("Me", RuneScapeProfileType.STANDARD, ME, "main"),
			new RuneScapeProfile("Me", RuneScapeProfileType.RAGING_ECHOES_LEAGUE, ME, "leagues"),
			new RuneScapeProfile("Me", RuneScapeProfileType.DEADMAN, ME, "dmm"));
		assertTrue(RaidTimesImport.importsInto("main", profiles));
		assertFalse("the log can't tell Leagues raids from the main game's", RaidTimesImport.importsInto("leagues", profiles));
		assertFalse(RaidTimesImport.importsInto("dmm", profiles));
		assertFalse("an unknown profile takes nothing", RaidTimesImport.importsInto("other", profiles));
		assertFalse(RaidTimesImport.importsInto(null, profiles));
	}

	@Test
	public void nothingIsAddedOnceThePluginHasStopped()
	{
		importer.start("A", read, tracker, changed::add);
		worker.runAll();
		importer.stop();
		clientThread.runAll();
		assertEquals(0, imported());
		assertTrue(changed.isEmpty());

		// and a read still in progress at shutdown never reaches the client thread
		RaidTimesImport other = new RaidTimesImport(worker::add, clientThread::add, loaded::get);
		other.start("A", read, tracker, changed::add);
		other.stop();
		worker.runAll();
		assertTrue(clientThread.list.isEmpty());
		assertEquals(0, imported());
	}

	@Test
	public void timesAlreadyThereAreNotAddedAgain()
	{
		// one of the two raids was imported before
		tracker.importRaidTimes("Chambers of Xeric", "Normal", Collections.singletonList(new long[]{1000000, 1500_000}));
		assertEquals(1, imported());
		importer.start("A", read, tracker, changed::add);
		worker.runAll();
		clientThread.runAll();
		assertEquals(2, imported());
	}

	@Test
	public void importingTwiceAddsEachTimeOnce()
	{
		// RuneLite loads on login and again when the profile is announced: two imports may overlap
		importer.start("A", read, tracker, changed::add);
		importer.start("A", read, tracker, changed::add);
		worker.runAll();
		clientThread.runAll();
		assertEquals(2, imported());
		assertEquals("only the import that added times reports a change", 1, changed.size());

		importer.start("A", read, tracker, changed::add);
		worker.runAll();
		clientThread.runAll();
		assertEquals(2, imported());
		assertEquals(1, changed.size());
	}
}
