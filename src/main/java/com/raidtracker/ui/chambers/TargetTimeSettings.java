package com.raidtracker.ui.chambers;

import com.raidtracker.RaidTrackerConfig;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseListener;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import net.runelite.client.config.ConfigManager;

/**
 * The settings page cannot calculate a total or reset a section on its own.
 * This fills the gray Target total and adds Reset to default on Target times.
 */
public final class TargetTimeSettings implements AWTEventListener {
	private static final int MAX_CHILDREN = 64;
	private static final String PAGE = "Raid Data Tracker";
	private static final String SECTION = "Target times";
	private static final String MARK = "raidtracker-target-reset";
	private static final String TOTAL = "raidtracker-target-total";
	private static final String NO_RIGHT = "raidtracker-target-noright";
	private static final Color INK = new Color(150, 150, 150);
	/** Same cuts as Times: under 10 behind is orange, further behind is red. */
	private static final Color ORANGE = new Color(255, 176, 46);
	private static final Color RED = new Color(255, 90, 90);
	private static final String ROOM = "raidtracker-target-room";
	private static final String[] BOXES = {
		"cmTekton",
		"cmCrabs",
		"cmIceDemon",
		"cmShamans",
		"cmVanguards",
		"cmThieving",
		"cmVespula",
		"cmTightrope",
		"cmGuardians",
		"cmVasa",
		"cmMystics",
		"cmMuttadiles",
		"cmOlmMage",
		"cmOlmPhase",
		"cmOlmHead",
		"cmBetweenRooms",
		"cmTotalPoints",
		"cmPph"
	};

	private final RaidTrackerConfig config;
	private final ConfigManager manager;
	private boolean busy;

	public TargetTimeSettings(RaidTrackerConfig config, ConfigManager manager) {
		this.config = config;
		this.manager = manager;
	}

	public boolean isBusy() {
		return busy;
	}

