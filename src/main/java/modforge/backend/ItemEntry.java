package modforge.backend;

import lombok.extern.slf4j.Slf4j;
import modforge.backend.model.I;
import modforge.backend.model.ModItem;

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
public enum ItemEntry {
	
	// ── Weapons ─────────────────────────────────────────────────────────────
	MELEE_WEAPON(I.MeleeWeapon.class, "Id", "item", true),
	
	MISSILE_WEAPON(I.MissileWeapon.class, "Id", "item", true),
	
	AMMO(I.Ammo.class, "Id", "item", true),
	
	MELEE_WEAPON_CLASS(I.MeleeWeaponClass.class, "id", "weapon_class", false),
	
	MISSILE_WEAPON_CLASS(I.MissileWeaponClass.class, "id", "weapon_class", false),
	
	// ── Armor ────────────────────────────────────────────────────────────────
	ARMOR(I.Armor.class, "Id", "item", true),
	
	HELMET(I.Helmet.class, "Id", "item", true),
	
	HOOD(I.Hood.class, "Id", "item", true),
	
	// ── Consumables ──────────────────────────────────────────────────────────
	FOOD(I.Food.class, "Id", "item", true),
	
	POISON(I.Poison.class, "Id", "item", true),
	
	// ── Crafting ─────────────────────────────────────────────────────────────
	HERB(I.Herb.class, "Id", "item", true),
	
	CRAFTING_MATERIAL(I.CraftingMaterial.class, "Id", "item", true),
	
	// ── Misc ─────────────────────────────────────────────────────────────────
	NPC_TOOL(I.NPCTool.class, "Id", "item", true),
	
	MISC_ITEM(I.MiscItem.class, "Id", "item", true),
	
	DOCUMENT(I.Document.class, "Id", "item", true), // ItemClasses version="8" - BlacksmithRecipeId
	
	DIE(I.Die.class, "Id", "item", true),
	
	ITEM_ALIAS(I.ItemAlias.class, "Id", "item", true),
	
	QUICK_SLOT_CONTAINER(I.QuickSlotContainer.class, "Id", "item", true),
	
	DICE_BADGE(I.DiceBadge.class, "Id", "item", true),
	
	PICKABLE_ITEM(I.PickableItem.class, "Id", "item", true),
	
	KEY(I.Key.class, "Id", "item", true),
	
	MONEY(I.Money.class, "Id", "item", true),
	
	KEY_RING(I.KeyRing.class, "Id", "item", true),
	
	OINTMENT(I.Ointment.class, "Id", "item", true),
	
	ALCHEMY_BASE(I.AlchemyBase.class, "Id", "item", true),
	
	// ── Perks / Buffs related ───────────────────────────────────────────────
	PERK(I.Perk.class, "perk_id", "perk", true),
	
	BUFF(I.Buff.class, "buff_id", "buff", true),
	
	RPG_PARAM(I.RpgParam.class, "rpg_param_key", "rpg_param", "rpg_param", true),
	
	PERK_BUFF(I.PerkBuff.class, "perk_id", "perk_buff", "perk_buff", true),
	
	PERK_BUFF_OVERRIDE(I.PerkBuffOverride.class, "perk_id", "perk_buff_override", "perk_buff_override", true),
	
	PERK_SCRIPT(I.PerkScript.class, "perk_id", "perk_script", "perk_script", true),
	
	SCRIPT_PARAM(I.ScriptParam.class, "Name", "ScriptParams", "ScriptParam", true),
	
	PERK_EXCLUSIVITY(I.PerkExclusivity.class, "first_perk_id", "perk2perk_exclusivity", "perk2perk_exclusivity", true),
	
	// ── Storm ────────────────────────────────────────────────────────────────
	STORM(I.Storm.class, "id", "storm", true);
	
	// ────────────────────────────────────────────────────────────────────────
	// Inner data
	// ────────────────────────────────────────────────────────────────────────
	
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
	 *  {@code "NPCTool"} → {@code "npctool"}).
	 */
	public final String objName;
	/**
	 * Whether this class should appear in the frontend item-type dropdown.
	 * Weapon-class entries are excluded ({@code false}); everything else is {@code true}.
	 */
	public final boolean showInDisplay;
	
	// ────────────────────────────────────────────────────────────────────────
	// Constructors
	// ────────────────────────────────────────────────────────────────────────
	
	/** Constructor with an explicit label override. */
	ItemEntry(Class<? extends ModItem> clazz, String idKey, String fileName, String objName, boolean showInDisplay) {
		this.clazz = clazz;
		this.idKey = idKey;
		this.fileName = fileName;
		this.showInDisplay = showInDisplay;
		this.objName = objName;
	}
	
	ItemEntry(Class<? extends ModItem> clazz, String idKey, String fileName, boolean showInDisplay) {
		this(clazz, idKey, fileName, clazz.getSimpleName().toLowerCase(), showInDisplay);
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
	
	public String parentName() {
		if (fileName.endsWith("s"))
			return fileName;
		return fileName + "s";
		// TODO : some of the items have special parent stuffs so ... I'll do it later
	}
}