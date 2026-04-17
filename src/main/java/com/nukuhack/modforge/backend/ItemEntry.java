package com.nukuhack.modforge.backend;

import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.model.I;
import com.nukuhack.modforge.backend.model.ModItem;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Single source of truth for every per-class mapping that used to live inside
 * {@code ItemType.MasterData}.  Each constant owns:
 * <ul>
 *   <li>the concrete {@link ModItem} subclass it represents</li>
 *   <li>all lower-cased table-name aliases that map to this class</li>
 *   <li>the XML attribute name used as the primary ID</li>
 *   <li>the endpoint key and pak-path (mirrors the enclosing {@link ItemType})</li>
 *   <li>whether the class should appear in the frontend item-type dropdown</li>
 * </ul>
 *
 * {@link ItemType} references these constants to build its lookup structures;
 * all public methods on {@code ItemType} are unaffected.
 */
@Slf4j
@NonNull
@AllArgsConstructor
public enum ItemEntry {

	MELEE_WEAPON_CLASS(I.MeleeWeaponClass.class, "id", "weapon_class", "MeleeWeaponClass", "MeleeWeaponClass", false),

	MISSILE_WEAPON_CLASS(I.MissileWeaponClass.class, "id", "weapon_class", "MissileWeaponClass", "MissileWeaponClass", false),

	DOCUMENT_VISUAL_CATEGORY(I.DocumentVisualCategory.class, "category_id", "DocumentVisualCategory", "DocumentVisualCategory", "DocumentVisualCategories", false),

	EQUIPMENT_SLOT(I.EquipmentSlot.class, "Id", "equipment_slot", "EquipmentSlot", "EquipmentSlots", false),

	SOUL_STATE_EFFECT_CONTEXT(I.SoulStateEffectContext.class, "Id", "SoulStateEffectContext", "SoulStateEffectContextData", "SoulStateEffectContexts", true),

	SCRIPT_PARAM(I.ScriptParam.class, "Name", "ScriptParams", "ScriptParam", "ScriptParams", true),

	LEVEL(I.LevelData.class, "LevelId", "level", "LevelData", "levels", true),

	LEVEL_SWITCH_DATA(I.LevelSwitchData.class, "Name", "LevelSwitch", "LevelSwitchData", "LevelSwitches", true),

	MELEE_WEAPON(I.MeleeWeapon.class, "Id", "item", "MeleeWeapon", "ItemClasses", true),
	MISSILE_WEAPON(I.MissileWeapon.class, "Id", "item", "MissileWeapon", "ItemClasses", true),
	AMMO(I.Ammo.class, "Id", "item", "Ammo", "ItemClasses", true),
	ARMOR(I.Armor.class, "Id", "item", "Armor", "ItemClasses", true),
	HELMET(I.Helmet.class, "Id", "item", "Helmet", "ItemClasses", true),
	HOOD(I.Hood.class, "Id", "item", "Hood", "ItemClasses", true),
	FOOD(I.Food.class, "Id", "item", "Food", "ItemClasses", true),
	POISON(I.Poison.class, "Id", "item", "Poison", "ItemClasses", true),
	HERB(I.Herb.class, "Id", "item", "Herb", "ItemClasses", true),
	CRAFTING_MATERIAL(I.CraftingMaterial.class, "Id", "item", "CraftingMaterial", "ItemClasses", true),
	ITEM_ALIAS(I.ItemAlias.class, "Id", "item", "ItemAlias", "ItemClasses", true),
	NPC_TOOL(I.NPCTool.class, "Id", "item", "NPCTool", "ItemClasses", true),
	MISC_ITEM(I.MiscItem.class, "Id", "item", "MiscItem", "ItemClasses", true),
	DOCUMENT(I.Document.class, "Id", "item", "Document", "ItemClasses", true),
	DIE(I.Die.class, "Id", "item", "Die", "ItemClasses", true),
	QUICK_SLOT_CONTAINER(I.QuickSlotContainer.class, "Id", "item", "QuickSlotContainer", "ItemClasses", true),
	DICE_BADGE(I.DiceBadge.class, "Id", "item", "DiceBadge", "ItemClasses", true),
	PICKABLE_ITEM(I.PickableItem.class, "Id", "item", "PickableItem", "ItemClasses", true),
	KEY(I.Key.class, "Id", "item", "Key", "ItemClasses", true),
	MONEY(I.Money.class, "Id", "item", "Money", "ItemClasses", true),
	KEY_RING(I.KeyRing.class, "Id", "item", "KeyRing", "ItemClasses", true),
	OINTMENT(I.Ointment.class, "Id", "item", "Ointment", "ItemClasses", true),
	ALCHEMY_BASE(I.AlchemyBase.class, "Id", "item", "AlchemyBase", "ItemClasses", true),

