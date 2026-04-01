package modforge.frontend;

import modforge.backend.service.ServiceRegistry;
import modforge.frontend.pages.*;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.EnumMap;
import java.util.Map;

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
	public static final Color SUCCESS = new Color(0xa6e3a1);

	// ── navigation enum ──────────────────────────────────────────────────────
	public enum Page {
		HOME("🏠 Home", HomePage.class),
		MODS("📦 Mods", ModsPage.class),
		MOD_EDIT("✏️ Edit Mod", ModEditPage.class),
		ITEMS("🗡 Items", ItemsPage.class),
		STORM("⚡ Storm", StormPage.class),
		SETTINGS("⚙ Settings", SettingsPage.class);

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

	// ── navigation ───────────────────────────────────────────────────────────
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel pageHolder = new JPanel(cardLayout);
	public final BarManager snackbar;
	private JButton activeNavBtn;
	private Page currentPage;

	// Store pages by enum
	private final Map<Page, BasePage> pages = new EnumMap<>(Page.class);

	// backend ---------------
	private final ServiceRegistry registry;

	public ServiceRegistry getRegistry() {
		return registry;
	}

	public MainWindow(ServiceRegistry registry) {
		super("ModForge");

		//backend ---
		this.registry = registry;

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

	private void initializePages() {
		for (Page page : Page.values()) {
			try {
				BasePage pageInstance = page.getPageClass()
						.getDeclaredConstructor(MainWindow.class)
						.newInstance(this);
				page.setInstance(pageInstance);
				pages.put(page, pageInstance);
				pageHolder.add(pageInstance, page.name());
			} catch (Exception e) {
				System.err.println("Failed to create page: " + page.name());
				e.printStackTrace();
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

		JLabel title = new JLabel("⚒  ModForge");
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

		for (Page page : Page.values()) {
			if (page != Page.MOD_EDIT && page != Page.SETTINGS) {
				side.add(navBtn(page.getDisplayName(), page));
			}
		}

		side.add(Box.createVerticalGlue());
		side.add(navBtn(Page.SETTINGS.getDisplayName(), Page.SETTINGS));

		return side;
	}

	private JButton navBtn(String text, Page page) {
		JButton b = new JButton(text);
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
				if (b != activeNavBtn) b.setBackground(new Color(0x313244));
			}

			@Override
			public void mouseExited(MouseEvent e) {
				if (b != activeNavBtn) b.setBackground(SURFACE);
			}
		});
		return b;
	}

	// ── page area ─────────────────────────────────────────────────────────────
	private JPanel buildPageArea() {
		pageHolder.setBackground(BG);
		return pageHolder;
	}

	public void navigate(Page page) {
		// Handle any pre-navigation logic
		if (page == Page.MOD_EDIT) {
			// Refresh the mod edit page with current mod data when navigating to it
			final ModEditPage modEditPage = (ModEditPage) pages.get(Page.MOD_EDIT);
			if (modEditPage != null) {
				modEditPage.refreshFieldData();
			}
		}

		// Show the page
		cardLayout.show(pageHolder, page.name());
		currentPage = page;

		// Update active nav button styling
		// (You might want to implement this to highlight the current page in sidebar)

		snackbar.show("Navigated to " + page.getDisplayName(), BarManager.Type.INFO);
	}

	// Convenience method for backward compatibility
	public void navigate(String pageName) {
		try {
			Page page = Page.valueOf(pageName);
			navigate(page);
		} catch (IllegalArgumentException e) {
			System.err.println("Unknown page: " + pageName);
		}
	}

	public Page getCurrentPage() {
		return currentPage;
	}

	public BasePage getPage(Page page) {
		return pages.get(page);
	}

	// ── zoom-block (mirrors js/functions.js) ──────────────────────────────────
	private void installZoomBlock() {
		KeyboardFocusManager.getCurrentKeyboardFocusManager()
				.addKeyEventDispatcher(e -> {
					if (e.getID() == KeyEvent.KEY_PRESSED && e.isControlDown()) {
						int k = e.getKeyCode();
						if (k == KeyEvent.VK_PLUS || k == KeyEvent.VK_MINUS ||
								k == KeyEvent.VK_EQUALS || k == KeyEvent.VK_0) {
							e.consume();
							return true;
						}
					}
					return false;
				});
	}
}