	public void start() {
		Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.CONTAINER_EVENT_MASK);
		SwingUtilities.invokeLater(this::decorateOpenPages);
	}

	public void stop() {
		Toolkit.getDefaultToolkit().removeAWTEventListener(this);
	}

	public void forgetTotal() {
		if (manager == null) {
			return;
		}
		busy = true;
		try {
			manager.unsetConfiguration(RaidTrackerConfig.CONFIG_GROUP, "cmSoloTotal");
		} finally {
			busy = false;
		}
	}

	public void refreshTotal() {
		SwingUtilities.invokeLater(this::refreshOpenTotal);
	}

	@Override
	public void eventDispatched(AWTEvent event) {
		if (event.getID() != ContainerEvent.COMPONENT_ADDED || !(event instanceof ContainerEvent)) {
			return;
		}
		notice(((ContainerEvent) event).getChild());
	}

	/**
	 * The room boxes are built before the section is attached to the plugin page.
	 * Graying the total has to run when that section joins the page, not only when a box changes.
	 */
	public void notice(Component child) {
		if (child == null || isWide(child) || !onPluginSettings(child)) {
			return;
		}
		decorate(child, false);
	}

	/** Writes the total, and the other boxes when {@code rewriteFields} is set. */
	public void decorate(Component component, boolean rewriteFields) {
		if (component == null || isWide(component)) {
			return;
		}
		JComponent body = sheetBody(component);
		if (body != null) {
			installReset(body);
			disarm(body);
			fill(body, rewriteFields);
		}
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				decorate(child, rewriteFields);
			}
		}
	}

	private void decorateOpenPages() {
		for (Window window : Window.getWindows()) {
			notice(window);
		}
	}

	private void refreshOpenTotal() {
		for (Window window : Window.getWindows()) {
			refreshTree(window);
		}
	}

	private void refreshTree(Component component) {
		if (component == null || isWide(component)) {
			return;
		}
		if (component instanceof JTextComponent) {
			JLabel label = rowLabel(component);
			if (label != null && onPluginSettings(component)) {
				if ("Target total".equals(roomName(label))) {
					applyTotal((JTextComponent) component);
				}
				applyAdd(label);
			}
		}
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				refreshTree(child);
			}
		}
	}

	private void installReset(JComponent body) {
		// Times also has a Tekton row. The button belongs on the settings section, which has Target total too.
		if (Boolean.TRUE.equals(body.getClientProperty(MARK))
			|| !hasLabel(body, "Tekton")
			|| !hasLabel(body, "Target total")) {
			return;
		}
		body.putClientProperty(MARK, Boolean.TRUE);
		JButton button = new JButton("Reset to default");
		button.setToolTipText("Restore every box in Target times to its default.");
		button.addActionListener(event -> confirmReset(button));
		body.add(button, 0);
		body.revalidate();
		body.repaint();
	}

	private void confirmReset(Component parent) {
		int choice = JOptionPane.showOptionDialog(
			parent,
			"Reset the target times to their defaults?",
			"Reset targets",
			JOptionPane.DEFAULT_OPTION,
			JOptionPane.PLAIN_MESSAGE,
			null,
			new Object[] {"Reset", "Cancel"},
			"Cancel");
		if (choice != 0) {
			return;
		}
		resetBoxes();
		SwingUtilities.invokeLater(() -> decorate(parent, true));
	}

	private void resetBoxes() {
		if (manager == null) {
			return;
		}
		busy = true;
		try {
			for (int i = 0; i < BOXES.length; i++) {
				manager.unsetConfiguration(RaidTrackerConfig.CONFIG_GROUP, BOXES[i]);
			}
			manager.unsetConfiguration(RaidTrackerConfig.CONFIG_GROUP, "cmSoloTotal");
		} finally {
			busy = false;
		}
	}

	private void fill(Component component, boolean rewriteFields) {
		if (component instanceof JTextComponent) {
			JTextComponent field = (JTextComponent) component;
			JLabel label = rowLabel(field);
			if (label != null && onPluginSettings(field)) {
				String name = roomName(label);
				if ("Target total".equals(name)) {
					applyTotal(field);
				} else if (rewriteFields && !field.isFocusOwner()) {
					String shown = ChambersTargets.shown(config, name);
					if (!shown.isEmpty() || "Tekton".equals(name)) {
						field.setText(shown);
					}
				}
				applyAdd(label);
			}
		}
		if (component instanceof Container && !isWide(component)) {
			for (Component child : ((Container) component).getComponents()) {
				fill(child, rewriteFields);
			}
		}
	}

	private void applyTotal(JTextComponent field) {
		field.setEnabled(true);
		field.setText(ChambersTargets.settingsTotal(config));
		field.setEditable(false);
		field.setFocusable(false);
		field.setForeground(INK);
		field.setDisabledTextColor(INK);
		for (FocusListener listener : field.getFocusListeners()) {
			field.removeFocusListener(listener);
		}
		field.putClientProperty(TOTAL, Boolean.TRUE);
	}

	private void disarm(Component component) {
		if (component instanceof JLabel) {
			JLabel label = (JLabel) component;
			if (!Boolean.TRUE.equals(label.getClientProperty(NO_RIGHT)) && sheetBody(label) != null) {
				for (MouseListener listener : label.getMouseListeners()) {
					label.removeMouseListener(listener);
				}
				label.putClientProperty(NO_RIGHT, Boolean.TRUE);
			}
		}
		if (component instanceof Container && !isWide(component)) {
			for (Component child : ((Container) component).getComponents()) {
				disarm(child);
			}
		}
	}

	private boolean onPluginSettings(Component component) {
		Component cursor = component;
		while (cursor != null) {
			if (isWide(cursor)) {
				return false;
			}
			if (hasLabel(cursor, PAGE) && hasLabel(cursor, SECTION)) {
				return true;
			}
			cursor = cursor.getParent();
		}
		return false;
	}

	private static JComponent sheetBody(Component component) {
		Component cursor = component;
		while (cursor != null) {
			Container parent = cursor.getParent();
			if (parent == null) {
				return null;
			}
			if (cursor instanceof JComponent && sectionTitle(parent, cursor) && contains(cursor, component)) {
				return (JComponent) cursor;
			}
			cursor = parent;
		}
		return null;
	}

	/** True when this contents panel is the only section titled Target times among its siblings. */
	private static boolean sectionTitle(Container parent, Component cursor) {
		boolean found = false;
		for (Component child : parent.getComponents()) {
			if (child == cursor) {
				continue;
			}
			if (!hasLabel(child, SECTION)) {
				continue;
			}
			if (found || hasLabel(child, "Tekton")) {
				return false;
			}
			found = true;
		}
		return found;
	}

	private static boolean hasLabel(Component component, String text) {
		if (isWide(component)) {
			return false;
		}
		if (component instanceof JLabel && names((JLabel) component, text)) {
			return true;
		}
		if (!(component instanceof Container)) {
			return false;
		}
		for (Component child : ((Container) component).getComponents()) {
			if (hasLabel(child, text)) {
				return true;
			}
		}
		return false;
	}

	private static boolean contains(Component parent, Component child) {
		if (parent == child) {
			return true;
		}
		if (!(parent instanceof Container) || isWide(parent)) {
			return false;
		}
		for (Component next : ((Container) parent).getComponents()) {
			if (contains(next, child)) {
				return true;
			}
		}
		return false;
	}

	private static JLabel rowLabel(Component component) {
		Component cursor = component;
		while (cursor != null) {
			Container parent = cursor.getParent();
			if (parent == null) {
				return null;
			}
			JLabel label = null;
			int labels = 0;
			for (Component child : parent.getComponents()) {
				if (child instanceof JLabel) {
					labels++;
					label = (JLabel) child;
				}
			}
			if (labels == 1) {
				return label;
			}
			cursor = parent;
		}
		return null;
	}

	private static boolean isWide(Component component) {
		return component instanceof Container && ((Container) component).getComponentCount() > MAX_CHILDREN;
	}

	/** The saved box stays as typed. The label shows the extra time from the toggles. */
	private void applyAdd(JLabel label) {
		String name = roomName(label);
		int extra = ChambersTargets.addSeconds(config, name);
		if (extra <= 0) {
			label.setText(name);
			label.setForeground(Color.WHITE);
			return;
		}
		String note = "+ " + ChambersFacts.formatTime(extra);
		Color color = extra < 10 ? ORANGE : RED;
		label.setText("<html>" + name + " <font color=\"#" + hex(color) + "\">" + note + "</font></html>");
		label.setForeground(Color.WHITE);
	}

	/** The colored plus replaces the label text. The stored name is what the section check still matches. */
	private static boolean names(JLabel label, String text) {
		if (text.equals(plain(label.getText()))) {
			return true;
		}
		return text.equals(label.getClientProperty(ROOM));
	}

	private static String roomName(JLabel label) {
		Object kept = label.getClientProperty(ROOM);
		if (kept instanceof String) {
			return (String) kept;
		}
		String name = plain(label.getText());
		int extra = name.indexOf(" + ");
		if (extra >= 0) {
			name = name.substring(0, extra);
		}
		label.putClientProperty(ROOM, name);
		return name;
	}

	private static String hex(Color color) {
		return String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	private static String plain(String text) {
		if (text == null) {
			return "";
		}
		return text.trim();
	}
}
