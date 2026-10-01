package com.raidtracker.ui.chambers;

import com.raidtracker.RaidTracker;
import com.raidtracker.RaidTrackerConfig;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Average, target, and best Chambers times.
 * A tick that only moves Current updates that label. The saved-raid comparison is rebuilt when a split changes.
 * Right-click the time table and choose Copy splits to put a Discord code block on the clipboard.
 */
public class ChambersTimesView extends JPanel {
	private static final Color GOLD = new Color(232, 196, 120);
	private static final Color GREEN = new Color(80, 220, 120);
	private static final Color RED = new Color(255, 90, 90);
	private static final Color ORANGE = new Color(255, 176, 46);
	private static final Color CYAN = new Color(80, 210, 255);
	private static final Color HAIR = ColorScheme.LIGHT_GRAY_COLOR.darker();
	private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color BAR = ColorScheme.DARKER_GRAY_COLOR.darker();
	private static final Font SWITCH = new Font(Font.SANS_SERIF, Font.BOLD, 12);
	private static final Font NAME = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
	private static final Font TIME = new Font(Font.SANS_SERIF, Font.BOLD, 13);
	private static final int STRIP_HEIGHT = 22;
	private static final int FILTER_HEIGHT = 20;

	private final List<RaidTracker> saved;
	private final List<RaidTracker> all;
	private final ChambersChoice choice;
	private final RaidTrackerConfig config;
	private final String mode;
	private final String size;
	private final String focus;
	private final String best;
	private final String targetSide;
	private final int targetCount;
	private final JPanel rows = new JPanel();
	private final JPopupMenu copyMenu = new JPopupMenu();
	private List<ChambersTimes.Row> shownRows = new ArrayList<>();
	private String copyHeading = "";
	private final PaceLine pace = new PaceLine();
	private final JLabel currentValue = new JLabel();
	private final JLabel kcLabel = new JLabel();
	private int liveSignature = Integer.MIN_VALUE;
	private boolean currentShown;
	private boolean watchingViewport;

	public ChambersTimesView(
		List<RaidTracker> saved,
		String mode,
		String size,
		String focus,
		String best,
		ChambersChoice choice,
		RaidTrackerConfig config,
		String targetSide,
		int targetCount
	) {
		this.all = saved == null ? java.util.Collections.<RaidTracker>emptyList() : saved;
		this.saved = ChambersFilter.select(this.all, mode, size);
		this.choice = choice;
		this.config = config;
		this.mode = mode;
		this.size = size;
		this.focus = focus;
		this.best = best;
		this.targetSide = "Last".equals(targetSide) ? "Last" : "This";
		this.targetCount = Math.max(1, Math.min(100, targetCount));
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		// Match the title row's center alignment. A left-aligned page in that box only receives the right half.
		setAlignmentX(CENTER_ALIGNMENT);
		setBorder(new EmptyBorder(0, 0, 2, 0));
		add(filters(mode, size));
		add(Box.createRigidArea(new Dimension(0, 2)));
		add(new ChoiceStrip(new String[] {"Average", "Target", "Best"}, focus, "focus"));
		if ("Best".equals(focus)) {
			add(Box.createRigidArea(new Dimension(0, 2)));
			add(new ChoiceStrip(new String[] {"Splits", "Raid time", "PPH"}, best, "best"));
		} else if ("Target".equals(focus)) {
			add(Box.createRigidArea(new Dimension(0, 2)));
			add(new TargetStrip());
		}
		add(Box.createRigidArea(new Dimension(0, 2)));
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setBackground(CARD);
		rows.setAlignmentX(LEFT_ALIGNMENT);
		rows.setBorder(new MatteBorder(1, 0, 1, 0, HAIR));
		JMenuItem copy = new JMenuItem("Copy splits");
		copy.addActionListener(event -> copySplits());
		copyMenu.add(copy);
		add(rows);
		add(Box.createRigidArea(new Dimension(0, 3)));
		pace.setAlignmentX(LEFT_ALIGNMENT);
		add(pace);
		showTable(tableFor(null, -1, focus, best));
		addHierarchyListener(event -> {
			if ((event.getChangeFlags() & (HierarchyEvent.SHOWING_CHANGED | HierarchyEvent.PARENT_CHANGED)) != 0) {
				SwingUtilities.invokeLater(this::watchViewport);
			}
		});
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
	}

