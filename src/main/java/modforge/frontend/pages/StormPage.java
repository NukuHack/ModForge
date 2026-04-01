package modforge.frontend.pages;

import modforge.frontend.MainWindow;

import javax.swing.*;
import java.awt.*;

// =============================================================================
//  STORM PAGE  (STORM rule viewer stub)
// =============================================================================
public class StormPage extends BasePage {
	public StormPage(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		add(header("STORM Rules"), BorderLayout.NORTH);

		JPanel card = card("Rule Browser");

		JTextArea area = new JTextArea(
				"// STORM rule viewer\n" +
						"// Connect StormService.getStormDtos() here.\n\n" +
						"// Example:\n" +
						"//   List<StormDto> dtos = stormService.getStormDtos();\n" +
						"//   dtos.forEach(dto -> tree.addNode(dto.getId(), dto.getCategory()));\n"
		);
		area.setBackground(new Color(0x11111b));
		area.setForeground(new Color(0xa6e3a1));
		area.setFont(new Font("JetBrains Mono", Font.PLAIN, 13));
		area.setCaretColor(MainWindow.TEXT);
		area.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		area.setEditable(false);

		card.add(new JScrollPane(area), BorderLayout.CENTER);
		add(card, BorderLayout.CENTER);
	}
}
