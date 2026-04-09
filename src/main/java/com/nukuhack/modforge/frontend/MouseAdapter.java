package com.nukuhack.modforge.frontend;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

@Slf4j
@RequiredArgsConstructor
class MouseAdapter extends java.awt.event.MouseAdapter {
	private final JFrame frame;
	private Point origin;
	
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
