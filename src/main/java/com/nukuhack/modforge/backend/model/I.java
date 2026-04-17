package com.nukuhack.modforge.backend.model;

import com.nukuhack.modforge.backend.model.ModItem.BaseModItem;
import com.nukuhack.modforge.backend.model.Storm.StormData;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

/**
 * I -> Item
 * Contains all the item-types stored in the database
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class I {
	public static class Ammo extends BaseModItem {
	}
	
	public static class Armor extends BaseModItem {
	}
	
	public static class QuickSlotContainer extends BaseModItem {
	}
	
	public static class Poison extends BaseModItem {
	}
	
	public static class PickableItem extends BaseModItem {
	}
	
	public static class PerkScript extends BaseModItem {
	}
	
	public static class PerkBuff extends BaseModItem {
	}
	
	public static class Perk extends BaseModItem {
	}
	
	public static class NPCTool extends BaseModItem {
	}
	
	public static class Money extends BaseModItem {
	}
	
	public static class MissileWeaponClass extends BaseModItem {
	}
	
	public static class MissileWeapon extends BaseModItem {
	}
	
	public static class MiscItem extends BaseModItem {
	}
	
	public static class MeleeWeaponClass extends BaseModItem {
	}
	
	public static class MeleeWeapon extends BaseModItem {
	}
	
	public static class KeyRing extends BaseModItem {
	}
	
	public static class Key extends BaseModItem {
	}
	
	public static class Ointment extends BaseModItem {
	}
	
	public static class AlchemyBase extends BaseModItem {
	}
	
	public static class ItemAlias extends BaseModItem {
	}
	
	public static class Hood extends BaseModItem {
	}
	
	public static class Herb extends BaseModItem {
	}
	
	public static class Helmet extends BaseModItem {
	}
	
	public static class PerkBuffOverride extends BaseModItem {
	}
	
	public static class Document extends BaseModItem {
	}
	
	public static class RpgParam extends BaseModItem {
	}
	
	public static class Buff extends BaseModItem {
	}
	
	public static class CraftingMaterial extends BaseModItem {
	}
	
	public static class DiceBadge extends BaseModItem {
	}
	
	public static class Die extends BaseModItem {
	}
	
	public static class Food extends BaseModItem {
	}
	
	public static class ScriptParam extends BaseModItem {
	}
	
	public static class PerkExclusivity extends BaseModItem {
	}
	
	/**
	 * Pov avg programmer naming things : SoulStateEffectContextData
	 */
	public static class SoulStateEffectContext extends BaseModItem {
	}
	
	public static class InventoryPreset extends BaseModItem {
	}

	public static class BlacksmithRecipe extends BaseModItem {
	}

	public static class ArmorArchetype extends BaseModItem {
	}

	public static class ArmorSurface extends BaseModItem {
	}

	public static class ArmorType extends BaseModItem {
	}

	public static class BodyPart extends BaseModItem {
	}

	public static class BodySubPart extends BaseModItem {
	}

	public static class CraftingMaterialSubtype extends BaseModItem {
	}

	public static class CraftingMaterialType extends BaseModItem {
	}

	public static class ItemCategory extends BaseModItem {
	}

	public static class ItemTag extends BaseModItem {
	}

	public static class PickableAreaDesc extends BaseModItem {
	}

	public static class WeaponAttachmentSlotCategory extends BaseModItem {
	}

	public static class PickableAreaMaterial extends BaseModItem {
	}

	public static class KeyType extends BaseModItem {
	}

	public static class KeySubtype extends BaseModItem {
	}

	public static class ItemUiSound extends BaseModItem {
	}

	public static class DiceBadgeSubtype extends BaseModItem {
	}

	public static class MiscType extends BaseModItem {
	}

	public static class MiscSubtype extends BaseModItem {
	}

	public static class NpcToolSubtype extends BaseModItem {
	}

	public static class NpcToolType extends BaseModItem {
	}

	public static class OintmentItemSubtype extends BaseModItem {
	}

	public static class OintmentItemType extends BaseModItem {
	}

	public static class WeaponAttachmentSlot extends BaseModItem {
	}

	public static class LevelData extends BaseModItem {
	}

	public static class LevelSwitchData extends BaseModItem {
	}

	public static class TimeOfDayProfile extends BaseModItem {
	}

	public static class WeaponSubClass extends BaseModItem {
	}

	public static class DiceBadgeType extends BaseModItem {
	}

	public static class DocumentClass extends BaseModItem {
	}

	public static class DocumentVisualCategory extends BaseModItem {
	}

	public static class EquipmentPart extends BaseModItem {
	}

	public static class EquipmentSlot extends BaseModItem {
	}

	public static class FoodType extends BaseModItem {
	}

	public static class FoodSubtype extends BaseModItem {
	}

	public static class BehaviorTree extends BaseModItem {
	}
	
	@NoArgsConstructor
	@Slf4j
	@Getter
	@Setter
	public static class EmptyImpl extends BaseModItem {
		private String idKey;
	}
	
	/**
	 * {@code ModItem} representation of a Storm script entry.
	 *
	 * <p>Previously this was a flat item with a {@code HashMap}, which broke as soon
	 * as selectors/operations needed to be nested more than one level deep. Now it
	 * carries a fully-parsed {@link com.nukuhack.modforge.backend.model.Storm.StormData} object that supports arbitrary tree
	 * depth for both selectors and operations.</p>
	 *
	 * <p>The base-class {@code id} / {@code attributes} fields are still populated
	 * by {@link com.nukuhack.modforge.backend.service.ModItemBuilder} for the normal item-list
	 * display pipeline (search, filter, etc.).  The rich {@link com.nukuhack.modforge.backend.model.Storm.StormData} payload is
	 * populated separately by {@link com.nukuhack.modforge.backend.service.StormService} after the
	 * PAK scan.</p>
	 */
	@ToString
	@NoArgsConstructor
	@Slf4j
	public static class Storm extends BaseModItem {
		
		/** Fully-parsed Storm file contents. May be {@code null} if not yet loaded. */
		@Getter
		@Setter
		private StormData stormData;
	}
}
