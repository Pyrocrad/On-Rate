package com.expecteddrops;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.function.Supplier;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Small box on the game screen while you kill a boss: this trip (kills, expected, luck), the account's verdict and your driest item. Hidden 10 minutes after your last kill.
 */
class OnRateOverlay extends OverlayPanel
{
	private static final long SHOW_MILLIS = 10 * 60_000;

	private final ExpectedDropsConfig config;
	private final Supplier<OverlayInfo> info;

	OnRateOverlay(ExpectedDropsPlugin plugin, ExpectedDropsConfig config, Supplier<OverlayInfo> info)
	{
		super(plugin);
		this.config = config;
		this.info = info;
		setPosition(OverlayPosition.TOP_LEFT);
		// wide enough for an item name and its dry streak on one line each
		panelComponent.setPreferredSize(new Dimension(190, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		OverlayInfo i = info.get();
		if (!config.showOverlay() || i == null || System.currentTimeMillis() - i.lastKillMillis > SHOW_MILLIS)
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("On Rate · " + i.boss)
			.color(ColorScheme.BRAND_ORANGE)
			.build());
		// this trip first (what you compare with a friend), then the account in two lines
		panelComponent.getChildren().add(LineComponent.builder().left("This trip").right(i.trip).rightColor(Color.WHITE).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Expected").right(i.tripExpected).rightColor(Color.WHITE).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Trip luck").right(i.tripLuck).rightColor(i.tripLuckColor).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Account").right(i.account).rightColor(i.accountColor).build());
		if (i.driest != null)
		{
			panelComponent.getChildren().add(LineComponent.builder().left("Driest").right(i.driest)
				.rightColor(i.driestColor).build());
		}
		return super.render(graphics);
	}
}
