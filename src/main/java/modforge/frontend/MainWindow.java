package modforge.frontend;

import modforge.backend.service.ServiceRegistry;
import modforge.frontend.pages.*;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.logging.Logger;

// =============================================================================
//  MAIN WINDOW
// =============================================================================
public class MainWindow extends JFrame {
	// ── palette ──────────────────────────────────────────────────────────────
	public static final Color BG = new Color(0x1e1e2e);
	public static final Color SURFACE = new Color(0x181825);
	public static final Color TITLEBAR = new Color(0x11111b);
	public static final Color ACCENT = new Color(0x89b4fa);
	public static final Color TEXT = new Color(0xcdd6f4);
	public static final Color MUTED = new Color(0x6c6f85);
	public static final Color DANGER = new Color(0xf38ba8);
	private static final Logger log = Logger.getLogger(MainWindow.class.getName());
	public final BarManager snackbar;
	// ── navigation ───────────────────────────────────────────────────────────
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel pageHolder = new JPanel(cardLayout);
	// backend ---------------
	private final ServiceRegistry registry;
	
	public MainWindow(ServiceRegistry registry) {
		super("ModForge");
		
		//backend ---
		this.registry = registry;
		
		// Load window icon before setting up the UI
		setWindowIcon();
		
		// basic properties
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(1280, 800);
		setMinimumSize(new Dimension(900, 600));
		setLocationRelativeTo(null);
		setUndecorated(true);   // custom title bar
		
		// Allow dragging via title bar
		MouseAdapter tbm = new MouseAdapter(this);
		
		// ── root layout ───────────────────────────────────────────────────
		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(BG);
		root.setBorder(new LineBorder(MUTED, 1)); // thin frame border
		
		root.add(buildTitleBar(tbm), BorderLayout.NORTH);
		root.add(buildSidebar(), BorderLayout.WEST);
		root.add(buildPageArea(), BorderLayout.CENTER);
		
		setContentPane(root);
		
		// ── snackbar overlay ──────────────────────────────────────────────
		snackbar = new BarManager(this);
		
		// ── zoom block (mirrors js/functions.js Ctrl+scroll / Ctrl±) ─────
		installZoomBlock();
		
		// Initialize all pages
		initializePages();
		
		// show home by default
		navigate(Page.HOME);
	}
	
	public ServiceRegistry getRegistry() {
		return registry;
	}
	
	/**
	 * Loads the window icon from resources/images/Icons/modforge.png or .ico
	 * Falls back to default Java icon if not found
	 */
	private void setWindowIcon() {
		// Try PNG first (better cross-platform support)
		final var f = new String[] {
				// most modern systems should allow the first but left the rest for fallback is ... extreme cases
				"/images/Icons/modforge.png", "/images/Icons/modforge.ico", "/resources/images/Icons/modforge.png", "/resources/images/Icons/modforge.ico", "resources/images/Icons/modforge.png", "resources/images/Icons/modforge.ico" };
		
		for (final var el : f) {
			final var out = getClass().getResource(el);
			
			if (out != null) {
				setIconImage(new ImageIcon(out).getImage());
				log.info("Window icon loaded successfully from: " + out);
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
				log.warning("Failed to create page: " + page.name());
				log.warning("Ex : " + e);
			}
		}
	}
	
	// ── title bar ─────────────────────────────────────────────────────────────
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
		controls.add(winCtrlBtn("✕", DANGER, e -> dispose()));
		
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
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.addActionListener(action);
		b.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				b.setBackground(new Color(0x313244));
			}
			
			@Override
			public void mouseExited(MouseEvent e) {
				b.setBackground(TITLEBAR);
			}
		});
		return b;
	}
	
	// ── sidebar ───────────────────────────────────────────────────────────────
	private JPanel buildSidebar() {
		JPanel side = new JPanel();
		side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
		side.setBackground(SURFACE);
		side.setPreferredSize(new Dimension(200, 0));
		side.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 0));
		
		side.add(Box.createVerticalStrut(8));
		
		for (Page page : new Page[] { Page.HOME, Page.MODS, Page.ITEMS }) {
			side.add(navBtn(page.getDisplayName(), page));
		}
		
		side.add(Box.createVerticalGlue());
		side.add(navBtn(Page.SETTINGS.getDisplayName(), Page.SETTINGS));
		
		return side;
	}
	
	private JButton navBtn(String text, Page page) {
		final JButton b = new JButton(text);
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
		b.setHorizontalAlignment(SwingConstants.LEFT);
		b.setBackground(SURFACE);
		b.setForeground(TEXT);
		b.setBorderPainted(false);
		b.setFocusPainted(false);
		b.setFont(new Font("Roboto", Font.PLAIN, 13));
		b.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.addActionListener(e -> navigate(page));
		b.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				b.setBackground(new Color(0x313244));
			}
			
			@Override
			public void mouseExited(MouseEvent e) {
				b.setBackground(SURFACE);
			}
		});
		return b;
	}
	
	// ── page area ─────────────────────────────────────────────────────────────
	private JPanel buildPageArea() {
		pageHolder.setBackground(BG);
		return pageHolder;
	}
	
	public void navigate(Page page, Object... input) {
		// Show the page
		cardLayout.show(pageHolder, page.name());
		
		page.instance.refresh(input);
		
		// Update active nav button styling
		// (You might want to implement this to highlight the current page in sidebar)
		
		snackbar.show("Navigated to " + page.getDisplayName(), BarManager.Type.INFO);
	}
	
	// ── zoom-block (mirrors js/functions.js) ──────────────────────────────────
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
	
	// ── navigation enum ──────────────────────────────────────────────────────
	public enum Page {
		HOME("Home", HomePage.class), MODS("Mods", ModsPage.class), MOD_EDIT("Edit Mod", ModEditPage.class),
		ITEMS("Items", ItemsPage.class), STORM("Storm", StormPage.class), ITEM_EDIT("Item Edit", ItemEdit.class),
		LANG_EDIT("Lang Edit", LangEdit.class), SETTINGS("Settings", SettingsPage.class);
		
		private final String displayName;
		private final Class<? extends BasePage> pageClass;
		private BasePage instance;
		
		Page(String displayName, Class<? extends BasePage> pageClass) {
			this.displayName = displayName;
			this.pageClass = pageClass;
		}
		
		public String getDisplayName() {
			return displayName;
		}
		
		public Class<? extends BasePage> getPageClass() {
			return pageClass;
		}
		
		public BasePage getInstance() {
			return instance;
		}
		
		public void setInstance(BasePage instance) {
			this.instance = instance;
		}
	}
}