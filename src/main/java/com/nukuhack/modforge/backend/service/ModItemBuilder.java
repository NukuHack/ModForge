package com.nukuhack.modforge.backend.service;

import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ItemEntry;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.Attribute;
import com.nukuhack.modforge.backend.model.Attributes;
import com.nukuhack.modforge.backend.model.I;
import com.nukuhack.modforge.backend.model.I.Storm;
import com.nukuhack.modforge.backend.model.ModItem;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ModItemBuilder {
	
	static final Map<String, BuildHandler> HANDLER_MAP = new HashMap<>();
	static final Map<Class<?>, CreateHandler> MAKER_MAP = new HashMap<>();
	static final FallbackBuilder fallbackBuilder = new FallbackBuilder();
	
	static {
		
		for (var spec : ItemEntry.values()) {
			var name = spec.xmlObjName;
			
			if (name.equals(spec.parentName)) {
				
				var fileH = new FileBuilder<>(spec.clazz, spec.idKey);
				HANDLER_MAP.put(name, fileH);
				MAKER_MAP.put(spec.clazz, fileH);
				
			} else if (spec.isTree) {
				
				var treeH = new TreeBuilder<>(spec.clazz, spec.idKey);
				HANDLER_MAP.put(name, treeH);
				MAKER_MAP.put(spec.clazz, treeH);
				
			} else {
				
				var handler = new GeneralBuilder<>(spec.clazz, spec.idKey);
				HANDLER_MAP.put(name, handler);
				MAKER_MAP.put(spec.clazz, handler);
			}
		}
	}
	
	public static <I extends ModItem> I create(final Element el, final I item) {
		item.setAttribute(getAttributeFromElement(el));
		return item;
	}
	
	static List<Attribute> getAttributeFromElement(Element el) {
		var xmlAttrs = el.getAttributes();
		var list = new ArrayList<Attribute>(xmlAttrs.getLength());
		for (int i = 0; i < xmlAttrs.getLength(); i++) {
			var a = (org.w3c.dom.Attr) xmlAttrs.item(i);
			list.add(Attributes.create(a.getName(), a.getValue()));
		}
		return list;
	}
	
	public static ModItem create(final Element element) {
		var name = element.getTagName();
		var handler = HANDLER_MAP.getOrDefault(name, fallbackBuilder);
		return handler.handle(element);
	}
	
	public static Optional<Element> create(final Document document, final ModItem item) {
		var maker = MAKER_MAP.getOrDefault(item.getClass(), fallbackBuilder);
		
		return Optional.ofNullable(maker.handle(document, item));
	}
	
	/**
	 * Deep-copy a mod item, changing its path.
	 */
	private static ModItem deepCopy(ModItem src, String newPath) {
		try {
			var copy = src.getClass().getDeclaredConstructor().newInstance();
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
		var path = src.getPath();
		var colon = path.indexOf(':') + 1;
		
		final String prefix;
		
		final String innerPath;
		
		if (colon != 0 && colon < path.length()) {
			prefix = path.substring(0, colon);
			
			innerPath = path.substring(colon);
		} else {
			prefix = "";
			innerPath = path;
		}
		
		var fullPath = Path.of(innerPath);
		var name = fullPath.getFileName().toString();
		var parent = fullPath.getParent();
		var xmlFile = Util.modXmlFile(name, mod.getId());
		var fullFinal = prefix + (parent != null ? Util.join(parent.toString(), xmlFile) : xmlFile);
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
	 * Generic build handler: recognizes elements whose name matches the predefined ones
	 */
	@Slf4j
	protected static class GeneralBuilder<M extends ModItem> implements BuildHandler, CreateHandler {
		@NonNull
		protected final Class<M> type;
		@NonNull
		protected final String idKey;
		protected final Constructor<M> cons;
		
		protected GeneralBuilder(@NonNull Class<M> type, @NonNull String idKey) {
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
				M item = cons.newInstance();
				
				var idValue = element.getAttribute(idKey);
				item.setId(idValue);
				
				return ModItemBuilder.create(element, item);
			} catch (Exception e) {
				log.warn("Handler failed for {}", type.getSimpleName(), e);
				return null;
			}
		}
		
		@Override
		public Element handle(final Document document, final ModItem item) {
			var typeName = group(item).xmlObjName;
			var el = document.createElement(typeName);
			for (var attr : item.getAttributes()) {
				el.setAttribute(attr.getName(), attr.serialize());
			}
			return el;
		}
	}
	/**
	 * Fallbakc : pretty obvious
	 */
	@Slf4j
	protected static class FallbackBuilder implements BuildHandler, CreateHandler {
		@Override
		public ModItem handle(Element element) {
			var elementName = element.getTagName();
			
			if (true)
				return null;
			
			var attributes = element.getAttributes();
			var map = new HashMap<String, String>();
			for (int i = 0; i < attributes.getLength(); i++) {
				var a = (org.w3c.dom.Attr) attributes.item(i);
				map.put(a.getName(), a.getValue());
			}
			
			log.trace("No creater matched element data {}", map);
			var item = new I.EmptyImpl();
			String id = null;
			String name = null;
			for (var key : map.keySet()) {
				if (id == null && key.toLowerCase().endsWith("id"))
					id = key;
				if (name == null && key.equalsIgnoreCase("name"))
					name = key;
			}
			var idKey = id != null ? id : name != null ? name : "";
			var idValue = element.getAttribute(idKey);
			item.setIdKey(idKey);
			item.setId(idValue);
			log.debug("set unknown item's id to be key={}, val={}", idKey, idValue);
			
			for (var ent : map.entrySet())
				item.addAttribute(Attributes.create(ent.getKey(), ent.getValue()));
			
			return item;
		}
		
		@Override
		public Element handle(Document document, ModItem item) {
			
			log.info("No builder matched item <{}>", item);
			
			return null;
		}
	}
	
	/**
	 * File builder for times when the entire file is a single object
	 */
	@Slf4j
	protected static class FileBuilder<M extends ModItem> extends GeneralBuilder<M> {
		protected FileBuilder(@NonNull Class<M> type, @NonNull String idKey) {
			super(type, idKey);
		}
		
		@Override
		public ModItem handle(final Element element) {
			try {
				if (cons == null)
					return fallbackBuilder.handle(element);
				var item = cons.newInstance();
				item.setId("storm_" + Util.randomString(32));
				
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
			} catch (Exception e) {
				log.warn("Creating Storm xml failed for {}: {}", item, e.getMessage());
				return null;
			}
		}
	}
}