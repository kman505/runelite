/*
 * Copyright (c) 2017, Seth <Sethtroll3@gmail.com>
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.ui.overlay.infobox;

import com.google.common.base.Strings;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuOpcode;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.InfoBoxMenuClicked;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.InfoBoxComponent;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

@Singleton
public class InfoBoxOverlay extends OverlayPanel
{
	private static final int GAP = 1;
	private static final int DEFAULT_WRAP_COUNT = 4;

	private final InfoBoxManager infoboxManager;
	private final TooltipManager tooltipManager;
	private final Client client;
	private final RuneLiteConfig config;
	private final EventBus eventBus;

	private InfoBoxComponent hoveredComponent;

	@Inject
	private InfoBoxOverlay(
		InfoBoxManager infoboxManager,
		TooltipManager tooltipManager,
		Client client,
		RuneLiteConfig config,
		EventBus eventBus)
	{
		this.tooltipManager = tooltipManager;
		this.infoboxManager = infoboxManager;
		this.client = client;
		this.config = config;
		this.eventBus = eventBus;
		setPosition(OverlayPosition.TOP_LEFT);
		setClearChildren(false);

		panelComponent.setWrap(true);
		panelComponent.setBackgroundColor(null);
		panelComponent.setBorder(new Rectangle());
		panelComponent.setGap(new Point(GAP, GAP));

		eventBus.subscribe(MenuOptionClicked.class, this, this::onMenuOptionClicked);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final List<InfoBox> infoBoxes = infoboxManager.getInfoBoxes();

		final boolean menuOpen = client.isMenuOpen();
		if (!menuOpen)
		{
			hoveredComponent = null;
		}

		if (infoBoxes.isEmpty())
		{
			return null;
		}

		// Set preferred size to the size of DEFAULT_WRAP_COUNT infoboxes, including the padding - which is applied
		// to the last infobox prior to wrapping too.
		panelComponent.setPreferredSize(new Dimension(DEFAULT_WRAP_COUNT * (config.infoBoxSize() + GAP), DEFAULT_WRAP_COUNT * (config.infoBoxSize() + GAP)));
		panelComponent.setOrientation(config.infoBoxVertical()
			? ComponentOrientation.VERTICAL
			: ComponentOrientation.HORIZONTAL);

		for (InfoBox box : infoBoxes)
		{
			if (!box.render())
			{
				continue;
			}

			final String text = box.getText();
			final Color color = box.getTextColor();

			final InfoBoxComponent infoBoxComponent = new InfoBoxComponent();
			infoBoxComponent.setText(text);
			if (color != null)
			{
				infoBoxComponent.setColor(color);
			}
			infoBoxComponent.setImage(box.getScaledImage());
			infoBoxComponent.setTooltip(box.getTooltip());
			infoBoxComponent.setPreferredSize(new Dimension(config.infoBoxSize(), config.infoBoxSize()));
			infoBoxComponent.setBackgroundColor(config.overlayBackgroundColor());
			infoBoxComponent.setInfoBox(box);
			panelComponent.getChildren().add(infoBoxComponent);
		}

		final Dimension dimension = super.render(graphics);

		// Handle tooltips
		final Point mouse = new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY());

		for (final LayoutableRenderableEntity child : panelComponent.getChildren())
		{
			final InfoBoxComponent component = (InfoBoxComponent) child;

			// Create intersection rectangle
			final Rectangle intersectionRectangle = new Rectangle(component.getBounds());
			intersectionRectangle.translate(getBounds().x, getBounds().y);

			if (intersectionRectangle.contains(mouse))
			{
				final String tooltip = component.getTooltip();
				if (!Strings.isNullOrEmpty(tooltip))
				{
					tooltipManager.add(new Tooltip(tooltip));
				}

				if (!menuOpen)
				{
					hoveredComponent = component;
				}
				break;
			}
		}

		panelComponent.getChildren().clear();
		return dimension;
	}

	@Override
	public List<OverlayMenuEntry> getMenuEntries()
	{
		// we dynamically build the menu options based on which infobox is hovered
		return hoveredComponent == null ? Collections.emptyList() : hoveredComponent.getInfoBox().getMenuEntries();
	}

	public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked)
	{
		if (menuOptionClicked.getMenuOpcode() != MenuOpcode.RUNELITE_INFOBOX)
		{
			return;
		}

		InfoBox infoBox = hoveredComponent.getInfoBox();
		infoBox.getMenuEntries().stream()
			.filter(me -> me.getOption().equals(menuOptionClicked.getOption()))
			.findAny().ifPresent(overlayMenuEntry -> eventBus.post(InfoBoxMenuClicked.class, new InfoBoxMenuClicked(overlayMenuEntry, infoBox)));
	}
}