package frontend.pages;

import frontend.*;

import javax.swing.*;
import java.awt.*;

// =============================================================================
//  MODS PAGE  (ModCollection list)
// =============================================================================
public class ModsPage extends BasePage {

	public ModsPage(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(header("My Mods"), BorderLayout.WEST);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		actions.setOpaque(false);
		actions.add(primaryBtn("+ New Mod", e -> w.navigate(MainWindow.PAGE_MOD_EDIT)));
		actions.add(primaryBtn("Refresh", e -> refresh()));
		top.add(actions, BorderLayout.EAST);

		DefaultListModel<String> model = new DefaultListModel<>();
		JList<String> list = new JList<>(model);
		list.setBackground(MainWindow.SURFACE);
		list.setForeground(MainWindow.TEXT);
		list.setFont(new Font("Roboto", Font.PLAIN, 13));
		list.setSelectionBackground(new Color(0x313244));
		list.setFixedCellHeight(44);
		list.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));

		// Dummy data – replace with: registry.modService.ModCollection
		model.addElement("📦  my_first_mod   v1.0");
		model.addElement("📦  better_swords  v2.3");
		model.addElement("📦  perk_overhaul  v0.9-beta");

		JScrollPane scroll = new JScrollPane(list);
		scroll.setBackground(MainWindow.SURFACE);
		scroll.setBorder(BorderFactory.createEmptyBorder());

		JPanel card = card("Installed Mods");
		card.add(scroll, BorderLayout.CENTER);

		add(top, BorderLayout.NORTH);
		add(card, BorderLayout.CENTER);
	}

	private void refresh() {
		// registry.modService.InitiateModCollections();
		// model.clear(); registry.modService.ModCollection.forEach(m -> model.addElement(m.getName()));
		window.snackbar.show("Mods refreshed", BarManager.Type.SUCCESS);
	}
}
