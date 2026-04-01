package frontend.pages;

import frontend.*;

import javax.swing.*;
import java.awt.*;

// =============================================================================
//  MOD EDIT PAGE  (create / edit a mod – replaces Blazor mod-edit form)
// =============================================================================
public class ModEditPage extends BasePage {
	public ModEditPage(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		add(header("Create / Edit Mod"), BorderLayout.NORTH);

		JPanel form = card("Mod Details");
		form.setLayout(new GridBagLayout());

		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(6, 6, 6, 6);
		gc.fill = GridBagConstraints.HORIZONTAL;

		String[][] fields = {
				{"Mod ID", "my_mod"},
				{"Name", "My Mod"},
				{"Author", "YourName"},
				{"Version", "1.0"},
				{"Description", "Describe your mod …"},
		};

		for (int i = 0; i < fields.length; i++) {
			gc.gridx = 0;
			gc.gridy = i;
			gc.weightx = 0.2;
			JLabel lbl = new JLabel(fields[i][0]);
			lbl.setForeground(MainWindow.TEXT);
			lbl.setFont(new Font("Roboto", Font.PLAIN, 13));
			form.add(lbl, gc);

			gc.gridx = 1;
			gc.weightx = 0.8;
			form.add(styledField(fields[i][1]), gc);
		}

		gc.gridx = 1;
		gc.gridy = fields.length;
		gc.weightx = 0;
		JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		btns.setOpaque(false);
		btns.add(primaryBtn("Save", e -> w.snackbar.show("Mod saved!", BarManager.Type.SUCCESS)));
		btns.add(primaryBtn("Export", e -> w.snackbar.show("Exported to PAK", BarManager.Type.INFO)));
		form.add(btns, gc);

		add(form, BorderLayout.CENTER);
	}
}
