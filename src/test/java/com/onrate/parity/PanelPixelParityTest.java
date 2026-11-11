package com.onrate.parity;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.Bundled;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import javax.imageio.ImageIO;
import net.runelite.client.game.ItemManager;
import org.junit.Test;

/**
 * UI regression guard. The original panel (running on the original engine) and the integrated
 * panel (the same UI classes, running on the new engine) are built from the same simulated player
 * and drawn off screen, page by page, in every item view. The pictures must be identical, pixel for
 * pixel. A difference saves both pictures and a diff to build/pixel-diff.
 */
public class PanelPixelParityTest
{
	private static final int WIDTH = 225;
	private static final String[] VIEWS = {"TARGET_BARS", "DELVE_BARS", "COMPACT", "RINGS", "GAUGE"};
	private static int pages;
	private static long pixels;

	/** One way of drawing the panel: an item view and the display switches. */
	private static final class Look
	{
		final String view;
		final boolean gp;
		final boolean graph;
		final boolean trip;
		final boolean colourBlind;

		Look(String view, boolean gp, boolean graph, boolean trip, boolean colourBlind)
		{
			this.view = view;
			this.gp = gp;
			this.graph = graph;
			this.trip = trip;
			this.colourBlind = colourBlind;
		}

		@Override
		public String toString()
		{
			return view + (gp ? "" : "-noGP") + (graph ? "-graph" : "") + (colourBlind ? "-cb" : "");
		}
	}

