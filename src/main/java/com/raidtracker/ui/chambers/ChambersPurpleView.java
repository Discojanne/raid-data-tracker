package com.raidtracker.ui.chambers;

import com.raidtracker.RaidTracker;
import com.raidtracker.ui.RaidUniques;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Points, purple rate, history, deaths, and tracked drops for every logged Chambers raid.
 */
public class ChambersPurpleView extends JPanel {
	private static final Color GREEN = new Color(80, 220, 120);
	private static final Color PURPLE = new Color(196, 96, 255);
	private static final Color RED = new Color(255, 90, 90);
	private static final Color CYAN = new Color(80, 210, 255);
	private static final Color GOLD = new Color(232, 196, 120);
	private static final Color DRY = new Color(58, 58, 58);
	private static final Color HAIR = ColorScheme.LIGHT_GRAY_COLOR.darker();
	private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color BAR = ColorScheme.DARKER_GRAY_COLOR.darker();
	private static final Font VALUE = new Font(Font.SANS_SERIF, Font.BOLD, 16);
	private static final Font COUNT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
	private static final int SQUARE = 10;
	private static final int PITCH = 13;

	private final ItemManager itemManager;

	public ChambersPurpleView(ItemManager itemManager, List<RaidTracker> raids) {
		this.itemManager = itemManager;
		List<RaidTracker> saved = raids == null ? java.util.Collections.<RaidTracker>emptyList() : raids;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		// Match the title row's center alignment. A left-aligned page in that box only receives the right half.
		setAlignmentX(CENTER_ALIGNMENT);
		setBorder(new EmptyBorder(2, 0, 10, 0));
		ChambersPurples board = ChambersPurples.of(saved);
		if (board.isEmpty()) {
			add(notice("No completed raids logged yet."));
			return;
		}
		add(header("Summary", "All raids"));
		add(summary(board));
		add(header("Items", null));
		add(items(board));
		add(header("History", null));
		add(new HistoryStrip(board));
		add(header("Deaths", null));
		add(deaths(board));
		if (!board.deaths.isEmpty()) {
			add(deathRows(board));
		}
		if (!board.tracked.isEmpty()) {
			add(header("Tracked items", null));
			add(tracked(board));
		}
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
	}

	@Override
	public Dimension getMinimumSize() {
		return new Dimension(0, getPreferredSize().height);
	}

	private JPanel summary(ChambersPurples board) {
		JPanel grid = new JPanel(new GridLayout(0, 2, 4, 4));
		grid.setOpaque(false);
		grid.add(stat("Raids", Integer.toString(board.raids), Color.WHITE));
		String rate = board.expected > 0 ? String.format(Locale.US, "1/%.2f", board.rate()) : "--";
		grid.add(stat("Rate", rate, Color.WHITE));
		grid.add(stat("Regular", Integer.toString(board.regular), CYAN));
		grid.add(stat("CM", Integer.toString(board.challengeMode), GOLD));
		grid.add(stat("Expected", String.format(Locale.US, "%.1f", board.expected), Color.WHITE));
		grid.add(stat("Actual", Integer.toString(board.actual), Color.WHITE));
		double scrollPct = board.actual == 0 ? 0 : board.scrolls * 100.0 / board.actual;
		grid.add(stat("Scrolls", String.format(Locale.US, "%.0f%%", scrollPct), Color.WHITE));
		grid.add(stat("Diff", String.format(Locale.US, "%+.1f", board.diff()), diffColor(board.diff())));
		stretch(grid);

		JPanel dry = new JPanel(new GridLayout(1, 3, 4, 0));
		dry.setOpaque(false);
		dry.add(tip(stat("Dry now", Integer.toString(board.dryNow), Color.WHITE), "Raids since your last purple"));
		dry.add(tip(stat("Longest", Integer.toString(board.longestDry), Color.WHITE), "Longest dry streak"));
		dry.add(tip(stat("Avg dry", Integer.toString(board.averageDry), Color.WHITE), "Average dry streak"));
		stretch(dry);

		JPanel points = new JPanel(new GridLayout(1, 2, 4, 0));
		points.setOpaque(false);
		points.add(stat("Avg points", grouped(Math.round(board.averagePoints)), Color.WHITE));
		points.add(stat("Total points", grouped(board.points), GOLD));
		stretch(points);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		body.add(grid);
		body.add(Box.createRigidArea(new Dimension(0, 4)));
		body.add(dry);
		body.add(Box.createRigidArea(new Dimension(0, 4)));
		body.add(points);
		stretch(body);
		return body;
	}

