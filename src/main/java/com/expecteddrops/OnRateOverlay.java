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
 * Small box on the game screen while you kill a boss: verdict, uniques, your driest item
 * and the current trip. Hidden 10 minutes after your last kill.
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
		panelComponent.setPreferredSize(new Dimension(170, 0));
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
		panelComponent.getChildren().add(LineComponent.builder().left("Verdict").right(i.verdict).rightColor(i.verdictColor).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Uniques").right(i.uniques).build());
		if (i.driest != null)
		{
			panelComponent.getChildren().add(LineComponent.builder().left("Driest").right(i.driestItem).build());
			panelComponent.getChildren().add(LineComponent.builder().left("Dry for").right(i.driest)
				.rightColor(i.driestColor).build());
		}
		if (i.trip != null)
		{
			panelComponent.getChildren().add(LineComponent.builder().left("Trip").right(i.trip).rightColor(Color.WHITE).build());
		}
		return super.render(graphics);
	}
}
