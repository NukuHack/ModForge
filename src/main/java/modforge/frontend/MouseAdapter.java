package modforge.frontend;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

// =============================================================================
//  TITLE-BAR DRAG  (replaces DragMove / TitleBar_MouseDown)
// =============================================================================
class MouseAdapter extends java.awt.event.MouseAdapter {
	private final JFrame frame;
	private Point origin;
	
	MouseAdapter(JFrame f) {
		this.frame = f;
	}
	
	@Override
	public void mousePressed(MouseEvent e) {
		if (SwingUtilities.isLeftMouseButton(e))
			origin = e.getPoint();
	}
	
	@Override
	public void mouseDragged(MouseEvent e) {
		if (origin != null && frame.getExtendedState() != Frame.MAXIMIZED_BOTH) {
			Point loc = frame.getLocation();
			frame.setLocation(loc.x + e.getX() - origin.x, loc.y + e.getY() - origin.y);
		}
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {
		if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
			frame.setExtendedState(frame.getExtendedState() == Frame.MAXIMIZED_BOTH ? Frame.NORMAL : Frame.MAXIMIZED_BOTH);
		}
	}
}
