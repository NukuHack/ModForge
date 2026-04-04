package modforge.backend;

import modforge.backend.model.*;
import modforge.backend.model.item.*;

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
public enum ItemEntry {
	
	// ── Weapons ─────────────────────────────────────────────────────────────
	MELEE_WEAPON(MeleeWeapon.class, "Id", C.TABLES, "item", true, "meleeweapon"),
	
	MISSILE_WEAPON(MissileWeapon.class, "Id", C.TABLES, "item", true, "missileweapon"),
	
	AMMO(Ammo.class, "Id", C.TABLES, "item", true, "ammo"),
	
	MELEE_WEAPON_CLASS(MeleeWeaponClass.class, "id", C.TABLES, "weapon_class", false, "meleeweaponclass"),
	
	MISSILE_WEAPON_CLASS(MissileWeaponClass.class, "id", C.TABLES, "weapon_class", false, "missileweaponclass"),
	
	// ── Armor ────────────────────────────────────────────────────────────────
	ARMOR(Armor.class, "Id", C.TABLES, "item", true, "armor"),
	
	HELMET(Helmet.class, "Id", C.TABLES, "item", true, "helmet"),
	
	HOOD(Hood.class, "Id", C.TABLES, "item", true, "hood"),
	
	// ── Consumables ──────────────────────────────────────────────────────────
	FOOD(Food.class, "Id", C.TABLES, "item", true, "food"),
	
	POISON(Poison.class, "Id", C.TABLES, "item", true, "poison"),
	
	// ── Crafting ─────────────────────────────────────────────────────────────
	HERB(Herb.class, "Id", C.TABLES, "item", true, "herb"),
	
	CRAFTING_MATERIAL(CraftingMaterial.class, "Id", C.TABLES, "item", true, "craftingmaterial"),
	
	// ── Misc ─────────────────────────────────────────────────────────────────
	NPC_TOOL(NPCTool.class, "Id", C.TABLES, "item", true, "npctool"),
	
	MISC_ITEM(MiscItem.class, "Id", C.TABLES, "item", true, "miscitem"),
	
	GAME_DOCUMENT(GameDocument.class, "Id", C.TABLES, "item", true, "gamedocument"),
	
	DIE(Die.class, "Id", C.TABLES, "item", true, "die"),
	
	ITEM_ALIAS(ItemAlias.class, "Id", C.TABLES, "item", true, "itemalias"),
	
	QUICK_SLOT_CONTAINER(QuickSlotContainer.class, "Id", C.TABLES, "item", true, "quickslotcontainer"),
	
	DICE_BADGE(DiceBadge.class, "Id", C.TABLES, "item", true, "dicebadge"),
	
	PICKABLE_ITEM(PickableItem.class, "Id", C.TABLES, "item", true, "pickableitem"),
	
	KEY(Key.class, "Id", C.TABLES, "item", true, "key"),
	
	MONEY(Money.class, "Id", C.TABLES, "item", true, "money"),
	
	KEY_RING(KeyRing.class, "Id", C.TABLES, "item", true, "keyring"),
	
	// ── Perks / Buffs / Scripts ───────────────────────────────────────────────
	PERK(Perk.class, "perk_id", C.TABLES, "perk", true, "perk"),
	
	BUFF(Buff.class, "buff_id", C.TABLES, "buff", true, "buff"),
	
	PERK_BUFF(PerkBuff.class, "perk_id", C.TABLES, "perk_buff", true, "perk_buff"),
	
	PERK_SCRIPT(PerkScript.class, "perk_id", C.TABLES, "perk_script", true, "perk_script"),
	
	// ── Storm ────────────────────────────────────────────────────────────────
	STORM(Storm.class, "id", C.STORM, "storm", true, "storm");
	
	// ────────────────────────────────────────────────────────────────────────
	// Fields
	// ────────────────────────────────────────────────────────────────────────
	
	/** The concrete ModItem subclass this constant represents. */
	public final Class<? extends ModItem> clazz;
	
	/**
	 * The XML attribute name used as the primary ID for this item class
	 * (e.g. {@code "Id"}, {@code "perk_id"}, {@code "buff_id"}, {@code "id"}).
	 */
	public final String idKey;
	
	/**
	 * Pak-path (e.g. {@code "Data/Tables.pak"}).
	 * Ignored at Mod load
	 */
	public final String endpointPath;
	
	/**
	 * The XML element name used for quick lookup in the builder
	 * All lower-cased table-name aliases that resolve to this class.
	 * (e.g. {@code "perk"}, {@code "perk_buff"}, {@code "buff"}).
	 */
	public final String simpleName;
	
	/**
	 * The short XML key used as the endpoint name in zip entries
	 * (e.g. {@code "item"}, {@code "perk"}, {@code "storm"}).
	 */
	public final String endpointKey;
	
	/**
	 * Whether this class should appear in the frontend item-type dropdown.
	 * Weapon-class entries are excluded ({@code false}); everything else is {@code true}.
	 */
	public final boolean showInDisplay;
	
	// ────────────────────────────────────────────────────────────────────────
	// Constructor
	// ────────────────────────────────────────────────────────────────────────
	
	ItemEntry(Class<? extends ModItem> clazz, String idKey, String endpointPath, String endpointKey, boolean showInDisplay, String simpleName) {
		this.clazz = clazz;
		this.idKey = idKey;
		this.endpointPath = endpointPath;
		this.endpointKey = endpointKey;
		this.showInDisplay = showInDisplay;
		this.simpleName = simpleName;
	}
	
	// ────────────────────────────────────────────────────────────────────────
	// Convenience helpers (used by ItemType internally)
	// ────────────────────────────────────────────────────────────────────────
	
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
		String s = clazz.getSimpleName();
		s = s.replaceAll("(?<=[A-Z])(?=[A-Z][a-z])|(?<=[a-z])(?=[A-Z])", " ");
		s = s.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
		return s;
	}
}