	private static String ui(Side side)
	{
		return side == Side.ORIGINAL ? "legacy.expecteddrops.ui." : "com.expecteddrops.ui.";
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object panel(Side side, Side.Settings settings, Look look) throws Exception
	{
		Class<?> panelClass = Class.forName(ui(side) + "ExpectedDropsPanel");
		Class<?> itemView = Class.forName(ui(side) + "ItemView");
		Class<?> options = Class.forName(ui(side) + "ViewOptions");
		Object view = Enum.valueOf((Class<Enum>) itemView, look.view);
		Object opts = options.getConstructor(itemView, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class)
			.newInstance(view, look.gp, look.graph, look.trip, look.colourBlind, true);
		Object raids = side.raidSettings(settings);
		Constructor<?> c = panelClass.getConstructor(side.cls("tracking", "Tracker"), ItemManager.class, Supplier.class, ToIntFunction.class,
			Runnable.class, BooleanSupplier.class, Runnable.class, ToLongFunction.class, Supplier.class);
		Object p = c.newInstance(side.tracker, null, (Supplier<Object>) () -> raids, (ToIntFunction<String>) s -> -1, (Runnable) () ->
		{
		}, (BooleanSupplier) () -> false, (Runnable) () ->
		{
		}, PRICES, (Supplier<Object>) () -> opts);
		if (side == Side.REBUILT)
		{
			// the raid sections and PB tags are new: the original panel has none, so they are
			// left out of the comparison (RaidSectionsTest covers them)
			set(p, "raidExtras", false);
			// the card look (lists in outlined cards, one pill and tab style) is new too: the comparison draws
			// the old look, so it still checks every number and row pixel for pixel (CardLookTest covers the cards)
			set(p, "boxes", false);
			// the line under the verdict taking turns with other facts is new: the original shows one line
			set(p, "moreFacts", false);
			// the View buttons (boss pages' ITEMS line, account Sort row) are new: the comparison draws the old layout
			// (ViewChipTest covers the button)
			set(p, "viewButtons", false);
		}
		panelClass.getMethod("onActivate").invoke(p);
		return p;
	}

	private static final ToLongFunction<String> PRICES = name ->
	{
		int h = Math.abs(name.toLowerCase().hashCode());
		return h % 7 == 0 ? 0 : (h % 5000) * 1000L + 100;
	};

	private static void set(Object target, String field, Object value) throws Exception
	{
		Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void setEnum(Object target, String field, String nested, String value) throws Exception
	{
		Class<?> type = Class.forName(target.getClass().getName() + "$" + nested);
		set(target, field, Enum.valueOf((Class<Enum>) type, value));
	}

	private static void layoutAll(Component c)
	{
		if (c instanceof Container)
		{
			((Container) c).doLayout();
			for (Component child : ((Container) c).getComponents())
			{
				layoutAll(child);
			}
		}
	}

	private static BufferedImage render(Object panelObject)
	{
		Container panel = (Container) panelObject;
		panel.setSize(WIDTH, 6000);
		layoutAll(panel);
		int height = Math.max(200, Math.min(6000, panel.getPreferredSize().height));
		panel.setSize(WIDTH, height);
		layoutAll(panel);
		layoutAll(panel);
		BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		panel.printAll(g);
		g.dispose();
		return image;
	}

	/** Draws the same page on both panels, one after the other: the original's picture, then the integrated one's. */
	private static BufferedImage[] draw(Object[] panels, PageSetter page) throws Exception
	{
		BufferedImage[] shots = new BufferedImage[2];
		for (int k = 0; k < 2; k++)
		{
			Object panel = panels[k];
			int index = k;
			// drawn on the Swing thread, as in the client, and only once the deferred re-layouts the
			// original UI queues (a tile whose text wrapped lays itself out again "later") have run
			onSwing(() ->
			{
				page.show(panel);
				panel.getClass().getMethod("refresh").invoke(panel);
				render(panel);
			});
			settle();
			onSwing(() -> render(panel));
			settle();
			onSwing(() -> shots[index] = render(panel));
		}
		return shots;
	}

	/** Pixels that differ between two pictures: {count, first x, first y}; count -1 when the sizes differ. */
	private static int[] difference(BufferedImage a, BufferedImage b)
	{
		if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
		{
			return new int[]{-1, -1, -1};
		}
		int differing = 0;
		int firstX = -1;
		int firstY = -1;
		for (int y = 0; y < a.getHeight(); y++)
		{
			for (int x = 0; x < a.getWidth(); x++)
			{
				if (a.getRGB(x, y) != b.getRGB(x, y) && differing++ == 0)
				{
					firstX = x;
					firstY = y;
				}
			}
		}
		return new int[]{differing, firstX, firstY};
	}

	/** Shows the same page on both panels and compares the pictures. */
	private static void compare(String name, Object[] panels, PageSetter page) throws Exception
	{
		long minuteBefore = System.currentTimeMillis() / 60_000;
		BufferedImage[] shots = draw(panels, page);
		int[] diff = difference(shots[0], shots[1]);
		// The trip card reads the clock ("Idle 12 min"): when the minute turns between the two pictures, the
		// same page says 12 on one and 13 on the other. Only then, draw both once more, well inside a minute:
		// a real difference shows again, a clock tick doesn't.
		if (diff[0] != 0 && System.currentTimeMillis() / 60_000 != minuteBefore)
		{
			shots = draw(panels, page);
			diff = difference(shots[0], shots[1]);
		}
		pages++;
		BufferedImage a = shots[0];
		BufferedImage b = shots[1];
		if (diff[0] >= 0)
		{
			pixels += (long) a.getWidth() * a.getHeight();
		}
		int differing = diff[0];
		int firstX = diff[1];
		int firstY = diff[2];
		if (differing == 0 && name.startsWith("seed0-TARGET_BARS-"))
		{
			// a sample of pages side by side (original | integrated) for people to look at
			File dir = new File(System.getProperty("onrate.pairs", "build/pixel-parity"));
			dir.mkdirs();
			BufferedImage pair = new BufferedImage(a.getWidth() * 2 + 6, a.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = pair.createGraphics();
			g.setColor(new java.awt.Color(220, 138, 0));
			g.fillRect(0, 0, pair.getWidth(), pair.getHeight());
			g.drawImage(a, 0, 0, null);
			g.drawImage(b, a.getWidth() + 6, 0, null);
			g.dispose();
			ImageIO.write(pair, "png", new File(dir, name.substring("seed0-TARGET_BARS-".length()).replaceAll("[^A-Za-z0-9.-]", "_") + ".png"));
		}
		if (differing != 0)
		{
			File dir = new File("build/pixel-diff");
			dir.mkdirs();
			String file = name.replaceAll("[^A-Za-z0-9.-]", "_");
			ImageIO.write(a, "png", new File(dir, file + "-original.png"));
			ImageIO.write(b, "png", new File(dir, file + "-integrated.png"));
			fail(name + ": " + (differing < 0 ? "sizes differ " + a.getWidth() + "x" + a.getHeight() + " vs " + b.getWidth() + "x" + b.getHeight()
				: differing + " pixels differ, first at " + firstX + "," + firstY) + " (pictures in build/pixel-diff)");
		}
	}

	private interface SwingTask
	{
		void run() throws Exception;
	}

	private static void onSwing(SwingTask task) throws Exception
	{
		Exception[] failed = new Exception[1];
		javax.swing.SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				task.run();
			}
			catch (Exception e)
			{
				failed[0] = e;
			}
		});
		if (failed[0] != null)
		{
			throw failed[0];
		}
	}

