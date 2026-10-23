package com.raidtracker.ui.chambers;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JPanel;

/**
 * One pace line. Zero is the average, or the target on Target.
 * The personal best is a dashed line. A point above zero is ahead. Past the personal best, the point is gold.
 */
public class PaceLine extends JPanel {
	private static final Color PAPER = new Color(40, 40, 40);
	private static final Color INK = new Color(220, 220, 220);
	private static final Color AXIS = new Color(90, 90, 90);
	private static final Color GOLD = new Color(232, 196, 120);
	private static final Color GREEN = new Color(80, 220, 120);
	private static final Color RED = new Color(255, 90, 90);
	private static final Color ORANGE = new Color(255, 176, 46);
	private static final Color CYAN = new Color(80, 210, 255);
	private static final Stroke DASH = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {4f, 3f}, 0f);

	private String emptyText = "Need another raid in this filter.";
	private String comparedWith = "average";
	private List<ChambersTimes.Mark> marks = Collections.emptyList();
	private Integer personalBestGap;
	private int[] pointX = new int[0];
	private int plotHeight = 156;

	public PaceLine() {
		setOpaque(true);
		setBackground(PAPER);
		setAlignmentX(LEFT_ALIGNMENT);
		setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent event) {
				setToolTipText(tip(event.getX()));
			}
		});
	}

	public void show(List<ChambersTimes.Mark> next, Integer gap) {
		show(next, gap, "average");
	}

	public void show(List<ChambersTimes.Mark> next, Integer gap, String comparedWith) {
		marks = next == null || next.size() < 2 ? Collections.<ChambersTimes.Mark>emptyList() : new ArrayList<>(next);
		personalBestGap = gap;
		this.comparedWith = comparedWith == null || comparedWith.isEmpty() ? "average" : comparedWith;
		pointX = new int[marks.size()];
		repaint();
	}

	public void showEmpty(String text) {
		emptyText = text == null || text.isEmpty() ? "Need another raid in this filter." : text;
		marks = Collections.emptyList();
		personalBestGap = null;
		pointX = new int[0];
		repaint();
	}

	/**
	 * Grow into leftover sidebar height. The floor stays tall enough to read the line and both captions.
	 */
	public void setPlotHeight(int height) {
		int next = Math.max(156, Math.min(height, 300));
		if (next == plotHeight) {
			return;
		}
		plotHeight = next;
		revalidate();
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(180, plotHeight);
	}

	@Override
	public Dimension getMinimumSize() {
		return new Dimension(0, plotHeight);
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Short.MAX_VALUE, plotHeight);
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setFont(getFont());
		g.setColor(PAPER);
		g.fillRect(0, 0, getWidth(), getHeight());
		if (marks.isEmpty()) {
			g.setColor(INK);
			g.drawString(emptyText, 8, 18);
			g.dispose();
			return;
		}
		int left = 28;
		int right = Math.max(left + 20, getWidth() - 8);
		int top = 8;
		int line = Math.max(13, g.getFontMetrics().getHeight());
		boolean showPb = personalBestGap != null;
		int bottom = getHeight() - (showPb ? line + 18 : line + 4);
		int maxAbs = 24;
		int gap = personalBestGap == null ? 0 : personalBestGap;
		for (int i = 0; i < marks.size(); i++) {
			maxAbs = Math.max(maxAbs, Math.abs(marks.get(i).aheadOfAverage));
		}
		maxAbs = Math.max(maxAbs, Math.abs(gap));
		int zeroY = top + (bottom - top) / 2;
		int half = Math.max(1, (bottom - top) / 2);
		g.setColor(AXIS);
		g.drawLine(left, zeroY, right, zeroY);
		g.setColor(INK);
		g.drawString("target".equals(comparedWith) ? "Tgt" : "Avg", 2, labelY(zeroY, top, bottom));
		if (personalBestGap != null && Math.abs(yAt(gap, zeroY, half, maxAbs) - zeroY) >= 14) {
			int pbY = yAt(gap, zeroY, half, maxAbs);
			Stroke solid = g.getStroke();
			g.setColor(GOLD);
			g.setStroke(DASH);
			g.drawLine(left, pbY, right, pbY);
			g.setStroke(solid);
			g.drawString("PB", 2, labelY(pbY, top, bottom));
		}
		int previousX = 0;
		int previousY = 0;
		for (int i = 0; i < marks.size(); i++) {
			int x = marks.size() == 1 ? (left + right) / 2 : left + i * (right - left) / (marks.size() - 1);
			int y = yAt(marks.get(i).aheadOfAverage, zeroY, half, maxAbs);
			pointX[i] = x;
			if (i > 0) {
				g.setColor(color(marks.get(i).aheadOfAverage));
				Stroke solid = g.getStroke();
				g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.drawLine(previousX, previousY, x, y);
				g.setStroke(solid);
			}
			previousX = x;
			previousY = y;
		}
		for (int i = 0; i < marks.size(); i++) {
			int y = yAt(marks.get(i).aheadOfAverage, zeroY, half, maxAbs);
			g.setColor(color(marks.get(i).aheadOfAverage));
			g.fillOval(pointX[i] - 3, y - 3, 6, 6);
		}
		ChambersTimes.Mark last = marks.get(marks.size() - 1);
		g.setColor(color(last.aheadOfAverage));
		g.drawString(compared(last.label, last.aheadOfAverage, comparedWith), 4, showPb ? getHeight() - line - 2 : getHeight() - 2);
		if (showPb) {
			int aheadOfBest = last.aheadOfAverage - personalBestGap;
			g.setColor(aheadOfBest > 0 ? GOLD : color(aheadOfBest));
			g.drawString(compared(last.label, aheadOfBest, "PB"), 4, getHeight() - 2);
		}
		g.dispose();
	}

	private String tip(int x) {
		if (marks.isEmpty() || pointX.length != marks.size()) {
			return null;
		}
		int nearest = 0;
		int distance = Math.abs(x - pointX[0]);
		for (int i = 1; i < pointX.length; i++) {
			int next = Math.abs(x - pointX[i]);
			if (next < distance) {
				distance = next;
				nearest = i;
			}
		}
		ChambersTimes.Mark mark = marks.get(nearest);
		String average = compared(mark.label, mark.aheadOfAverage, comparedWith);
		if (personalBestGap == null) {
			return average;
		}
		return "<html>" + average + "<br>" + compared(mark.label, mark.aheadOfAverage - personalBestGap, "PB") + "</html>";
	}

	private static int yAt(int seconds, int zeroY, int half, int maxAbs) {
		return zeroY - (int) Math.round(seconds * (double) half / maxAbs);
	}

	private static int labelY(int lineY, int top, int bottom) {
		return Math.max(top + 10, Math.min(bottom - 2, lineY + 4));
	}

	private static String compared(String label, int seconds, String target) {
		String pace;
		if (seconds > 0) {
			pace = ChambersFacts.formatTime(seconds) + " ahead of " + target;
		} else if (seconds < 0) {
			pace = ChambersFacts.formatTime(-seconds) + " behind " + target;
		} else {
			pace = "even with " + target;
		}
		return label + "  " + pace;
	}

	private Color color(int aheadOfAverage) {
		if (personalBestGap != null && aheadOfAverage > personalBestGap) {
			return GOLD;
		}
		if (aheadOfAverage >= 20) {
			return CYAN;
		}
		if (aheadOfAverage > 0) {
			return GREEN;
		}
		if (aheadOfAverage == 0) {
			return INK;
		}
		if (aheadOfAverage > -10) {
			return ORANGE;
		}
		return RED;
	}
}
