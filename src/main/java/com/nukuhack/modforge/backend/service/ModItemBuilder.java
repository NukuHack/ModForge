package com.nukuhack.modforge.backend.service;

import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ItemEntry;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.Attribute;
import com.nukuhack.modforge.backend.model.Attributes;
import com.nukuhack.modforge.backend.model.I.Storm;
import com.nukuhack.modforge.backend.model.ModItem;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ModItemBuilder {
	
	// O(1) lookup map - element name to handler
	static final Map<String, BuildHandler> HANDLER_MAP = new HashMap<>();
	static final Map<Class<? extends ModItem>, CreateHandler> MAKER_MAP = new HashMap<>();
	static final FallbackBuilder fallbackBuilder = new FallbackBuilder();
	
	static {
		// Build the handler map once at class initialization
		for (var spec : ItemEntry.values()) {
			var name = spec.xmlObjName;
			if (! name.equals(spec.parentName)) {
				var handler = new GeneralBuilder<>(spec.clazz, spec.idKey);
				HANDLER_MAP.put(name, handler);
				MAKER_MAP.put(spec.clazz, handler);
				continue;
			}
			var fileH = new FileBuilder<>(spec.clazz, spec.idKey);
			HANDLER_MAP.put(name, fileH);
			MAKER_MAP.put(spec.clazz, fileH);
		}
	}
	
	public static <I extends ModItem> I create(final Element el, final I item) {
		var xmlAttrs = el.getAttributes();
		final var list = new ArrayList<Attribute>(xmlAttrs.getLength());
		for (int i = 0; i < xmlAttrs.getLength(); i++) {
			final var a = (org.w3c.dom.Attr) xmlAttrs.item(i);
			list.add(Attributes.create(a.getName(), a.getValue()));
		}
		item.setAttribute(list);
		return item;
	}
	
	public static ModItem create(final Element element) {
		// glue it together yet again, barely works
		final var elementName = element.getTagName();
		final var handler = HANDLER_MAP.getOrDefault(elementName, fallbackBuilder);
		
		return handler.handle(element);
	}
	
	public static Optional<Element> handle(final Document document, final ModItem item) {
		// getting the correct one from HANDLER_MAP
		final var maker = MAKER_MAP.get(item.getClass());
		
		// Intentional - No serialization if item is not correct, we don't want incorrect data to be serialized
		if (maker != null)
			return Optional.of(maker.handle(document, item));
		
		log.info("No builder matched item <{}>", item);
		return Optional.empty();
	}
	
	
	/**
	 * Deep-copy a mod item, changing its path.
	 */
	private static ModItem deepCopy(ModItem src, String newPath) {
		try {
			final var copy = src.getClass().getDeclaredConstructor().newInstance();
			copy.setId(src.getId());
			copy.setPath(newPath);
			copy.setAttribute(src.getAttributes().stream().map(Attribute::deepClone).toList());
			return copy;
		} catch (Exception e) {
			throw new RuntimeException("Deep copy failed for " + src.getClass().getSimpleName(), e);
		}
	}
	
	/**
	 * @param src ModItem - the data to be copied itself
	 * @param mod ModData - the mod it's going to be copied To
	 * @return ModItem - the element, it might be == to the base, and it could be .equal() but usually not
	 * since path change is reflected in the data of the item
	 */
	public static ModItem deepCopy(ModItem src, ModData mod) {
		final var path = src.getPath();
		final int colon = path.indexOf(':') + 1;
		
		final String prefix;   // e.g. "SomePak.pak:" or ""
		final String innerPath; // e.g. "inner/dir/entry.xml"
		// check for 0 since we incremented it once for later splitting
		if (colon != 0 && colon < path.length()) {
			prefix = path.substring(0, colon);   // includes ':'
			innerPath = path.substring(colon);
		} else {
			prefix = "";
			innerPath = path;
		}
		
		final var fullPath = Path.of(innerPath);
		final var name = fullPath.getFileName().toString();
		final var parent = fullPath.getParent();
		final var xmlFile = Util.modXmlFile(name, mod.id);
		final var fullFinal = prefix + (parent != null ? Util.join(parent.toString(), xmlFile) : xmlFile);
		return deepCopy(src, fullFinal);
	}
	
	public static ItemEntry group(ModItem item) {
		return ItemEntry.forClass(item.getClass());
	}
	
	protected interface BuildHandler {
		ModItem handle(final Element element);
	}
	
	protected interface CreateHandler {
		Element handle(final Document document, final ModItem item);
	}
	
	/**
	 * Generic build handler: recognizes elements whose local name matches the
	 * simple class name (case-insensitive) and populates a configurable ID attribute.
	 */
	@Slf4j
	protected static class GeneralBuilder<M extends ModItem> implements BuildHandler, CreateHandler {
		protected final Class<M> type;
		protected final String idKey;
		protected final Constructor<M> cons;
		
		protected GeneralBuilder(Class<M> type, String idKey) {
			this.type = type;
			this.idKey = idKey;
			Constructor<M> cons;
			try {
				cons = type.getDeclaredConstructor();
			} catch (NoSuchMethodException e) {
				cons = null;
				log.warn("failed to get constructor for type: {} falling back to Fallback", type.getSimpleName());
			}
			this.cons = cons;
		}
		
		@Override
		public ModItem handle(final Element element) {
			try {
				if (cons == null)
					return fallbackBuilder.handle(element);
				final M item = cons.newInstance();
				
				final var idValue = element.getAttribute(idKey);
				item.setId(idValue);
				
				return ModItemBuilder.create(element, item);
			} catch (final Exception e) {
				log.warn("Handler failed for {}: {}", type.getSimpleName(), e.getMessage());
				return null;
			}
		}
		
		@Override
		public Element handle(final Document document, final ModItem item) {
			final var typeName = group(item).xmlObjName;
			final var el = document.createElement(typeName);
			for (var attr : item.getAttributes()) {
				el.setAttribute(attr.getName(), attr.serialize());
			}
			return el;
		}
	}
	
	protected static class FallbackBuilder implements BuildHandler, CreateHandler {
		@Override
		public ModItem handle(Element element) {
			final var elementName = element.getTagName();
			log.info("No creater matched element <{}>", elementName);
			
			// using if- so the compiler sees it as good code
			if (true)
				return null;
			
			final var attributes = element.getAttributes();
			final Map<String, String> map = new HashMap<>();
			for (int i = 0; i < attributes.getLength(); i++) {
				final var a = (org.w3c.dom.Attr) attributes.item(i);
				map.put(a.getName(), a.getValue());
			}
			
			log.trace("No creater matched element data {}", map);
			final var item = new ModItem.EmptyImpl();
			String id = null;
			String name = null;
			for (var key : map.keySet()) {
				if (id == null && key.toLowerCase().endsWith("id"))
					id = key;
				if (name == null && key.equalsIgnoreCase("name"))
					name = key;
			}
			final var idKey = id != null ? id : name != null ? name : "";
			final var idValue = element.getAttribute(idKey);
			item.setIdKey(idKey);
			item.setId(idValue);
			log.debug("set unknown item's id to be key={}, val={}", idKey, idValue);
			
			for (var ent : map.entrySet())
				item.addAttribute(Attributes.create(ent.getKey(), ent.getValue()));
			
			return item;
		}
		
		@Override
		public Element handle(Document document, ModItem item) {
			return null;
		}
	}
	
	@Slf4j
	protected static class FileBuilder<M extends ModItem> extends GeneralBuilder<M> {
		protected FileBuilder(Class<M> type, String idKey) {
			super(type, idKey);
		}
		
		
		@Override
		public ModItem handle(final Element element) {
			try {
				if (cons == null)
					return fallbackBuilder.handle(element);
				var item = cons.newInstance();
				item.setId("storm_" + Util.getRandomString(32));
				
				if (item instanceof Storm storm) {
					var data = StormService.parse(element);
					storm.setStormData(data);
				}
				
				return item;
			} catch (Exception e) {
				log.warn("Handler failed for {}", type.getSimpleName(), e);
				return null;
			}
		}
		
		@Override
		public Element handle(final Document document, final ModItem item) {
			try {
				if (item instanceof Storm storm) {
					return StormService.serialize(storm.getStormData(), document);
				}
				return fallbackBuilder.handle(document, item);
			} catch (final Exception e) {
				log.warn("Creating Storm xml failed for {}: {}", item, e.getMessage());
				return null;
			}
		}
	}
}