	/** Lets everything already queued on the Swing thread run (twice: a run can queue another). */
	private static void settle() throws Exception
	{
		for (int i = 0; i < 3; i++)
		{
			javax.swing.SwingUtilities.invokeAndWait(() ->
			{
			});
		}
	}

	private interface PageSetter
	{
		void show(Object panel) throws Exception;
	}

	private static void account(Object p, int tab, int topMode) throws Exception
	{
		set(p, "accountTab", tab);
		set(p, "topMode", topMode);
		p.getClass().getMethod("showBoss", String.class).invoke(p, "Account overview");
	}

	private static void boss(Object p, String name) throws Exception
	{
		set(p, "historyOpen", true);
		p.getClass().getMethod("showBoss", String.class).invoke(p, name);
	}

	/** Plays one simulated player into both trackers. */
	private static Sim play(long seed, BossCatalog catalog, List<BossDefinition> bosses, int offset) throws Exception
	{
		Sim sim = new Sim(seed, catalog);
		for (int i = 0; i < 6; i++)
		{
			sim.playBoss(bosses.get((offset + i * 9) % bosses.size()));
		}
		sim.sprinkle();
		for (Side side : new Side[]{Side.ORIGINAL, Side.REBUILT})
		{
			side.newTracker();
			side.call("setSettings", sim.settings);
			AtomicInteger team = new AtomicInteger();
			side.call("setTeamSizeDetectorFor", (ToIntFunction<String>) b -> team.get());
			for (Sim.Op op : sim.ops)
			{
				if (op.method.equals("SET_TEAM"))
				{
					team.set((Integer) op.args[0]);
				}
				else if (op.method.equals("SET_SETTINGS"))
				{
					side.call("setSettings", op.args[0]);
				}
				else if (!op.method.equals("OBSERVE"))
				{
					try
					{
						side.apply(op);
					}
					catch (RuntimeException e)
					{
						// identical on both, checked by the replay test
					}
				}
			}
		}
		return sim;
	}

	@Test
	public void everyPageLooksTheSameInEveryView() throws Exception
	{
		BossCatalog catalog = Bundled.catalog(new com.google.gson.Gson());
		List<BossDefinition> bosses = new ArrayList<>(catalog.getBosses());
		List<Look> looks = new ArrayList<>();
		for (String v : VIEWS)
		{
			looks.add(new Look(v, true, false, true, false));
		}
		looks.add(new Look("TARGET_BARS", false, true, false, true));
		looks.add(new Look("COMPACT", true, true, true, true));
		for (int s = 0; s < 4; s++)
		{
			Sim sim = play(70000 + s, catalog, bosses, s * 13);
			for (Look look : looks)
			{
				Object[] panels = new Object[2];
				Side.Settings st = sim.settings;
				onSwing(() ->
				{
					panels[0] = panel(Side.ORIGINAL, st, look);
					panels[1] = panel(Side.REBUILT, st, look);
				});
				String tag = "seed" + s + "-" + look;
				compare(tag + "-account-bosses", panels, p -> account(p, 0, 0));
				compare(tag + "-account-recent", panels, p -> account(p, 2, 0));
				for (int mode = 0; mode <= 4; mode++)
				{
					int m = mode;
					compare(tag + "-account-items-" + mode, panels, p -> account(p, 1, m));
				}
				if (look.view.equals("TARGET_BARS") && look.gp)
				{
					for (String tv : new String[]{"ITEMS", "CHART", "PETS", "RAIDS"})
					{
						compare(tag + "-time-" + tv, panels, p ->
						{
							setEnum(p, "timeView", "TimeView", tv);
							account(p, 1, 4);
						});
					}
				}
				for (String name : new java.util.LinkedHashSet<>(sim.touched))
				{
					compare(tag + "-boss-" + name, panels, p -> boss(p, name));
				}
			}
		}
		System.out.println("pixel parity: " + pages + " pages, " + pixels + " pixels, all identical");
		assertTrue(pages > 100);
	}
}