	private JPanel deaths(ChambersPurples board) {
		JPanel grid = new JPanel(new GridLayout(0, 2, 4, 4));
		grid.setOpaque(false);
		grid.add(stat("Points lost", grouped(board.pointsLost), Color.WHITE));
		grid.add(stat("As purples", String.format(Locale.US, "%.2f", board.lostPurples()), Color.WHITE));
		String raids = board.averagePoints > 0 ? String.format(Locale.US, "%.2f", board.lostRaids()) : "--";
		grid.add(stat("As raids", raids, Color.WHITE));
		String rate = board.raids > 0 ? String.format(Locale.US, "%.1f%%", board.deathRate() * 100.0) : "--";
		grid.add(stat("Death rate", rate, Color.WHITE));
		stretch(grid);
		return grid;
	}

	private JPanel deathRows(ChambersPurples board) {
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		for (int i = 0; i < board.deaths.size(); i++) {
			ChambersPurples.Death death = board.deaths.get(i);
			JPanel row = new JPanel(new BorderLayout(10, 0));
			row.setBackground(CARD);
			row.setBorder(new CompoundBorder(
				new MatteBorder(0, 0, 1, 0, HAIR),
				new EmptyBorder(5, 8, 5, 8)
			));
			String kc = "--".equals(death.kc) ? "--" : (death.challengeMode ? "CM " : "KC ") + death.kc;
			JLabel tag = text(kc, death.challengeMode ? GOLD : CYAN);
			tag.setPreferredSize(new Dimension(72, 16));
			JLabel room = text(death.room, Color.WHITE);
			JLabel lost = text(grouped(death.pointsLost), Color.WHITE);
			row.add(tag, BorderLayout.WEST);
			row.add(room, BorderLayout.CENTER);
			row.add(lost, BorderLayout.EAST);
			stretch(row);
			list.add(row);
		}
		stretch(list);
		return list;
	}

	private JPanel items(ChambersPurples board) {
		JPanel grid = new JPanel(new GridLayout(0, 2, 4, 4));
		grid.setOpaque(false);
		for (int i = 0; i < board.items.size(); i++) {
			grid.add(itemRow(board.items.get(i), true));
		}
		stretch(grid);
		JPanel side = new JPanel(new GridLayout(1, 3, 4, 0));
		side.setOpaque(false);
		for (int i = 0; i < board.sides.size(); i++) {
			side.add(itemRow(board.sides.get(i), false));
		}
		stretch(side);
		JPanel wrap = new JPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.setOpaque(false);
		wrap.add(grid);
		wrap.add(Box.createRigidArea(new Dimension(0, 4)));
		wrap.add(side);
		stretch(wrap);
		return wrap;
	}