	@Override
	public Dimension getMinimumSize() {
		return new Dimension(0, getPreferredSize().height);
	}

	public void showLive(RaidTracker live, int openSeconds) {
		if ("Target".equals(focus) && "Last".equals(targetSide)) {
			return;
		}
		int signature = signature(live);
		boolean wantCurrent = openSeconds > 0;
		if (signature != liveSignature || wantCurrent != currentShown) {
			liveSignature = signature;
			showTable(tableFor(live, openSeconds, focus, null));
			return;
		}
		if (wantCurrent) {
			currentValue.setText(ChambersFacts.formatTime(openSeconds));
		}
	}

	private ChambersTimes.Table tableFor(RaidTracker live, int openSeconds, String focus, String best) {
		if ("Target".equals(focus)) {
			return ChambersTargets.compare(saved, live, openSeconds, config, mode, size, "Last".equals(targetSide), targetCount);
		}
		boolean includeIceMilking = config != null && config.iceMilking();
		if (!"Best".equals(focus)) {
			return ChambersTimes.average(saved, live, openSeconds, includeIceMilking);
		}
		if ("Raid time".equals(best)) {
			return ChambersTimes.bestRaid(saved, includeIceMilking);
		}
		if ("PPH".equals(best)) {
			return ChambersTimes.bestPointsPerHour(saved, includeIceMilking);
		}
		return ChambersTimes.bestSplits(saved, includeIceMilking);
	}

	private void showTable(ChambersTimes.Table table) {
		rows.removeAll();
		currentShown = false;
		shownRows = new ArrayList<>(table.rows);
		copyHeading = copyTitle(focus, best, targetSide, targetCount, table.caption);
		rows.setComponentPopupMenu(shownRows.isEmpty() ? null : copyMenu);
		String group = "";
		if (isBadge(table.caption)) {
			kcLabel.setText(table.caption);
		} else {
			kcLabel.setText("");
			if (!table.caption.isEmpty() && !"Fastest in this filter".equals(table.caption)) {
				rows.add(caption(table.caption));
			}
		}
		for (int i = 0; i < table.rows.size(); i++) {
			ChambersTimes.Row row = table.rows.get(i);
			if (!row.group.equals(group) && !"Current".equals(row.group)) {
				group = row.group;
				rows.add(header(group));
			}
			rows.add(line(row));
			if (!"Best".equals(focus) && ("Shamans".equals(row.label) || "Tightrope".equals(row.label))) {
				rows.add(floorGap());
			}
		}
		stretch(rows);
		if (table.graph && table.pace.size() >= 2) {
			pace.show(table.pace, table.personalBestGap, table.paceAgainst);
			pace.setVisible(true);
		} else if (table.graph && !table.rows.isEmpty()) {
			pace.showEmpty("target".equals(table.paceAgainst) ? "No target to plot yet." : "Need another raid in this filter.");
			pace.setVisible(true);
		} else {
			pace.showEmpty("");
			pace.setVisible(false);
		}
		rows.revalidate();
		rows.repaint();
		SwingUtilities.invokeLater(this::fillGraph);
	}

