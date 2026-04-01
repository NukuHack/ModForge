package frontend;

import frontend.pages.*;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;

// =============================================================================
//  MAIN WINDOW  (replaces MainWindow.xaml + custom title bar)
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

	// ── navigation ───────────────────────────────────────────────────────────
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel pageHolder = new JPanel(cardLayout);
	public final BarManager snackbar;
	private JButton activeNavBtn;

	// ── pages ─────────────────────────────────────────────────────────────────
	public static final String PAGE_HOME = "HOME";
	public static final String PAGE_MODS = "MODS";
	public static final String PAGE_MOD_EDIT = "MOD_EDIT";
	public static final String PAGE_ITEMS = "ITEMS";
	public static final String PAGE_STORM = "STORM";
	public static final String PAGE_SETTINGS = "SETTINGS";

	public MainWindow(Object registry /* ServiceRegistry */) {
		super("ModForge");
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

		// ── clipboard API (mirrors window.clipboardCopy in functions.js) ──
		Util.install();

		// show home by default
		navigate(PAGE_HOME);
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
		side.add(navBtn("🏠  Home", PAGE_HOME));
		side.add(navBtn("📦  Mods", PAGE_MODS));
		side.add(navBtn("🗡  Items", PAGE_ITEMS));
		side.add(navBtn("⚡  Storm", PAGE_STORM));
		side.add(Box.createVerticalGlue());
		side.add(navBtn("⚙  Settings", PAGE_SETTINGS));

		return side;
	}

	private JButton navBtn(String text, String page) {
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
		pageHolder.add(new HomePage(this), PAGE_HOME);
		pageHolder.add(new ModsPage(this), PAGE_MODS);
		pageHolder.add(new ModEditPage(this), PAGE_MOD_EDIT);
		pageHolder.add(new ItemsPage(this), PAGE_ITEMS);
		pageHolder.add(new StormPage(this), PAGE_STORM);
		pageHolder.add(new SettingsPage(this), PAGE_SETTINGS);
		return pageHolder;
	}

	public void navigate(String page) {
		cardLayout.show(pageHolder, page);
		snackbar.show("Navigated to " + page, BarManager.Type.INFO);
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
		// Ctrl+Scroll block on all components is handled by consuming wheel
		// events globally is not trivially possible in Swing, but we can
		// override on focused components.  Most Swing components ignore wheel
		// zoom by default, so this is sufficient.
	}
}