	STORM(I.Storm.class, "id", "storm", "storm", "storm", true),

	INVENTORY_PRESET(I.InventoryPreset.class, "Name", "InventoryPreset", "InventoryPreset", "InventoryPresets", true, true),
	BLACKSMITH_RECIPE(I.BlacksmithRecipe.class, "Id", "BlacksmithRecipes", "BlacksmithRecipe", "BlacksmithRecipes", true, true),
	BEHAVIOR_TREE(I.BehaviorTree.class, "name", "behaviortrees", "BehaviorTree", "BehaviorTrees", true, true),

	WEAPON_ATTACHMENT_SLOT_CATEGORY(I.WeaponAttachmentSlotCategory.class, "weapon_attachment_slot_category", "category_id", true),
	PERK_EXCLUSIVITY(I.PerkExclusivity.class, "perk2perk_exclusivity",  "first_perk_id", true),
	PERK_SCRIPT(I.PerkScript.class, "perk_script", "perk_id", true),
	PERK_BUFF_OVERRIDE(I.PerkBuffOverride.class, "perk_buff_override", "perk_id", true),
	PERK(I.Perk.class, "perk", true),
	BUFF(I.Buff.class, "buff", true),
	RPG_PARAM(I.RpgParam.class, "rpg_param", "rpg_param_key", true),
	PERK_BUFF(I.PerkBuff.class, "perk_buff", "perk_id", true),
	ITEM_TAG(I.ItemTag.class, "item_tag", "item_tag_name"),
	ITEM_UI_SOUND(I.ItemUiSound.class, "item_ui_sound", "item_ui_sound_name"),
	PICKABLE_AREA_DESC(I.PickableAreaDesc.class, "pickable_area_desc", "id"),
	ARMOR_SURFACE(I.ArmorSurface.class, "armor_surface", "Name"),
	ARMOR_TYPE(I.ArmorType.class, "armor_type", "Id"),
	PICKABLE_AREA_MATERIAL(I.PickableAreaMaterial.class, "pickable_area_material", "material_name"),
	TimeOfDayProfile(I.TimeOfDayProfile.class, "time_of_day_profile", "Name"),

	ITEM_CATEGORY(I.ItemCategory.class),
	FOOD_SUBTYPE(I.FoodSubtype.class),
	EQUIPMENT_PART(I.EquipmentPart.class),
	FOOD_TYPE(I.FoodType.class),
	CRAFTING_MATERIAL_SUBTYPE(I.CraftingMaterialSubtype.class),
	CRAFTING_MATERIAL_TYPE(I.CraftingMaterialType.class),
	DICE_BADGE_SUBTYPE(I.DiceBadgeSubtype.class),
	DICE_BADGE_TYPE(I.DiceBadgeType.class),
	DOCUMENT_CLASS(I.DocumentClass.class),
	BODY_PART(I.BodyPart.class),
	BODY_SUBPART(I.BodySubPart.class),
	ARMOR_ARCHETYPE(I.ArmorArchetype.class),
	KEY_TYPE(I.KeyType.class),
	KEY_SUBTYPE(I.KeySubtype.class),
	MiscType(I.MiscType.class),
	MiscSubtype(I.MiscSubtype.class),
	NpcToolSubtype(I.NpcToolSubtype.class),
	NpcToolType(I.NpcToolType.class),
	OintmentItemSubtype(I.OintmentItemSubtype.class),
	OintmentItemType(I.OintmentItemType.class),
	WeaponAttachmentSlot(I.WeaponAttachmentSlot.class),
	WeaponSubClass(I.WeaponSubClass.class),
	;
	
