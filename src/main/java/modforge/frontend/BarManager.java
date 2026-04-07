package modforge.frontend;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@lombok.extern.slf4j.Slf4j
public class BarManager {
	private static final int VISIBLE_MS = 3000;
	private static final int BAR_HEIGHT = 44;
	private static final int BAR_WIDTH = 320;
	private static final int PAD = 12;
	private final JFrame owner;
	private final List<JWindow> active = new ArrayList<>();
	
	BarManager(JFrame owner) {
		this.owner = owner;
	}
	
	public void show(String message, Type type) {
		Color bg = type.c;
		Color fg = new Color(0x1e1e2e);
		
		JWindow toast = new JWindow(owner);
		toast.setSize(BAR_WIDTH, BAR_HEIGHT);
		
		JLabel lbl = new JLabel(" " + message);
		lbl.setForeground(fg);
		lbl.setBackground(bg);
		lbl.setOpaque(true);
		lbl.setFont(new Font("Roboto", Font.PLAIN, 13));
		lbl.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
		toast.setContentPane(lbl);
		
		repositionAll(toast);
		toast.setVisible(true);
		active.add(toast);
		
		Timer hide = new Timer(VISIBLE_MS, e -> {
			toast.dispose();
			active.remove(toast);
		});
		hide.setRepeats(false);
		hide.start();
	}
	
	private void repositionAll(JWindow newest) {
		int ownerBottom = owner.getY() + owner.getHeight();
		int ownerRight = owner.getX() + owner.getWidth();
		int y = ownerBottom - PAD;
		// Stack existing toasts upward
		List<JWindow> all = new ArrayList<>(active);
		all.add(newest);
		for (int i = all.size() - 1; i >= 0; i--) {
			JWindow w = all.get(i);
			y -= BAR_HEIGHT + 6;
			w.setLocation(ownerRight - BAR_WIDTH - PAD, y);
		}
	}
	
	public enum Type {
		INFO(Color.LIGHT_GRAY), SUCCESS(Color.GREEN), WARNING(Color.YELLOW), ERROR(Color.RED);
		public final Color c;
		
		Type(Color c) {
			this.c = c;
		}
	}
}
