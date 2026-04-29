package com.nukuhack.modforge.frontend;

import com.nukuhack.modforge.Singleton;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.E;
import com.nukuhack.modforge.backend.service.ServiceRegistry;
import com.nukuhack.modforge.frontend.pages.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.text.MessageFormat;
import java.util.Arrays;

@Slf4j
public class MainWindow extends JFrame {
	
	public static final Color BG = new Color(0x1e1e2e);
	public static final Color SURFACE = new Color(0x181825);
	public static final Color TITLEBAR = new Color(0x11111b);
	public static final Color ACCENT = new Color(0x89b4fa);
	public static final Color TEXT = new Color(0xcdd6f4);
	public static final Color MUTED = new Color(0x6c6f85);
	public static final Color DANGER = new Color(0xf38ba8);
	public final BarManager snackbar;
	
	private final CardLayout cardLayout = new CardLayout();
	private Page current = null;
	private final JPanel pageHolder = new JPanel(cardLayout);
	
	@Getter
	private final ServiceRegistry registry;
	
	public MainWindow(ServiceRegistry registry) {
		super("ModForge");
		
		this.registry = registry;
		
		setWindowIcon();
		
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(1280, 800);
		setMinimumSize(new Dimension(900, 600));
		setLocationRelativeTo(null);
		setUndecorated(true);
		
		MouseAdapter tbm = new MouseAdapter(this);
		
		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(BG);
		root.setBorder(new LineBorder(MUTED, 1));
		
		root.add(buildTitleBar(tbm), BorderLayout.NORTH);
		root.add(buildSidebar(), BorderLayout.WEST);
		root.add(buildPageArea(), BorderLayout.CENTER);
		
		setContentPane(root);
		
		snackbar = new BarManager(this);
		
		installZoomBlock();
		
		initializePages();
		
		navigate(Page.HOME);
	}
	
	public static String getLocalText(String key, Object... args) {
		if (key == null || key.isBlank()) {
			if (args == null || args.length == 0)
				return "";
			else if (args.length == 1)
				return args[0].toString();
			else
				return Arrays.toString(args);
		}
		
		var lang = Singleton.getRegistry().userConfig.getLanguage();
		var langMap = Singleton.getLangMap();
		var engMap = langMap.get(E.Language.ENGLISH);
		var fMap = langMap.get(lang);
		String text = null;
		if (fMap != null)
			text = fMap.get(key);
		if (text == null && engMap != null)
			text = engMap.get(key);
		
		if (text == null || text.isBlank())
			return key;
		
		var safeArgs = new Object[args.length];
		for (var i = 0; i < args.length; i++) {
			safeArgs[i] = (args[i] == null) ? "" : args[i];
		}
		
		var result = MessageFormat.format(text, safeArgs);
		
		return result.replaceAll("\\{\\D+\\}", "").replaceAll("\\s+", " ").trim();
	}
	
	/**
	 * Loads the window icon from resources/images/Icons/modforge.png or .ico
	 * Falls back to default Java icon if not found
	 */
	private void setWindowIcon() {
		
		var f = new String[] { "/Icons/modforge.png", "/Icons/modforge.ico", "/resources/Icons/modforge.png", "/resources/Icons/modforge.ico", "resources/Icons/modforge.png", "resources/Icons/modforge.ico" };
		
		for (var el : f) {
			var out = getClass().getResource(el);
			
			if (out != null) {
				setIconImage(new ImageIcon(out).getImage());
				log.info("Window icon loaded successfully from: {}", out);
				break;
			}
		}
		
	}
	
	private void initializePages() {
		for (Page page : Page.values()) {
			try {
				BasePage pageInstance = page.getPageClass().getDeclaredConstructor(MainWindow.class).newInstance(this);
				page.setInstance(pageInstance);
				pageHolder.add(pageInstance, page.name());
			} catch (Exception e) {
				log.warn("Failed to create page: {}", page.name(), e);
			}
		}
	}
	
