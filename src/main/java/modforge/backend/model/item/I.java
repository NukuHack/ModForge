package modforge.backend.model.item;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import modforge.backend.model.ModItem.BaseModItem;
import modforge.backend.model.storm.StormData;

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
	 * {@code ModItem} representation of a Storm script entry.
	 *
	 * <p>Previously this was a flat item with a {@code HashMap}, which broke as soon
	 * as selectors/operations needed to be nested more than one level deep. Now it
	 * carries a fully-parsed {@link modforge.backend.model.storm.StormData} object that supports arbitrary tree
	 * depth for both selectors and operations.</p>
	 *
	 * <p>The base-class {@code id} / {@code attributes} fields are still populated
	 * by {@link modforge.backend.service.ModItemBuilder} for the normal item-list
	 * display pipeline (search, filter, etc.).  The rich {@link modforge.backend.model.storm.StormData} payload is
	 * populated separately by {@link modforge.backend.service.StormService} after the
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
