package com.expecteddrops.ui;

import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The card look: runs of list rows go into one outlined card, everything else stays where it was. */
public class CardLookTest
{
	private static JPanel row()
	{
		JPanel row = new JPanel();
		row.setBorder(new EmptyBorder(Theme.HAIR, Theme.S, Theme.HAIR, Theme.S));
		return Card.row(row);
	}

	private static JPanel gap(int height)
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(1, height));
		return p;
	}

	private static JPanel host(Component... parts)
	{
		JPanel host = new JPanel();
		host.setLayout(new BoxLayout(host, BoxLayout.Y_AXIS));
		for (Component c : parts)
		{
			host.add(c);
		}
		return host;
	}

	@Test
	public void rowsWithSmallGapsBecomeOneCardWithLinesBetween()
	{
		JLabel header = new JLabel("ITEMS");
		JPanel a = row();
		JPanel b = row();
		JPanel c = row();
		JPanel host = host(header, gap(Theme.HAIR), a, gap(Theme.HAIR), b, gap(Theme.HAIR), c);
		Card.box(host);
		// the header, the gap before the first row, then the card
		assertEquals(3, host.getComponentCount());
		assertSame(header, host.getComponent(0));
		assertTrue(host.getComponent(2) instanceof Card);
		Card card = (Card) host.getComponent(2);
		// three rows and a line between each two: the gaps between the rows are gone
		assertEquals(5, card.getComponentCount());
		assertSame(a, card.getComponent(0));
		assertSame(b, card.getComponent(2));
		assertSame(c, card.getComponent(4));
		assertEquals(1, card.getComponent(1).getPreferredSize().height);
	}

	@Test
	public void aLabelOrBiggerGapBetweenRowsStartsANewCard()
	{
		JPanel host = host(row(), row(), new JLabel("LUCKIEST"), row(), gap(Theme.S), row());
		Card.box(host);
		// card, label, card, gap, card
		assertEquals(5, host.getComponentCount());
		assertTrue(host.getComponent(0) instanceof Card);
		assertTrue(host.getComponent(1) instanceof JLabel);
		assertTrue(host.getComponent(2) instanceof Card);
		assertFalse(host.getComponent(3) instanceof Card);
		assertTrue(host.getComponent(4) instanceof Card);
	}

	@Test
	public void aSummaryHeadingRowOpensTheCardOfItsPartRows()
	{
		JLabel heading = new JLabel("Average purple chance from points");
		heading.setBorder(new EmptyBorder(Theme.HAIR, 0, Theme.HAIR, 0));
		JPanel host = host(row(), row(), gap(Theme.S), Card.row(heading), gap(Theme.HAIR), row(), gap(Theme.HAIR), row());
		Card.box(host);
		// the rows above, a gap, then one card: the heading and its part rows
		assertEquals(3, host.getComponentCount());
		assertTrue(host.getComponent(0) instanceof Card);
		assertFalse(host.getComponent(1) instanceof Card);
		assertEquals(Theme.S, host.getComponent(1).getPreferredSize().height);
		Card summary = (Card) host.getComponent(2);
		// heading, line, row, line, row
		assertEquals(5, summary.getComponentCount());
		assertSame(heading, summary.getComponent(0));
		assertEquals(Theme.S, heading.getInsets().left);
	}

	@Test
	public void unmarkedPanelsAreLeftAlone()
	{
		JPanel trip = new JPanel();
		JPanel host = host(trip, gap(Theme.M));
		Card.box(host);
		assertEquals(2, host.getComponentCount());
		assertSame(trip, host.getComponent(0));
	}

	@Test
	public void aRowsOwnSeparatorLineGoesInsideTheCard()
	{
		JPanel line = new JPanel();
		EmptyBorder inner = new EmptyBorder(Theme.HAIR, Theme.S, Theme.HAIR, Theme.S);
		line.setBorder(BorderFactory.createCompoundBorder(new MatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR), inner));
		JPanel host = host(Card.row(line));
		Card.box(host);
		assertSame(inner, line.getBorder());
	}

	@Test
	public void rowsWithoutSidePaddingGetSomeInsideTheCard()
	{
		JPanel bare = new JPanel();
		bare.setBorder(new EmptyBorder(Theme.HAIR, 0, Theme.HAIR, 0));
		Card.box(host(Card.row(bare)));
		assertTrue(bare.getBorder() instanceof CompoundBorder);
		assertEquals(Theme.S, bare.getInsets().left);
	}

	@Test
	public void aGridIsPaddedOnEverySide()
	{
		JPanel grid = Card.gridRow(new JPanel());
		Card.box(host(grid));
		assertEquals(Theme.M, grid.getInsets().top);
		assertEquals(Theme.S, grid.getInsets().left);
	}

	@Test
	public void turnedOffACardIsAPlainOpaquePanel()
	{
		Card card = new Card();
		assertFalse(card.isOpaque());
		card.setBoxed(false);
		assertTrue(card.isOpaque());
		assertFalse(card.isBoxed());
	}

	@Test
	public void aPillCanHaveAFixedWidthForAColumn()
	{
		LuckPill pill = new LuckPill("50%", Palette.luck(0.5), 46);
		assertEquals(46, pill.getPreferredSize().width);
		assertEquals(46, pill.getMaximumSize().width);
		assertTrue(new LuckPill("about average", Palette.luck(0.5), 0).getPreferredSize().width > 46);
	}
}
