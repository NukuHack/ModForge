package modforge.backend.service;

import modforge.backend.AttributeFactory;
import modforge.backend.GenericBuildHandler;
import modforge.backend.model.IBuildHandler;
import modforge.backend.model.ModItem;
import modforge.backend.model.attributes.IAttribute;
import modforge.backend.model.item.*;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;

public final class ModItemBuilder {
	private static final Logger log = Logger.getLogger(ModItemBuilder.class.getName());
	private final List<IBuildHandler> handlers;

	public ModItemBuilder(List<IBuildHandler> handlers) {
		this.handlers = List.copyOf(handlers);
	}

	public ModItem build(Element element) {
		for (var h : handlers) {
			if (h.isResponsible(element)) return h.handle(element);
		}
		log.fine("No handler matched element <" + element.getLocalName() + ">");
		return null;
	}

	/**
	 * Creates the default handler list (mirrors C# ServiceConfiguration).
	 */
	public static ModItemBuilder createDefault() {
		return new ModItemBuilder(List.of(
				new GenericBuildHandler<>(Perk.class, "perk_id"),
				new GenericBuildHandler<>(Buff.class, "buff_id"),
				new GenericBuildHandler<>(Storm.class, "id"),
				new GenericBuildHandler<>(PerkBuff.class, "perk_id"),
				new GenericBuildHandler<>(MeleeWeapon.class, "Id"),
				new GenericBuildHandler<>(MissileWeapon.class, "Id"),
				new GenericBuildHandler<>(Ammo.class, "Id"),
				new GenericBuildHandler<>(MeleeWeaponClass.class, "id"),
				new GenericBuildHandler<>(MissileWeaponClass.class, "id"),
				new GenericBuildHandler<>(Hood.class, "Id"),
				new GenericBuildHandler<>(Armor.class, "Id"),
				new GenericBuildHandler<>(Helmet.class, "Id"),
				new GenericBuildHandler<>(Food.class, "Id"),
				new GenericBuildHandler<>(Poison.class, "Id"),
				new GenericBuildHandler<>(Herb.class, "Id"),
				new GenericBuildHandler<>(CraftingMaterial.class, "Id"),
				new GenericBuildHandler<>(NPCTool.class, "Id"),
				new GenericBuildHandler<>(MiscItem.class, "Id"),
				new GenericBuildHandler<>(GameDocument.class, "Id"),
				new GenericBuildHandler<>(Die.class, "Id"),
				new GenericBuildHandler<>(ItemAlias.class, "Id"),
				new GenericBuildHandler<>(QuickSlotContainer.class, "Id"),
				new GenericBuildHandler<>(DiceBadge.class, "Id"),
				new GenericBuildHandler<>(PickableItem.class, "Id"),
				new GenericBuildHandler<>(Key.class, "Id"),
				new GenericBuildHandler<>(Money.class, "Id"),
				new GenericBuildHandler<>(KeyRing.class, "Id")
		));
	}


	public static ModItem create(Element el, ModItem item) {

		var xmlAttrs = el.getAttributes();
		var list = new ArrayList<IAttribute>(xmlAttrs.getLength());
		for (int i = 0; i < xmlAttrs.getLength(); i++) {
			var a = (org.w3c.dom.Attr) xmlAttrs.item(i);
			list.add(AttributeFactory.create(a.getLocalName(), a.getValue()));
		}
		item.setAttribute(list);
		return item;
	}
}
