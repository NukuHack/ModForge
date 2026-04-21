package com.nukuhack.modforge.backend.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * E -> Enum
 * Contains all the Enums stored in database, nice
 * Realised these are actually stored as data, so instead of enum we should switch to the read values
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class E {
	
	@Getter
	@RequiredArgsConstructor
	public enum AmmoClass implements ValueEnum {
		ARROW(1),
		BOLT(2),
		BALL(3),
		SHOTGUN(4);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum ArmorArchetype implements ValueEnum {
		BODY_PLATE_CUIRASS_DOUBLE_LONG(0),
		BODY_PLATE_CUIRASS_FRONT_SHORT(1),
		BODY_PLATE_CUIRASS_FRONT_LONG(2),
		BODY_PLATE_CUIRASS_DOUBLE_SHORT(3),
		BODY_PLATE_CUIRASS_FULL_SHORT(10),
		BODY_PLATE_CUIRASS_FULL_LONG(11),
		BODY_PLATE_CUIRASS_FULL_LONG_LONG_SLEEVES(12),
		BODY_PLATE_CUIRASS_FULL_LONG_SHORT_SLEEVES(13),
		BODY_CHAIN_VEST_SHORT(14),
		BODY_CHAIN_SHIRT_SHORT_SLEEVES(15),
		BODY_CHAIN_SHIRT_LONG_SLEEVES(16),
		BODY_CHAIN_VEST_LONG(17),
		BODY_CHAIN_SHIRT_LONG_SHORT_SLEEVES(18),
		BODY_CHAIN_SHIRT_LONG_LONG_SLEEVES(19),
		BODY_CHAIN_OVERALL_NO_SLEEVES(20),
		BODY_CHAIN_OVERALL_LONG_SLEEVES(21),
		BODY_CLOTH_SHIRT_NO_SLEEVES(22),
		BODY_CLOTH_SHIRT_SHORT_SLEEVES(23),
		BODY_CLOTH_SHIRT_LONG_SLEEVES(24),
		BODY_CLOTH_SHIRT_LONG_NO_SLEEVES(25),
		BODY_CLOTH_SHIRT_LONG_SHORT_SLEEVES(26),
		BODY_CLOTH_SHIRT_LONG_LONG_SLEEVES(27),
		ARMS_PLATE_SHOULDER_GUARDS(28),
		ARMS_PLATE_BACK_GUARDS_SHORT(29),
		ARMS_PLATE_FULL_GUARDS_SHORT(30),
		ARMS_PLATE_FULL_GUARDS_LONG(31),
		ARMS_PLATE_COMBINED_LONG(32),
		ARMS_PLATE_BACK_GUARDS_LONG(33),
		ARMS_PLATE_FOREARM_BACK(34),
		ARMS_PLATE_FOREARM_FULL(35),
		HEAD_CLOTH_CAP(36),
		HEAD_CLOTH_FULL_MASK(37),
		HEAD_CHAIN_CAP(38),
		HEAD_PLATE_HAT_SMALL(39),
		HEAD_PLATE_HAT_BIG(40),
		HEAD_PLATE_HELM_OPEN(41),
		HEAD_PLATE_HELM_OPEN_COLLAR(42),
		HEAD_PLATE_HELM_FULL(43),
		HEAD_PLATE_HELM_FULL_COLLAR(44),
		HANDS_GLOVES(45),
		HANDS_GLOVES_PLATE_TO_BE_DELETED(46),
		LEGS_CLOTH_TROUSERS_LONG(47),
		LEGS_CLOTH_TROUSERS_SHORT(48),
		LEGS_PLATE_PROTS_UPPER_FRONT(51),
		LEGS_PLATE_PROTS_UPPER_FULL(52),
		LEGS_PLATE_PROTS_LOWER_FRONT(53),
		LEGS_PLATE_PROTS_LOWER_FULL(54),
		LEGS_PLATE_PROTS_LONG_FRONT(55),
		LEGS_PLATE_PROTS_LONG_FULL(56),
		LEGS_PLATE_PROTS_LONG_FRONT_WITH_FEET(57),
		LEGS_PLATE_PROTS_LONG_FULL_WITH_FEET(58),
		COAT_NO_SLEEVES(67),
		HORSE_TRAPPINGS_FULL(74),
		HORSE_SADDLE(75),
		HORSE_ARMOR_HEAD_NECK(76),
		HORSE_BRIDLE(77),
		TMP_HOOD_ON(78),
		TMP_HOOD_DOWN(79),
		RING(80),
		SPUR(81),
		NECKLACE(82),
		COAT(83),
		ARMS_PLATE_FULL_BACK_WITH_SHOULDERS(84),
		ARMS_PLATE_FULL_COMPLETE_WITH_SHOULDERS(85),
		BODY_DECOR_SHIRT_LONG_SHORT_SLEEVES(86),
		HORSE_SHOE(87),
		HEAD_CHAIN_CAP_COLLAR_ONLY(88),
		HEAD_CHAIN_CAP_WITH_COLLAR(89),
		HEAD_CLOTH_CAP_WITH_COLLAR(90),
		BODY_DECOR_LONG_NO_SLEEVES(91),
		BODY_DECOR_ULTRA_LONG_NO_SLEEVES(92),
		ROBE_CAPE(93),
		COLLAR(94),
		HOOD_ON(95),
		HOOD_DOWN(96),
		BOOTS(97),
		BOOTS_ANKLE(98),
		BOOTS_KNEE(99),
		UBERLOD_PLACEHOLDER(100),
		COAT_BERGOV(102),
		BELT(103),
		POUCH(104),
		CATTLE_PAINT(105),
		LEGS_CLOTH_TROUSERS_LONG_WITH_FEET(106);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum BodyLayerType implements ValueEnum {
		BODY(0),
		CLOTH(1),
		CHAINMAIL(2),
		PLATE(3),
		DECORATION(4);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum ArmorSurface implements ValueEnum {
		MAT_FABRIC(0),
		MAT_LEATHER(1),
		MAT_CHAINMAIL(2),
		MAT_PLATE(3),
		MAT_BELLS(4);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum BuffClass implements ValueEnum {
		TESTING_STAT_BUFF(0),
		SYSTEM_BUFF(1),
		WEAPON_SKILL_BUFF(2),
		TESTING_COMBAT_BUFF(3),
		PERK_BUFF(4),
		INJURY(5),
		HEAL(6),
		POISON(7),
		PERCEPTION(8),
		OVEREAT(9),
		ALCOHOL(10),
		ITEM_BUFF(12),
		POTION(13),
		FOOD_POISON(14),
		SCRIPT_SYSTEM(15),
		UNCONSCIOUSNESS(16),
		HANGOVER(17),
		SATISFACTION(18),
		PERFUME(19),
		PUNISHMENT(20),
		FORCED_DRUNKENESS(21);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum BuffAiTag implements ValueEnum {
		ALCOHOL_MOOD(0),
		ALCOHOL_DRUNK(1),
		ALCOHOL_BLACKOUT(2),
		POISON(3),
		BLEED(4),
		SLEEP(5),
		OVEREAT(6),
		INJURY(7),
		SAVEGAME(8);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum BuffExclusivity implements ValueEnum {
		NO_EXCLUSIVITY(0),
		ID_EXCLUSIVE(1),
		MOD_EXCLUSIVE(2),
		CLASS_EXCLUSIVE(3),
		TARGET_EXCLUSIVE(4);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum BuffFamily implements ValueEnum {
		AESOP_POTION(1),
		ANTIDOTE(2),
		AQUA_VITALIS_POTION(3),
		ARTEMISIA_POTION(4),
		BANE_POTION(5),
		BARD_POTION(6),
		BOWMANS_BREW(7),
		BUCKS_BLOOD_POTION(8),
		CHAMOMILE_DECOCTION(9),
		COCKEREL_POTION(10),
		DOLLMAKER_POTION(11),
		EMBROCATION(12),
		HAIR_O_DOG_POTION(13),
		LULLABAY_POTION(14),
		MARIGOLD_DECOCTION(15),
		NIGHTHAWK_POTION(16),
		PADFOOT_POTION(17),
		PAINKILLER_POTION(18),
		SAVEGAME_POTION(19),
		FOOD_AGI_EXP_MULTIPLIER(20),
		FOOD_STR_EXP_MULTIPLIER(21),
		FOOD_VIT_EXP_MULTIPLIER(22),
		ALCOHOL_CRAVING(23),
		FOOD_POISONING(24),
		PAINTER_MEDICINE(25);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum BuffLifetime implements ValueEnum {
		SHORT_TERM(0),
		LONG_TERM(1);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum BuffUiType implements ValueEnum {
		DEFAULT(0),
		BUFF(1),
		DEBUFF(2),
		INJURY_DEBUFF(3),
		PERK(4);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum BuffUiVisibility implements ValueEnum {
		NONE(0),
		HUD(1),
		INVENTORY(2),
		ALL(3);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum CraftingMaterialSubtype implements ValueEnum {
		TUSK(1),
		HORN(2),
		Q_DISGUISE_ANY_FEATHER(3),
		LARD(4),
		DRYABLE(5),
		BEZOAR(6),
		CRAFTING_SKIN(7);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum CraftingMaterialType implements ValueEnum {
		HERB(1),
		SPECIAL(2),
		BLACKSMITHING(3);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum DiceBadgeSubtype implements ValueEnum {
		HEADSTART(0),
		FORMATIONS(1),
		NULL(2),
		EXTRA_VALUE(3),
		ANTIBUST(4),
		DOUBLE_TAKE(5),
		MULTIPLIER(6),
		EXTRA_DICE(7),
		REROLL_DICE(8),
		SET_DICE(9),
		REROLL_PIPS(10);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum DiceBadgeType implements ValueEnum {
		PLUMB(0),
		SILVER(1),
		GOLD(2);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum DocumentClass implements ValueEnum {
		BOOK(0),
		RECIPE(1),
		SKILL_BOOK(2),
		MAP(3),
		LETTER(4);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum DocumentVisualCategory implements ValueEnum {
		PLAIN(1),
		PLAIN_LINED(2),
		SIMPLE_FLOURISH(3),
		SIMPLE_INITIALS(4),
		SIMPLE_INITIALS_FLOURISH(5),
		ADORNED(6),
		EMBELLISHED(7);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum ExcludeInGameMode implements ValueEnum {
		NORMAL(1),
		HARDCORE(2);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum FoodSubtype implements ValueEnum {
		APPLE(0),
		BACON(2),
		WINE(3),
		NIGHTHAWK_POTION(4),
		POISON(5),
		RAW(6),
		BEER(7),
		LIQUOR(8),
		PRESERVER(9),
		AESOP_POTION(10),
		WATER(11),
		AQUA_VITALIS_POTION(12),
		PEAR(13),
		VEGETABLE(14),
		DRYABLE(15),
		EMBROCATION_POTION(16),
		PAINKILLER_POTION(17),
		CHAMOMILE_DECOCTION_POTION(18),
		INSOMNIA_POTION(19),
		HAIR_O_DOG_POTION(20),
		BARD_POTION(21),
		ARTEMISIA_POTION(22),
		ANTIDOTE_POTION(23),
		MARIGOLD_POTION(24),
		STAMINA_POTION(25),
		PADFOOT_POTION(26),
		BOWMAN_BREW_POTION(27),
		PAINTER_MEDICINE_POTION(28);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum FoodType implements ValueEnum {
		FRUIT(0),
		POTION(1),
		MEAT(2),
		DRINK(3),
		BOWEL(5),
		VEGETABLE(6),
		MUSHROOM(7),
		PASTRY(8),
		DAIRY(9);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum ItemCategory implements ValueEnum {
		MISC(0),
		MELEE_WEAPON(1),
		MISSILE_WEAPON(2),
		AMMO(3),
		ARMOR(4),
		FOOD(5),
		MONEY(6),
		DICE_BADGE(7),
		DOCUMENT(8),
		CRAFTING_MATERIAL(9),
		HERB(10),
		ALCHEMY_BASE(11),
		NPC_TOOL(12),
		OINTMENT_ITEM(13),
		POISON(14),
		DIE(15),
		HELMET(16),
		KEY(17),
		KEYRING(18),
		QUICK_SLOT_CONTAINER(19),
		ITEM(21),
		PICKABLE_ITEM(22),
		DIVISIBLE_ITEM(23),
		WEAPON_EQUIP(24),
		PLAYER_ITEM(25),
		EQUIPPABLE_ITEM(26),
		WEAPON(27),
		CONSUMABLE_ITEM(28),
		HOOD(29);
		
		private static final Map<ItemCategory, Boolean> INSTANCEABLE_MAP = new HashMap<>();
		
		static {
			INSTANCEABLE_MAP.put(MISC, true);
			INSTANCEABLE_MAP.put(MELEE_WEAPON, true);
			INSTANCEABLE_MAP.put(MISSILE_WEAPON, true);
			INSTANCEABLE_MAP.put(AMMO, true);
			INSTANCEABLE_MAP.put(ARMOR, true);
			INSTANCEABLE_MAP.put(FOOD, true);
			INSTANCEABLE_MAP.put(MONEY, true);
			INSTANCEABLE_MAP.put(DICE_BADGE, true);
			INSTANCEABLE_MAP.put(DOCUMENT, true);
			INSTANCEABLE_MAP.put(CRAFTING_MATERIAL, true);
			INSTANCEABLE_MAP.put(HERB, true);
			INSTANCEABLE_MAP.put(ALCHEMY_BASE, true);
			INSTANCEABLE_MAP.put(NPC_TOOL, true);
			INSTANCEABLE_MAP.put(OINTMENT_ITEM, true);
			INSTANCEABLE_MAP.put(POISON, true);
			INSTANCEABLE_MAP.put(DIE, true);
			INSTANCEABLE_MAP.put(HELMET, true);
			INSTANCEABLE_MAP.put(KEY, true);
			INSTANCEABLE_MAP.put(KEYRING, true);
			INSTANCEABLE_MAP.put(QUICK_SLOT_CONTAINER, true);
			INSTANCEABLE_MAP.put(ITEM, false);
			INSTANCEABLE_MAP.put(PICKABLE_ITEM, false);
			INSTANCEABLE_MAP.put(DIVISIBLE_ITEM, false);
			INSTANCEABLE_MAP.put(WEAPON_EQUIP, false);
			INSTANCEABLE_MAP.put(PLAYER_ITEM, false);
			INSTANCEABLE_MAP.put(EQUIPPABLE_ITEM, false);
			INSTANCEABLE_MAP.put(WEAPON, false);
			INSTANCEABLE_MAP.put(CONSUMABLE_ITEM, false);
			INSTANCEABLE_MAP.put(HOOD, true);
		}
		
		private final int value;
		
		public boolean isInstanceable() {
			return INSTANCEABLE_MAP.getOrDefault(this, false);
		}
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum ItemTag implements ValueEnum {
		HARVESTER(0),
		POACHED(1),
		TRAININGS_WEAPON(2),
		SHIELD_NOT_AVAILABLE_IN_THE_SHIELD_PAINTING(3);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum ItemUiSound implements ValueEnum {
		UI_CONSUME_APPLE(0),
		UI_CONSUME_CAKE(1),
		UI_CONSUME_DRINK(2),
		UI_INV_ITEM_ARMOR_CHAINMAIL(3),
		UI_INV_ITEM_ARMOR_GAMBESON(4),
		UI_INV_ITEM_ARMOR_GAUNTLETS(5),
		UI_INV_ITEM_ARMOR_HELMET(6),
		UI_INV_ITEM_ARMOR_LEATHER(7),
		UI_INV_ITEM_ARMOR_PLATE(8),
		UI_INV_ITEM_CAP(9),
		UI_INV_ITEM_CLOTHES(10),
		UI_INV_ITEM_HORSESHOE(11),
		UI_INV_ITEM_JEWEL(12),
		UI_INV_ITEM_SPURS(13),
		UI_INV_ITEM_WEAPON_ARROWS(14),
		UI_INV_ITEM_WEAPON_AXE(15),
		UI_INV_ITEM_WEAPON_BOW(16),
		UI_INV_ITEM_WEAPON_BULLETS(17),
		UI_INV_ITEM_WEAPON_DAGGER(18),
		UI_INV_ITEM_WEAPON_HALBERD(19),
		UI_INV_ITEM_WEAPON_MACE(20),
		UI_INV_ITEM_WEAPON_SHIELD(21),
		UI_INV_ITEM_WEAPON_SWORD(22),
		UI_INV_ITEM_WEAPON_TORCH(23);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum KeySubtype implements ValueEnum {
		NONE(1);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum KeyType implements ValueEnum {
		DOOR(1),
		STASH(2),
		DYNAMIC(3);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum Language {
		NOT_VALID("Not valid", "unavailable", "n/a", 0),
		CHINESE_SIMPLIFIED("Chinese (Simplified)", "简体中文", "zh-CN", 1),
		CHINESE_TRADITIONAL("Chinese (Traditional)", "繁體中文", "zh-TW", 2),
		CZECH("Czech", "Čeština", "cs", 3),
		ENGLISH("English", "English", "en", 4),
		FRENCH("French", "Français", "fr", 5),
		GERMAN("German", "Deutsch", "de", 6),
		ITALIAN("Italian", "Italiano", "it", 7),
		JAPANESE("Japanese", "日本語", "ja", 8),
		KOREAN("Korean", "한국어", "ko", 9),
		POLISH("Polish", "Polski", "pl", 10),
		PORTUGUESE("Portuguese", "Português", "pt", 11),
		RUSSIAN("Russian", "Русский", "ru", 12),
		SPANISH("Spanish", "Español", "es", 13),
		TURKISH("Turkish", "Türkçe", "tr", 14),
		UKRAINIAN("Ukrainian", "Українська", "uk", 15),
		// Custom extras beyond the game (Included in base ModForge for some reason ... so left it here too):
		HUNGARIAN("Hungarian", "Magyar", "hu", 16),
		SLOVAK("Slovak", "Slovenčina", "sk", 17);
		
		/**
		 * English name (e.g., "German")
		 */
		private final String name;
		/**
		 * Real/native name (e.g., "Deutsch")
		 */
		private final String displayName;
		/**
		 * iso code ...
		 */
		private final String isoCode;
		/**
		 * code it's saved by in database
		 */
		private final int value;
		
		/**
		 * Get Language enum from English name (e.g., "English" -> ENGLISH)
		 * This is how files get stored in the disc
		 */
		public static Language fromName(String name) {
			try {
				return Language.valueOf(name);
			} catch (IllegalArgumentException ignored) {
				return Arrays.stream(values()).filter(lang -> lang.name.equals(name)).findFirst().orElse(null);
			}
		}
		
		/**
		 * Get Language enum from display name (e.g., "Deutsch" -> GERMAN)
		 * This is how languages are shown to the user
		 */
		public static Language fromDisplayName(String displayName) {
			return Arrays.stream(values()).filter(lang -> lang.displayName.equals(displayName)).findFirst().orElse(null);
		}
		
		/**
		 * Get Language enum from ISO code (e.g., "en" -> ENGLISH)
		 * This is just for saving
		 */
		public static Language fromIsoCode(String isoCode) {
			return Arrays.stream(values()).filter(lang -> lang.isoCode.equalsIgnoreCase(isoCode)).findFirst().orElse(null);
		}
		
		/**
		 * For frontend
		 */
		public static String[] getAllLang() {
			return Arrays.stream(values()).filter(l -> ! l.equals(Language.NOT_VALID)).map(Language::getDisplayName).collect(Collectors.toSet()).toArray(new String[] {});
		}
	}
	
	@Getter
	@Slf4j
	@RequiredArgsConstructor
	public enum MathOperation {
		ADD("+", 0, "AddAbsolute"),
		SUBTRACT("-", 1, "SubtractAbsolute"),
		SET("=", 2, "SetAbsolute"),
		MULTIPLY("*", 3, "AddRelativeToBase"),
		DIVIDE("%", 4, "MultiplyCurrent"),
		MINIMUM("<", 5, "Minimum"),
		MAXIMUM(">", 6, "Maximum"),
		NEGATE("!", 7, "NegateRelativeToValue");
		
		private final String symbol;
		private final int value;
		private final String name;
		
		public static MathOperation fromSymbol(String s) {
			for (var op : values()) {
				if (op.symbol.equals(s))
					return op;
			}
			throw new IllegalArgumentException("Unknown operation symbol: " + s);
		}
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum MiscSubtype implements ValueEnum {
		TRASH_METAL(0),
		CLEAN_CARRYABLE_ITEM(1),
		LOOT(2),
		SPICE(3),
		OFFICE_SUPPLIES(4);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum MiscType implements ValueEnum {
		SKIN(1),
		EAR(2),
		CARRYABLE_ITEM(3),
		USABLE(4),
		COMMON(5),
		BROKEN_AMMO(6);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum NpcToolSubtype implements ValueEnum {
		HAMMER(0),
		SPAWNED(1),
		POCKETED(2);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum OintmentItemSubtype implements ValueEnum {
		WEAPON(0),
		ARMOR(1),
		CLOTHES(2),
		SHOES(3),
		BOW(4),
		PERFUME_SHORT_STRONG(5),
		PERFUME_LONG_WEAK(6),
		GUN(7);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum OintmentItemType implements ValueEnum {
		BANDAGE(0),
		REPAIRKIT(1),
		PERFUME(2),
		SOAP(3);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum SkillSelector implements ValueEnum {
		STEALTH(0),
		HORSE_RIDING(1),
		FENCING(2),
		THIEVERY(4),
		ALCHEMY(6),
		CRAFTSMANSHIP(8),
		DRINKING(13),
		SURVIVAL(14),
		DEFENSE(15),
		WEAPON_SWORD(16),
		WEAPON_HEAVY(17),
		WEAPON_RANGED(19),
		WEAPON_SHIELD(20),
		WEAPON_POLEARM(23),
		WEAPON_UNARMED(24),
		SCHOLARSHIP(26),
		HOUNDMASTER(34);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum StatSelector implements ValueEnum {
		STRENGTH(0),
		AGILITY(1),
		VITALITY(2),
		SPEECH(3),
		PRESTIGE(9);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum Visibility implements ValueEnum {
		SYSTEM_HIDDEN(0),
		GAMEPLAY_HIDDEN(1),
		VISIBLE(2),
		OBSOLETE(3);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum WeaponClass implements ValueEnum {
		UNDEFINED(- 1),
		DAGGER(0),
		SWORD(1),
		SABRE(2),
		AXE(3),
		LONGSWORD(4),
		MACE(5),
		FLAIL(6),
		HALBERD(7),
		SHIELD(8),
		BOW(9),
		CROSSBOW_LIGHT(10),
		TORCH(11),
		UNARMED(12),
		RIFLE(13),
		CROSSBOW_MEDIUM(14),
		CROSSBOW_HEAVY(15),
		HUNTING_SWORD(16),
		SHIELD_BROKEN(17);
		
		private final int value;
	}
	
	@Getter
	@RequiredArgsConstructor
	public enum WeaponSubClass implements ValueEnum {
		UNDEFINED(- 1),
		AXE_FOR_STABBING(0),
		MACE_STAB_BLUNT(1),
		MACE_FOR_STABBING(2),
		POCKETED(4),
		TRAINING(5),
		STUB(6),
		SHIELD_KITE(7),
		SHIELD_PAVISE(8),
		SHIELD_CUMAN(9),
		SHIELD_HEATER(10);
		
		private final int value;
	}
	
	/**
	 * Interface (marker + contract)
	 */
	public interface ValueEnum {
		int getValue();
	}
	
	@UtilityClass
	public class Enums {
		private static final Map<Class<?>, Map<Integer, Enum<?>>> CACHE = new ConcurrentHashMap<>();
		
		/**
		 * Type-safe version — use this when you have the concrete enum class available at compile time.
		 * e.g. Enums.fromValue(WeaponSubClass.class, 4)
		 */
		public <E extends Enum<E> & ValueEnum> E fromValueV(Class<E> enumClass, int value) {
			return fromValue(enumClass, value);
		}
		
		/**
		 * Raw version — use this when the class is only known at runtime (e.g. from a variable).
		 * e.g. Enums.fromValueRaw(someClass, 4)
		 */
		public Enum<?> fromValueRaw(Class<?> enumClass, int value) {
			if (!enumClass.isEnum()) {
				throw new IllegalArgumentException(enumClass + " is not an enum class");
			}
			if (!ValueEnum.class.isAssignableFrom(enumClass)) {
				throw new IllegalArgumentException(enumClass + " does not implement ValueEnum");
			}
			return fromValue(enumClass.asSubclass(Enum.class), value);
		}
		private <E extends Enum<E>> E fromValue(Class<E> enumClass, int value) {
			Map<Integer, Enum<?>> lookup = CACHE.computeIfAbsent(enumClass, Enums::buildLookup);
			Enum<?> result = lookup.get(value);
			if (result == null) {
				throw new IllegalArgumentException(
						"No enum constant with value " + value + " in " + enumClass.getSimpleName()
				);
			}
			return enumClass.cast(result);
		}
		
		@SuppressWarnings("unchecked")
		private Map<Integer, Enum<?>> buildLookup(Class<?> enumClass) {
			Map<Integer, Enum<?>> map = new HashMap<>();
			for (Enum<?> e : ((Class<? extends Enum<?>>) enumClass).getEnumConstants()) {
				int val = ((ValueEnum) e).getValue();
				map.put(val, e);
			}
			return map;
		}
	}
}