	private static final Map<Class<? extends ModItem>, ItemEntry> BY_CLASS = new HashMap<>();
	
	static {
		for (var entry : values()) {
			BY_CLASS.put(entry.clazz, entry);
		}
	}
	
	/** The concrete ModItem subclass this constant represents. */
	public final Class<? extends ModItem> clazz;
	/**
	 * The XML attribute name used as the primary ID for this item class
	 * (e.g. {@code "Id"}, {@code "perk_id"}, {@code "buff_id"}, {@code "id"}).
	 */
	public final String idKey;
	/**
	 * The short XML key used as the endpoint name in zip entries
	 * (e.g. {@code "item"}, {@code "perk"}, {@code "storm"}).
	 */
	public final String fileName;
	/**
	 * The short key used to create Object from XML Elements and vice versa
	 * (e.g. {@code "MeleeWeapon"} → {@code "meleeweapon"},
	 *  {@code "NPCTool"} → {@code "npctool"}),
	 *  {@code "ScriptParam"} → {@code "ScriptParams"}).
	 */
	
	public final String xmlObjName;
	/**
	 * The short key used to create the Root/Parent XML Element
	 * (e.g. {@code "MeleeWeapon"} → {@code "ItemClasses"},
	 *  {@code "Storm"} → {@code "storm"}).
	 */
	public final String parentName;
	/**
	 * Whether this class should appear in the frontend item-type dropdown.
	 * Weapon-class entries are excluded ({@code false}); everything else is {@code true}.
	 */
	public final boolean showInDisplay;
	
	/** Whether children of the root element should be parsed as a nested XmlNode tree. */
	public final boolean isTree;

	ItemEntry(Class<? extends ModItem> clazz, String idKey, String fileName, String xmlObjName, String parentName, boolean showInDisplay) {
		this(clazz, idKey, fileName, xmlObjName, parentName, showInDisplay, false);
	}

	//KEY_SUBTYPE(I.KeySubtype.class, "key_subtype_id", "key_subtype", "key_subtype", "key_subtypes", false),
	ItemEntry(Class<? extends ModItem> clazz) {
		this(clazz, Util.convertCase(clazz.getSimpleName()));
	}
	ItemEntry(Class<? extends ModItem> clazz, boolean showInDisplay) {
		this(clazz, Util.convertCase(clazz.getSimpleName()), showInDisplay);
	}
	ItemEntry(Class<? extends ModItem> clazz, String fileName) {
		this(clazz, fileName, fileName+"_id");
	}
	ItemEntry(Class<? extends ModItem> clazz, String fileName, boolean showInDisplay) {
		this(clazz, fileName, fileName+"_id", showInDisplay);
	}
	ItemEntry(Class<? extends ModItem> clazz, String fileName, String idKey) {
		this(clazz, idKey, fileName, fileName, fileName+"s", false);
	}
	ItemEntry(Class<? extends ModItem> clazz, String fileName, String idKey, boolean showInDisplay) {
		this(clazz, idKey, fileName, fileName, fileName+"s", showInDisplay);
	}
	
	public static ItemEntry forClass(Class<? extends ModItem> clazz) {
		return BY_CLASS.get(clazz);
	}
	
	/** A predicate that accepts any {@link ModItem} whose runtime class is {@link #clazz}. */
	public Predicate<ModItem> matcher() {
		return clazz::isInstance;
	}
	
	/**
	 * Formats the simple class name into a human-readable display name
	 * (e.g. {@code "MeleeWeapon"} → {@code "Melee Weapon"},
	 *  {@code "NPCTool"} → {@code "NPC Tool"}).
	 */
	public String displayName() {
		var s = clazz.getSimpleName();
		s = s.replaceAll("(?<=[A-Z])(?=[A-Z][a-z])|(?<=[a-z])(?=[A-Z])", " ");
		return s.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
	}
	
	public String getVersion() {
		if (parentName.equals("ItemClasses"))
			return "8";
		return "1";
	}
}