	private void watchViewport() {
		JViewport viewport = viewport();
		if (viewport == null) {
			return;
		}
		if (!watchingViewport) {
			watchingViewport = true;
			viewport.addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent event) {
					SwingUtilities.invokeLater(ChambersTimesView.this::fillGraph);
				}
			});
		}
		fillGraph();
	}

	/**
	 * Give leftover sidebar height to the graph so Times fills the panel without growing past it.
	 */
	private void fillGraph() {
		if (!pace.isVisible()) {
			return;
		}
		JViewport viewport = viewport();
		if (viewport == null || viewport.getHeight() <= 0 || viewport.getView() == null) {
			return;
		}
		int graph = pace.getPreferredSize().height;
		int content = viewport.getView().getPreferredSize().height;
		pace.setPlotHeight(graph + viewport.getHeight() - content);
	}

	private JViewport viewport() {
		java.awt.Container cursor = getParent();
		while (cursor != null) {
			if (cursor instanceof JViewport) {
				return (JViewport) cursor;
			}
			cursor = cursor.getParent();
		}
		return null;
	}

	private JPanel line(ChambersTimes.Row row) {
		JPanel line = new JPanel(new BorderLayout(12, 0));
		line.setBackground(row.open ? new Color(46, 46, 46) : CARD);
		line.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, HAIR),
			new EmptyBorder(0, 4, 0, 6)
		));
		JLabel name = text(row.label, Color.WHITE);
		name.setHorizontalAlignment(SwingConstants.LEFT);
		if ("PPH".equals(row.label)) {
			name.setToolTipText("Points per hour");
		}
		JLabel value = text(row.value, row.gold ? GOLD : Color.WHITE);
		value.setFont(TIME);
		value.setHorizontalAlignment(SwingConstants.RIGHT);
		if (row.open) {
			currentValue.setText(row.value);
			currentValue.setForeground(Color.WHITE);
			currentValue.setFont(TIME);
			currentValue.setHorizontalAlignment(SwingConstants.RIGHT);
			currentShown = true;
			value = currentValue;
		}
		JPanel numbers = new JPanel(new BorderLayout(10, 0));
		numbers.setOpaque(false);
		if (row.aside.isEmpty()) {
			numbers.add(value, BorderLayout.EAST);
		} else {
			JLabel aside = text(row.aside, diffColor(row));
			aside.setFont(TIME);
			aside.setHorizontalAlignment(SwingConstants.RIGHT);
			numbers.add(value, BorderLayout.WEST);
			numbers.add(aside, BorderLayout.EAST);
		}
		JPanel nameSide = new JPanel(new BorderLayout(4, 0));
		nameSide.setOpaque(false);
		nameSide.add(mark(row), BorderLayout.WEST);
		nameSide.add(name, BorderLayout.CENTER);
		line.add(nameSide, BorderLayout.CENTER);
		line.add(numbers, BorderLayout.EAST);
		stretch(line);
		inheritCopy(line);
		return line;
	}

	/**
	 * Discord code block of the rows on screen. Comparison diffs stay off the paste.
	 * A Best-splits kill count that starts with {@code CM } or {@code KC } is kept.
	 * A blank line separates Rooms, Olm, and Finish. Times drop a leading zero.
	 */
	static String discordSplits(List<ChambersTimes.Row> rows, String title) {
		if (rows == null || rows.isEmpty()) {
			return "";
		}
		String[] names = new String[rows.size()];
		String[] values = new String[rows.size()];
		String[] counts = new String[rows.size()];
		int nameWidth = 0;
		int valueWidth = 0;
		for (int i = 0; i < rows.size(); i++) {
			ChambersTimes.Row row = rows.get(i);
			names[i] = row.label == null ? "" : row.label;
			values[i] = discordValue(row);
			counts[i] = killCountAside(row.aside);
			nameWidth = Math.max(nameWidth, names[i].length());
			valueWidth = Math.max(valueWidth, values[i].length());
		}
		StringBuilder out = new StringBuilder();
		out.append("```\n");
		if (title != null && !title.isEmpty()) {
			out.append(title).append('\n');
		}
		String section = "";
		boolean grouped = false;
		for (int i = 0; i < rows.size(); i++) {
			String next = sectionOf(rows.get(i));
			if (next != null && !next.equals(section)) {
				if (grouped) {
					out.append('\n');
				}
				section = next;
				grouped = true;
			}
			out.append(padRight(names[i], nameWidth));
			out.append("  ");
			out.append(padLeft(values[i], valueWidth));
			if (!counts[i].isEmpty()) {
				out.append("  ").append(counts[i]);
			}
			out.append('\n');
		}
		out.append("```");
		return out.toString();
	}

	/** Badge first, then the Best choice. Target's Last N is {@code Last N}. */
	static String copyTitle(String focus, String best, String targetSide, int targetCount, String caption) {
		if ("Target".equals(focus) && "Last".equals(targetSide)) {
			return "Last " + targetCount;
		}
		if (isBadge(caption)) {
			return caption;
		}
		if ("Best".equals(focus)) {
			if ("Raid time".equals(best) || "PPH".equals(best)) {
				return best;
			}
			return "Splits";
		}
		return "";
	}

	private void copySplits() {
		String text = discordSplits(rowsForCopy(), copyHeading);
		if (text.isEmpty()) {
			return;
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}

	/** Current is painted in place between rebuilds, so the paste reads that label. */
	private List<ChambersTimes.Row> rowsForCopy() {
		if (!currentShown) {
			return shownRows;
		}
		String live = currentValue.getText();
		List<ChambersTimes.Row> copy = new ArrayList<>(shownRows.size());
		for (int i = 0; i < shownRows.size(); i++) {
			ChambersTimes.Row row = shownRows.get(i);
			if (row.open) {
				copy.add(new ChambersTimes.Row(row.label, row.group, row.floor, live, row.aside, row.delta, row.higher, true, row.gold));
			} else {
				copy.add(row);
			}
		}
		return copy;
	}

	private static String discordValue(ChambersTimes.Row row) {
		String value = row.value == null ? "" : row.value;
		if (row.higher || value.indexOf(':') < 0) {
			return value;
		}
		int colon = value.indexOf(':');
		int start = 0;
		while (start < colon - 1 && value.charAt(start) == '0') {
			start++;
		}
		return value.substring(start);
	}

	private static String killCountAside(String aside) {
		if (aside != null && (aside.startsWith("CM ") || aside.startsWith("KC "))) {
			return aside;
		}
		return "";
	}

	private static String sectionOf(ChambersTimes.Row row) {
		if (row.open) {
			return null;
		}
		return row.group == null || row.group.isEmpty() ? null : row.group;
	}

	private static String padRight(String text, int width) {
		StringBuilder out = new StringBuilder(text);
		while (out.length() < width) {
			out.append(' ');
		}
		return out.toString();
	}

	private static String padLeft(String text, int width) {
		StringBuilder out = new StringBuilder();
		while (out.length() + text.length() < width) {
			out.append(' ');
		}
		out.append(text);
		return out.toString();
	}

	private static void inheritCopy(JComponent component) {
		component.setInheritsPopupMenu(true);
		for (int i = 0; i < component.getComponentCount(); i++) {
			Component child = component.getComponent(i);
			if (child instanceof JComponent) {
				inheritCopy((JComponent) child);
			}
		}
	}

	private static JPanel mark(ChambersTimes.Row row) {
		JPanel mark = new JPanel() {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(3, 10);
			}

			@Override
			public Dimension getMinimumSize() {
				return new Dimension(3, 4);
			}

			@Override
			public Dimension getMaximumSize() {
				return new Dimension(3, Short.MAX_VALUE);
			}

			@Override
			protected void paintComponent(Graphics graphics) {
				graphics.setColor(markColor(row));
				int height = Math.max(4, getHeight() - 6);
				graphics.fillRect(0, (getHeight() - height) / 2, 3, height);
			}
		};
		mark.setOpaque(false);
		return mark;
	}

	/** Same cuts as CoXGrind: 20 ahead is cyan, ahead is green, under 10 behind is orange. */
	private static Color markColor(ChambersTimes.Row row) {
		if (row.gold && !row.higher) {
			return GOLD;
		}
		if (row.open || row.delta == null || Math.abs(row.delta) < 1) {
			return ColorScheme.LIGHT_GRAY_COLOR;
		}
		int delta = row.delta;
		if (row.higher) {
			return delta > 0 ? GREEN : RED;
		}
		if (delta <= -20) {
			return CYAN;
		}
		if (delta < 0) {
			return GREEN;
		}
		if (delta < 10) {
			return ORANGE;
		}
		return RED;
	}

	private static Color diffColor(ChambersTimes.Row row) {
		if (row.delta == null || row.delta == 0) {
			return ColorScheme.LIGHT_GRAY_COLOR;
		}
		boolean good = row.higher ? row.delta > 0 : row.delta < 0;
		return good ? GREEN : RED;
	}

	private JPanel filters(String mode, String size) {
		JPanel combos = new JPanel(new GridLayout(1, 2, 4, 0));
		combos.setOpaque(false);
		combos.add(combo(new String[] {ChambersFilter.ALL, ChambersFilter.REGULAR, ChambersFilter.REGULAR_FULL, ChambersFilter.CM}, mode, "mode"));
		List<String> sizes = ChambersFilter.sizeChoices(all);
		if (!sizes.contains(size)) {
			size = ChambersFilter.ALL;
		}
		combos.add(combo(sizes.toArray(new String[0]), size, "size"));
		kcLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		kcLabel.setForeground(Color.WHITE);
		kcLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		kcLabel.setBorder(new EmptyBorder(0, 4, 0, 2));
		kcLabel.setText("In progress");
		int kcWidth = kcLabel.getPreferredSize().width;
		kcLabel.setText("");
		kcLabel.setPreferredSize(new Dimension(kcWidth, FILTER_HEIGHT));
		kcLabel.setMinimumSize(new Dimension(kcWidth, FILTER_HEIGHT));
		kcLabel.setMaximumSize(new Dimension(kcWidth, FILTER_HEIGHT));
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.add(combos, BorderLayout.CENTER);
		row.add(kcLabel, BorderLayout.EAST);
		row.setPreferredSize(new Dimension(0, FILTER_HEIGHT));
		stretch(row);
		return row;
	}

	private JComboBox<String> combo(String[] values, String selected, String field) {
		JComboBox<String> box = new JComboBox<>(values);
		box.setSelectedItem(selected);
		box.setFocusable(false);
		box.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		box.setPreferredSize(new Dimension(box.getPreferredSize().width, FILTER_HEIGHT));
		box.setMinimumSize(new Dimension(0, FILTER_HEIGHT));
		box.setMaximumSize(new Dimension(Short.MAX_VALUE, FILTER_HEIGHT));
		box.addActionListener(event -> {
			Object item = box.getSelectedItem();
			if (item != null) {
				choice.choose(field, item.toString());
			}
		});
		return box;
	}

	private JPanel caption(String value) {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(BAR);
		bar.setBorder(new EmptyBorder(1, 6, 1, 6));
		JLabel label = text(value, Color.WHITE);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		bar.add(label, BorderLayout.CENTER);
		stretch(bar);
		inheritCopy(bar);
		return bar;
	}

	private JPanel header(String value) {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(BAR);
		bar.setBorder(new EmptyBorder(1, 6, 0, 6));
		bar.add(text(value, ColorScheme.BRAND_ORANGE), BorderLayout.WEST);
		stretch(bar);
		inheritCopy(bar);
		return bar;
	}

	private JPanel floorGap() {
		JPanel gap = new JPanel() {
			@Override
			protected void paintComponent(Graphics graphics) {
				super.paintComponent(graphics);
				graphics.setColor(ColorScheme.LIGHT_GRAY_COLOR);
				int y = Math.max(0, getHeight() / 2 - 1);
				graphics.fillRect(10, y, Math.max(1, getWidth() - 20), 2);
			}
		};
		gap.setOpaque(true);
		gap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		gap.setPreferredSize(new Dimension(0, 8));
		stretch(gap);
		inheritCopy(gap);
		return gap;
	}

	private static JLabel text(String value, Color color) {
		JLabel label = new JLabel(value);
		label.setForeground(color);
		label.setFont(NAME);
		return label;
	}

	private static void stretch(JComponent component) {
		component.setAlignmentX(LEFT_ALIGNMENT);
		Dimension pref = component.getPreferredSize();
		int height = Math.max(1, pref.height);
		component.setMinimumSize(new Dimension(0, height));
		component.setMaximumSize(new Dimension(Short.MAX_VALUE, height));
	}

	private static boolean isBadge(String caption) {
		return caption.startsWith("KC ") || caption.startsWith("CM ") || "In progress".equals(caption);
	}

	private static int signature(RaidTracker raid) {
		if (raid == null) {
			return 0;
		}
		int sig = raid.getRaidTime();
		sig = 31 * sig + raid.getLowerTime();
		sig = 31 * sig + raid.getOlmHeadTime();
		sig = 31 * sig + raid.getPersonalPoints();
		for (int i = 0; i < ChambersFacts.PREP_ROOMS.length; i++) {
			sig = 31 * sig + ChambersFacts.roomSeconds(raid, ChambersFacts.PREP_ROOMS[i]);
		}
		sig = 31 * sig + listSig(raid.getOlmMageHandTimes());
		sig = 31 * sig + listSig(raid.getOlmPhaseTimes());
		return sig;
	}

	private static int listSig(List<Integer> values) {
		int sig = 1;
		if (values == null) {
			return sig;
		}
		for (int i = 0; i < values.size() && i < 8; i++) {
			Integer value = values.get(i);
			sig = 31 * sig + (value == null ? 0 : value);
		}
		return sig;
	}

	/**
	 * Full-width switch. The label is painted, so a narrow sidebar cannot clip it to "Sp..." or "Ra...".
	 */
	private final class ChoiceStrip extends JPanel {
		private final String[] names;
		private final String selected;
		private final String field;

		private ChoiceStrip(String[] names, String selected, String field) {
			this.names = names;
			this.selected = selected;
			this.field = field;
			setOpaque(true);
			setBackground(BAR);
			setAlignmentX(LEFT_ALIGNMENT);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText(tip(selected));
			addMouseMotionListener(new MouseAdapter() {
				@Override
				public void mouseMoved(MouseEvent event) {
					int index = indexAt(event.getX());
					setToolTipText(index < 0 ? null : tip(names[index]));
				}
			});
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent event) {
					int index = indexAt(event.getX());
					if (index >= 0 && !names[index].equals(selected)) {
						choice.choose(field, names[index]);
					}
				}
			});
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(180, STRIP_HEIGHT);
		}

		@Override
		public Dimension getMinimumSize() {
			return new Dimension(0, STRIP_HEIGHT);
		}

		@Override
		public Dimension getMaximumSize() {
			return new Dimension(Short.MAX_VALUE, STRIP_HEIGHT);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			int width = getWidth();
			int height = getHeight();
			int count = Math.max(1, names.length);
			int cell = width / count;
			for (int i = 0; i < names.length; i++) {
				int x = i * cell;
				int span = i == names.length - 1 ? width - x : cell;
				boolean on = names[i].equals(selected);
				Shape clip = g.getClip();
				g.clipRect(x, 0, span, height);
				g.setColor(on ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
				Font font = fontThatFits(g, names[i], span - 8);
				g.setFont(font);
				FontMetrics metrics = g.getFontMetrics();
				int textX = x + Math.max(4, (span - metrics.stringWidth(names[i])) / 2);
				int textY = (height - metrics.getHeight()) / 2 + metrics.getAscent();
				g.drawString(names[i], textX, textY);
				g.setClip(clip);
				g.setColor(on ? ColorScheme.BRAND_ORANGE : HAIR);
				g.fillRect(x, height - 2, span, 2);
				if (i > 0) {
					g.setColor(HAIR);
					g.drawLine(x, 6, x, height - 8);
				}
			}
			g.dispose();
		}

		private int indexAt(int x) {
			if (names.length == 0 || getWidth() <= 0) {
				return -1;
			}
			int index = x * names.length / getWidth();
			if (index < 0 || index >= names.length) {
				return -1;
			}
			return index;
		}

		private Font fontThatFits(Graphics2D g, String name, int room) {
			if (g.getFontMetrics(SWITCH).stringWidth(name) <= room) {
				return SWITCH;
			}
			Font smaller = new Font(Font.SANS_SERIF, Font.BOLD, 11);
			if (g.getFontMetrics(smaller).stringWidth(name) <= room) {
				return smaller;
			}
			return FontManager.getRunescapeSmallFont();
		}

		private String tip(String name) {
			if ("Average".equals(name)) {
				return "This raid against your average";
			}
			if ("Target".equals(name)) {
				return "Last".equals(targetSide)
					? "Last " + targetCount + " raids against your targets"
					: "This raid against your targets";
			}
			if ("Best".equals(name)) {
				return "Best splits, raid time, and points per hour";
			}
			if ("Splits".equals(name)) {
				return "Fastest split for each room";
			}
			if ("Raid time".equals(name)) {
				return "Fastest raid in this filter";
			}
			if ("PPH".equals(name)) {
				return "Highest points per hour";
			}
			return name;
		}
	}

	/**
	 * This, or Last N with minus and plus on either side of the title.
	 * A click changes N by 1. Shift changes it by 10.
	 */
	private final class TargetStrip extends JPanel {
		private TargetStrip() {
			setOpaque(true);
			setBackground(BAR);
			setAlignmentX(LEFT_ALIGNMENT);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent event) {
					hit(event);
				}
			});
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(180, STRIP_HEIGHT);
		}

		@Override
		public Dimension getMinimumSize() {
			return new Dimension(0, STRIP_HEIGHT);
		}

		@Override
		public Dimension getMaximumSize() {
			return new Dimension(Short.MAX_VALUE, STRIP_HEIGHT);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			int width = getWidth();
			int height = getHeight();
			int mid = width / 2;
			paintCell(g, 0, mid, "This", "This".equals(targetSide));
			paintCell(g, mid, width - mid, lastTitle(), "Last".equals(targetSide));
			g.dispose();
		}

		private void paintCell(Graphics2D g, int x, int span, String name, boolean on) {
			Shape clip = g.getClip();
			g.clipRect(x, 0, span, getHeight());
			g.setColor(on ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
			g.setFont(SWITCH);
			FontMetrics metrics = g.getFontMetrics();
			int textX = x + Math.max(4, (span - metrics.stringWidth(name)) / 2);
			int textY = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
			g.drawString(name, textX, textY);
			g.setClip(clip);
			g.setColor(on ? ColorScheme.BRAND_ORANGE : HAIR);
			g.fillRect(x, getHeight() - 2, span, 2);
			if (x > 0) {
				g.setColor(HAIR);
				g.drawLine(x, 6, x, getHeight() - 8);
			}
		}

		private void hit(MouseEvent event) {
			int width = getWidth();
			if (width <= 0) {
				return;
			}
			int mid = width / 2;
			if (event.getX() < mid) {
				if (!"This".equals(targetSide)) {
					choice.choose("target", "This");
				}
				return;
			}
			int span = width - mid;
			int pad = Math.min(22, Math.max(16, span / 5));
			int local = event.getX() - mid;
			int step = event.isShiftDown() ? 10 : 1;
			if (local < pad) {
				changeCount(-step);
				return;
			}
			if (local >= span - pad) {
				changeCount(step);
				return;
			}
			if (!"Last".equals(targetSide)) {
				choice.choose("target", "Last");
			}
		}

		private void changeCount(int step) {
			int next = Math.max(1, Math.min(100, targetCount + step));
			if (next == targetCount) {
				if (!"Last".equals(targetSide)) {
					choice.choose("target", "Last");
				}
				return;
			}
			choice.choose("targetCount", Integer.toString(next));
		}

		private String lastTitle() {
			return "\u2212  Last " + targetCount + "  +";
		}
	}
}