	private JPanel buildTitleBar(MouseAdapter drag) {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(TITLEBAR);
		bar.setPreferredSize(new Dimension(0, 40));
		bar.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 8));
		bar.addMouseListener(drag);
		bar.addMouseMotionListener(drag);
		
		final JLabel title = new JLabel("ModForge");
		title.setForeground(ACCENT);
		title.setFont(new Font("Roboto", Font.BOLD, 14));
		
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
		controls.setOpaque(false);
		controls.add(winCtrlBtn("–", MUTED, e -> setExtendedState(ICONIFIED)));
		controls.add(winCtrlBtn("□", MUTED, e -> {
			if (getExtendedState() == MAXIMIZED_BOTH)
				setExtendedState(NORMAL);
			else
				setExtendedState(MAXIMIZED_BOTH);
		}));
		controls.add(winCtrlBtn("✕", DANGER, e ->
				dispatchEvent(new WindowEvent(MainWindow.this, WindowEvent.WINDOW_CLOSING))
		));
		
		bar.add(title, BorderLayout.WEST);
		bar.add(controls, BorderLayout.EAST);
		return bar;
	}
	
	private JButton winCtrlBtn(String label, Color fg, ActionListener action) {
		JButton b = new JButton(label);
		b.setForeground(fg);
		b.setBackground(TITLEBAR);
		b.setBorderPainted(false);
		b.setFocusPainted(false);
		b.setFont(new Font("Roboto", Font.PLAIN, 13));
		b.setPreferredSize(new Dimension(36, 28));
		return getJButton(action, b, TITLEBAR);
	}
	
	private JPanel buildSidebar() {
		JPanel side = new JPanel();
		side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
		side.setBackground(SURFACE);
		side.setPreferredSize(new Dimension(200, 0));
		side.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 0));
		
		side.add(Box.createVerticalStrut(8));
		
		for (Page page : new Page[] { Page.HOME, Page.MODS, Page.ITEMS, Page.LANG, Page.ARCHIVE, Page.CONVERT, Page.KCD_CONVERTER }) {
			side.add(navBtn(page.getDisplayName(), e -> navigate(page, new ModData())));
		}
		
		side.add(Box.createVerticalGlue());
		side.add(navBtn(Page.SETTINGS.getDisplayName(), e -> navigate(Page.SETTINGS)));
		
		return side;
	}
	
	private JButton navBtn(String text, ActionListener event) {
		final JButton b = new JButton(getLocalText(text));
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
		b.setHorizontalAlignment(SwingConstants.LEFT);
		b.setBackground(SURFACE);
		b.setForeground(TEXT);
		b.setBorderPainted(false);
		b.setFocusPainted(false);
		b.setFont(new Font("Roboto", Font.PLAIN, 13));
		b.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
		return getJButton(event, b, SURFACE);
	}
	
	private JButton getJButton(ActionListener event, JButton b, Color surface) {
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.addActionListener(event);
		b.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				b.setBackground(new Color(0x313244));
			}
			
			@Override
			public void mouseExited(MouseEvent e) {
				b.setBackground(surface);
			}
		});
		return b;
	}
	
	private JPanel buildPageArea() {
		pageHolder.setBackground(BG);
		return pageHolder;
	}
	
	public void navigate(Page page, Object... input) {
		if (current != null && current == page)
			return;
		current = page;
		cardLayout.show(pageHolder, page.name());
		
		Page.instance.refresh(current, input);
		
		snackbar.show("ui_navigate_page", BarManager.Type.INFO, getLocalText(page.getDisplayName()));
	}
	
	private void installZoomBlock() {
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
			if (e.getID() == KeyEvent.KEY_PRESSED && e.isControlDown()) {
				int k = e.getKeyCode();
				if (k == KeyEvent.VK_PLUS || k == KeyEvent.VK_MINUS || k == KeyEvent.VK_EQUALS || k == KeyEvent.VK_0) {
					e.consume();
					return true;
				}
			}
			return false;
		});
	}
}
