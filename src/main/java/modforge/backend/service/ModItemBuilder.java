package modforge.backend.service;

import modforge.backend.GenericBuildHandler;
import modforge.backend.model.IBuildHandler;
import modforge.backend.model.IModItem;
import modforge.backend.model.item.*;
import org.w3c.dom.Element;

import java.util.List;
import java.util.logging.*;

public final class ModItemBuilder {
	private static final Logger log = Logger.getLogger(ModItemBuilder.class.getName());
	private final List<IBuildHandler> handlers;

	public ModItemBuilder(List<IBuildHandler> handlers) {
		this.handlers = List.copyOf(handlers);
	}

	public IModItem build(Element element) {
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
}

// =============================================================================
// PAK READER  (mirrors C# PakReader)
// =============================================================================

// =============================================================================
// DDS CONVERTER  (stub - requires a third-party DDS decoder)
// =============================================================================

// =============================================================================
// XML ADAPTER  (mirrors C# XmlAdapter)
// =============================================================================

// =============================================================================
// LOCALIZATION ADAPTER  (mirrors C# LocalizationAdapter)
// =============================================================================

// =============================================================================
// JSON ADAPTER  (mirrors C# JsonAdapter)
// =============================================================================

// =============================================================================
// USER CONFIGURATION SERVICE  (mirrors C# UserConfigurationService)
// =============================================================================

// =============================================================================
// LOCALIZATION SERVICE  (mirrors C# LocalizationService)
// =============================================================================

// =============================================================================
// XML SERVICE  (mirrors C# XmlService)
// =============================================================================

// =============================================================================
// ICON SERVICE  (mirrors C# IconService)
// =============================================================================

// =============================================================================
// NAVIGATION SERVICE  (mirrors C# NavigationService without Blazor)
// =============================================================================

// =============================================================================
// MOD SERVICE  (mirrors C# ModService)
// =============================================================================

// =============================================================================
// EXTENSIONS UTILITY  (mirrors C# Extensions static class)
// =============================================================================

// =============================================================================
// SERVICE REGISTRY  (replaces C# IServiceCollection DI wiring)
// =============================================================================