	private JPanel itemRow(ChambersPurples.Item item, boolean rated) {
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(CARD);
		row.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, HAIR),
			new EmptyBorder(5, 8, 5, 6)
		));
		JLabel icon = icon(item.name, 28);
		JLabel count = new JLabel(Integer.toString(item.got));
		count.setFont(COUNT);
		count.setForeground(item.got > 0 ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		JPanel identity = new JPanel(new BorderLayout(10, 0));
		identity.setOpaque(false);
		identity.add(icon, BorderLayout.WEST);
		identity.add(count, BorderLayout.CENTER);
		row.add(identity, BorderLayout.CENTER);
		if (rated) {
			String diff = String.format(Locale.US, "%+.2f", item.diff());
			JLabel rate = new JLabel(diff);
			rate.setFont(COUNT);
			rate.setForeground(Math.abs(item.diff()) < 0.05 && item.got == 0 ? ColorScheme.LIGHT_GRAY_COLOR : diffColor(item.diff()));
			row.add(rate, BorderLayout.EAST);
			String tip = String.format(Locale.US, "%s. Got %d, on rate %.2f", item.name, item.got, item.onRate);
			count.setToolTipText(tip);
			rate.setToolTipText(tip);
			icon.setToolTipText(tip);
		} else {
			String tip = item.name + ", got " + item.got;
			icon.setToolTipText(tip);
			count.setToolTipText(tip);
			count.setForeground(item.got > 0 ? trackedColor(sideKind(item.name)) : ColorScheme.LIGHT_GRAY_COLOR);
		}
		return row;
	}

	private JPanel tracked(ChambersPurples board) {
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		for (int i = 0; i < board.tracked.size(); i++) {
			ChambersPurples.Drop drop = board.tracked.get(i);
			JPanel row = new JPanel(new BorderLayout(10, 0));
			row.setBackground(CARD);
			row.setBorder(new CompoundBorder(
				new MatteBorder(0, 0, 1, 0, HAIR),
				new EmptyBorder(5, 8, 5, 8)
			));
			String kc = drop.challengeMode ? "CM " + drop.kc : "KC " + drop.kc;
			JLabel tag = text(kc, drop.challengeMode ? GOLD : CYAN);
			tag.setPreferredSize(new Dimension(72, 16));
			JLabel name = text(drop.item, trackedColor(drop.kind));
			row.add(tag, BorderLayout.WEST);
			row.add(name, BorderLayout.CENTER);
			stretch(row);
			list.add(row);
		}
		stretch(list);
		return list;
	}

	private JLabel icon(String name, int size) {
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(size, size));
		icon.setMinimumSize(new Dimension(size, size));
		int id = itemId(name);
		if (itemManager == null || id < 0) {
			return icon;
		}
		try {
			AsyncBufferedImage image = itemManager.getImage(id, 1, false);
			image.onLoaded(() -> {
				int width = Math.max(1, image.getWidth());
				int height = Math.max(1, image.getHeight());
				Image shown = image;
				if (width > size || height > size) {
					double scale = Math.min(size / (double) width, size / (double) height);
					width = Math.max(1, (int) Math.round(width * scale));
					height = Math.max(1, (int) Math.round(height * scale));
					shown = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
				}
				icon.setPreferredSize(new Dimension(width, height));
				icon.setMinimumSize(new Dimension(width, height));
				icon.setIcon(new ImageIcon(shown));
				icon.revalidate();
				icon.repaint();
			});
		} catch (RuntimeException ex) {
			return icon;
		}
		return icon;
	}

	private static int itemId(String name) {
		RaidUniques[] uniques = RaidUniques.values();
		for (int i = 0; i < uniques.length; i++) {
			if (uniques[i].getName().equalsIgnoreCase(name)) {
				return uniques[i].getItemID();
			}
		}
		return -1;
	}

	private static String sideKind(String name) {
		if ("Olmlet".equalsIgnoreCase(name)) {
			return "pet";
		}
		if (name != null && name.toLowerCase(Locale.US).contains("kit")) {
			return "kit";
		}
		if (name != null && name.toLowerCase(Locale.US).contains("dust")) {
			return "dust";
		}
		return "";
	}

	private static JPanel tip(JPanel card, String text) {
		card.setToolTipText(text);
		return card;
	}

	private static JPanel stat(String name, String value, Color color) {
		JPanel card = new JPanel(new BorderLayout(0, 2));
		card.setBackground(CARD);
		card.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, HAIR),
			new EmptyBorder(6, 8, 7, 8)
		));
		card.add(text(name, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.NORTH);
		JLabel amount = new JLabel(value);
		amount.setForeground(color);
		amount.setFont(VALUE);
		card.add(amount, BorderLayout.CENTER);
		return card;
	}

	private static JPanel header(String title, String aside) {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(BAR);
		bar.setBorder(new EmptyBorder(10, 8, 4, 8));
		bar.add(text(title, ColorScheme.BRAND_ORANGE), BorderLayout.WEST);
		if (aside != null) {
			bar.add(text(aside, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
		}
		stretch(bar);
		return bar;
	}

	private static JLabel notice(String value) {
		JLabel label = text(value, ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(new EmptyBorder(12, 8, 12, 8));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel text(String value, Color color) {
		JLabel label = new JLabel(value);
		label.setForeground(color);
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	private static void stretch(JComponent component) {
		component.setAlignmentX(LEFT_ALIGNMENT);
		Dimension pref = component.getPreferredSize();
		int height = Math.max(1, pref.height);
		component.setMinimumSize(new Dimension(0, height));
		component.setMaximumSize(new Dimension(Short.MAX_VALUE, height));
	}

	private static Color diffColor(double diff) {
		if (diff > 0.05) {
			return GREEN;
		}
		if (diff < -0.05) {
			return RED;
		}
		return Color.WHITE;
	}

	private static Color trackedColor(String kind) {
		if ("pet".equals(kind)) {
			return Color.WHITE;
		}
		if ("kit".equals(kind)) {
			return GREEN;
		}
		if ("dust".equals(kind)) {
			return CYAN;
		}
		return PURPLE;
	}

	private static String grouped(long value) {
		return String.format(Locale.US, "%,d", value);
	}

	private static final class HistoryStrip extends JPanel {
		private final ChambersPurples board;
		private int laidColumns = -1;

		private HistoryStrip(ChambersPurples board) {
			this.board = board;
			setOpaque(false);
			setAlignmentX(LEFT_ALIGNMENT);
			setBorder(new EmptyBorder(2, 6, 6, 6));
			String every = board.expectedEvery > 0 ? " A dot is where one was expected, every " + board.expectedEvery + " raids." : "";
			setToolTipText("Purple is a unique you received." + every + " Grey is dry. Gold is the next expected raid. Kit is green, pet is white, and dust is cyan.");
			addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent event) {
					int columns = columns(getWidth());
					if (columns != laidColumns) {
						laidColumns = columns;
						revalidate();
					}
				}
			});
		}

		@Override
		public Dimension getPreferredSize() {
			int width = getWidth() > 40 ? getWidth() : 220;
			int rows = Math.max(1, (board.marks.size() + columns(width) - 1) / columns(width));
			return new Dimension(220, rows * PITCH + 10);
		}

		@Override
		public Dimension getMinimumSize() {
			return new Dimension(0, getPreferredSize().height);
		}

		@Override
		public Dimension getMaximumSize() {
			return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int columns = columns(getWidth());
			int left = 6;
			for (int i = 0; i < board.marks.size(); i++) {
				int x = left + (i % columns) * PITCH;
				int y = 2 + (i / columns) * PITCH;
				ChambersPurples.Mark mark = board.marks.get(i);
				String kind = i < board.sideKinds.size() ? board.sideKinds.get(i) : "";
				g.setColor(square(mark, kind));
				g.fillRect(x, y, SQUARE, SQUARE);
				if (mark == ChambersPurples.Mark.EXPECTED || mark == ChambersPurples.Mark.BOTH) {
					g.setColor(GOLD);
					g.fillOval(x + 3, y + 3, 4, 4);
				}
			}
			g.dispose();
		}

		private int columns(int width) {
			int usable = Math.max(PITCH, width - 12);
			return Math.max(1, usable / PITCH);
		}

		private static Color square(ChambersPurples.Mark mark, String kind) {
			if (mark == ChambersPurples.Mark.PURPLE || mark == ChambersPurples.Mark.BOTH) {
				return PURPLE;
			}
			if (mark == ChambersPurples.Mark.NEXT) {
				return GOLD;
			}
			if ("pet".equals(kind)) {
				return Color.WHITE;
			}
			if ("kit".equals(kind)) {
				return new Color(12, 92, 48);
			}
			if ("dust".equals(kind)) {
				return CYAN;
			}
			return DRY;
		}
	}
}
