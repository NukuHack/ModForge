package com.nukuhack.modforge.backend.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * I -> Item
 * Contains all the item-types stored in the database
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class I {
	public static class Ammo extends ModItem {
	}
	
	public static class Armor extends ModItem {
	}
	
	public static class QuickSlotContainer extends ModItem {
	}
	
	public static class Poison extends ModItem {
	}
	
	public static class PickableItem extends ModItem {
	}
	
	public static class PerkScript extends ModItem {
	}
	
	public static class PerkBuff extends ModItem {
	}
	
	public static class Perk extends ModItem {
	}
	
	public static class NPCTool extends ModItem {
	}
	
	public static class Money extends ModItem {
	}
	
	public static class MissileWeaponClass extends ModItem {
	}
	
	public static class MissileWeapon extends ModItem {
	}
	
	public static class MiscItem extends ModItem {
	}
	
	public static class MeleeWeaponClass extends ModItem {
	}
	
	public static class MeleeWeapon extends ModItem {
	}
	
	public static class KeyRing extends ModItem {
	}
	
	public static class Key extends ModItem {
	}
	
	public static class Ointment extends ModItem {
	}
	
	public static class AlchemyBase extends ModItem {
	}
	
	public static class ItemAlias extends ModItem {
	}
	
	public static class Hood extends ModItem {
	}
	
	public static class Herb extends ModItem {
	}
	
	public static class Helmet extends ModItem {
	}
	
	public static class PerkBuffOverride extends ModItem {
	}
	
	public static class Document extends ModItem {
	}
	
	public static class RpgParam extends ModItem {
	}
	
	public static class Buff extends ModItem {
	}
	
	public static class CraftingMaterial extends ModItem {
	}
	
	public static class DiceBadge extends ModItem {
	}
	
	public static class Die extends ModItem {
	}
	
	public static class Food extends ModItem {
	}
	
	public static class ScriptParam extends ModItem {
	}
	
	public static class PerkExclusivity extends ModItem {
	}
	
	/**
	 * Pov avg programmer naming things : SoulStateEffectContextData
	 */
	public static class SoulStateEffectContext extends ModItem {
	}
	
	public static class InventoryPreset extends ModItem {
	}

	public static class BlacksmithRecipe extends ModItem {
	}

	public static class ArmorArchetype extends ModItem {
	}

	public static class ArmorSurface extends ModItem {
	}

	public static class ArmorType extends ModItem {
	}

	public static class BodyPart extends ModItem {
	}

	public static class BodySubPart extends ModItem {
	}

	public static class CraftingMaterialSubtype extends ModItem {
	}

	public static class CraftingMaterialType extends ModItem {
	}

	public static class ItemCategory extends ModItem {
	}

	public static class ItemTag extends ModItem {
	}

	public static class PickableAreaDesc extends ModItem {
	}

	public static class WeaponAttachmentSlotCategory extends ModItem {
	}

	public static class PickableAreaMaterial extends ModItem {
	}

	public static class KeyType extends ModItem {
	}

	public static class KeySubtype extends ModItem {
	}

	public static class ItemUiSound extends ModItem {
	}

	public static class DiceBadgeSubtype extends ModItem {
	}

	public static class MiscType extends ModItem {
	}

	public static class MiscSubtype extends ModItem {
	}

	public static class NpcToolSubtype extends ModItem {
	}

	public static class NpcToolType extends ModItem {
	}

	public static class OintmentItemSubtype extends ModItem {
	}

	public static class OintmentItemType extends ModItem {
	}

	public static class WeaponAttachmentSlot extends ModItem {
	}

	public static class LevelData extends ModItem {
	}

	public static class LevelSwitchData extends ModItem {
	}

	public static class TimeOfDayProfile extends ModItem {
	}

	public static class WeaponSubClass extends ModItem {
	}

	public static class DiceBadgeType extends ModItem {
	}

	public static class DocumentClass extends ModItem {
	}

	public static class DocumentVisualCategory extends ModItem {
	}

	public static class EquipmentPart extends ModItem {
	}

	public static class EquipmentSlot extends ModItem {
	}

	public static class FoodType extends ModItem {
	}

	public static class FoodSubtype extends ModItem {
	}

	public static class BehaviorTree extends ModItem {
	}
	
	@NoArgsConstructor
	@Slf4j
	@Getter
	@Setter
	public static class EmptyImpl extends ModItem {
		private String idKey;
	}
}
