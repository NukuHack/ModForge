package modforge.backend.service;

import modforge.backend.AttributeFactory;
import modforge.backend.BuildHandler;
import modforge.backend.model.ModItem;
import modforge.backend.model.attributes.IAttribute;
import modforge.backend.model.item.*;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class ModItemBuilder {
	private static final Logger log = Logger.getLogger(ModItemBuilder.class.getName());
	private final List<BuildHandler> handlers;

	public ModItemBuilder(List<BuildHandler> handlers) {
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
				new GBuildHandler<>(Perk.class, "perk_id"),
				new GBuildHandler<>(Buff.class, "buff_id"),
				new GBuildHandler<>(Storm.class, "id"),
				new GBuildHandler<>(PerkBuff.class, "perk_id"),
				new GBuildHandler<>(PerkScript.class, "perk_id"),
				new GBuildHandler<>(MeleeWeapon.class, "Id"),
				new GBuildHandler<>(MissileWeapon.class, "Id"),
				new GBuildHandler<>(Ammo.class, "Id"),
				new GBuildHandler<>(MeleeWeaponClass.class, "id"),
				new GBuildHandler<>(MissileWeaponClass.class, "id"),
				new GBuildHandler<>(Hood.class, "Id"),
				new GBuildHandler<>(Armor.class, "Id"),
				new GBuildHandler<>(Helmet.class, "Id"),
				new GBuildHandler<>(Food.class, "Id"),
				new GBuildHandler<>(Poison.class, "Id"),
				new GBuildHandler<>(Herb.class, "Id"),
				new GBuildHandler<>(CraftingMaterial.class, "Id"),
				new GBuildHandler<>(NPCTool.class, "Id"),
				new GBuildHandler<>(MiscItem.class, "Id"),
				new GBuildHandler<>(GameDocument.class, "Id"),
				new GBuildHandler<>(Die.class, "Id"),
				new GBuildHandler<>(ItemAlias.class, "Id"),
				new GBuildHandler<>(QuickSlotContainer.class, "Id"),
				new GBuildHandler<>(DiceBadge.class, "Id"),
				new GBuildHandler<>(PickableItem.class, "Id"),
				new GBuildHandler<>(Key.class, "Id"),
				new GBuildHandler<>(Money.class, "Id"),
				new GBuildHandler<>(KeyRing.class, "Id")
		));
	}

	/**
	 * Generic build handler: recognizes elements whose local name matches the
	 * simple class name (case-insensitive) and populates a configurable ID attribute.
	 */
	protected static final class GBuildHandler<M extends BaseModItem> implements BuildHandler {
		private static final Logger log = Logger.getLogger(GBuildHandler.class.getName());
		private final Class<M> type;
		private final String idAttrKey;

		public GBuildHandler(Class<M> type, String idAttrKey) {
			this.type = type;
			this.idAttrKey = idAttrKey;
		}

		@Override
		public boolean isResponsible(Element el) {
			return el.getLocalName().equalsIgnoreCase(type.getSimpleName());
		}

		@Override
		public ModItem handle(final Element element) {
			try {
				final M item = type.getDeclaredConstructor().newInstance();
				item.setIdKey(idAttrKey);

				final String idValue = element.getAttribute(idAttrKey);
				item.setId(idValue.isBlank() ? null : idValue);

				return ModItemBuilder.create(element, item);
			} catch (final Exception e) {
				log.warning("Handler failed for " + type.getSimpleName() + ": " + e.getMessage());
				return null;
			}
		}
	}

	public static ModItem create(final Element el, final ModItem item) {
		final var xmlAttrs = el.getAttributes();
		final var list = new ArrayList<IAttribute>(xmlAttrs.getLength());
		for (int i = 0; i < xmlAttrs.getLength(); i++) {
			final var a = (org.w3c.dom.Attr) xmlAttrs.item(i);
			list.add(AttributeFactory.create(a.getLocalName(), a.getValue()));
		}
		item.setAttribute(list);
		return item;
	}
}
