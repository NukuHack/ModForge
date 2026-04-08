package modforge.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import modforge.Util;
import modforge.backend.ItemEntry;
import modforge.backend.ItemType;
import modforge.backend.ModData;
import modforge.backend.model.Attribute;
import modforge.backend.model.Attributes;
import modforge.backend.model.I.Storm;
import modforge.backend.model.ModItem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.file.Path;
import java.util.*;

@Slf4j
public final class ModItemBuilder {
	
	// O(1) lookup map - element name to handler
	static final Set<String> FILE_PARSERS = new HashSet<>();
	static final Map<String, BuildHandler> HANDLER_MAP = new HashMap<>();
	static final Map<Class<? extends ModItem>, CreateHandler> MAKER_MAP = new HashMap<>();
	
	static {
		FILE_PARSERS.add("storm");
		// Build the handler map once at class initialization
		for (var spec : ItemType.getHandlerSpecs()) {
			if (! FILE_PARSERS.contains(spec.getName())) {
				HANDLER_MAP.put(spec.getName(), new GeneralBuilder<>(spec.getClazz(), spec.getIdKey()));
				MAKER_MAP.put(spec.getClazz(), new GeneralCreater<>(spec.getClazz(), spec.getIdKey()));
			} else {
				if (spec.getName().equals("storm")) {
					HANDLER_MAP.put(spec.getName(), new StormBuilder((Class<Storm>) spec.getClazz(), spec.getIdKey()));
					MAKER_MAP.put(spec.getClazz(), new StormCreater((Class<Storm>) spec.getClazz(), spec.getIdKey()));
				} else if (true) {
					// do future stuff here
				}
			}
		}
	}
	
	private ModItemBuilder() {
	}
	
	public static ModItem create(final Element el, final ModItem item) {
		final var xmlAttrs = el.getAttributes();
		final var list = new ArrayList<Attribute>(xmlAttrs.getLength());
		for (int i = 0; i < xmlAttrs.getLength(); i++) {
			final var a = (org.w3c.dom.Attr) xmlAttrs.item(i);
			list.add(Attributes.create(a.getLocalName(), a.getValue()));
		}
		item.setAttribute(list);
		return item;
	}
	
	public static ModItem create(final Element element) {
		Attributes.traverseElement(element);
		// glue it together yet again, barely works
		final var elementName = element.getLocalName().toLowerCase(Locale.ROOT);
		final var handler = HANDLER_MAP.get(elementName);
		
		if (handler != null) {
			return handler.create(element);
		}
		
		log.info("No creater matched element <{}>", elementName);
		if (log.isDebugEnabled()) {
			final var attributes = element.getAttributes();
			final Map<String, String> map = new HashMap<>();
			
			for (int i = 0; i < attributes.getLength(); i++) {
				var attr = attributes.item(i);
				map.put(attr.getNodeName(), attr.getNodeValue());
			}
			log.debug("No creater matched element data {}", map);
		}
		return null;
	}
	
	public static Element build(final Document document, final ModItem item) {
		// getting the correct one from HANDLER_MAP
		final var maker = MAKER_MAP.get(item.getClass());
		
		if (maker != null) {
			return maker.build(document, item);
		}
		
		log.info("No builder matched item <{}>", item);
		return null;
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
	
	public static String groupName(ModItem item) {
		return ItemEntry.forClass(item.getClass()).parentName();
	}
	
	protected interface BuildHandler {
		ModItem create(final Element element);
	}
	
	protected interface CreateHandler {
		Element build(final Document document, final ModItem item);
	}
	
	/**
	 * Generic build handler: recognizes elements whose local name matches the
	 * simple class name (case-insensitive) and populates a configurable ID attribute.
	 */
	@Slf4j
	@RequiredArgsConstructor
	protected static final class GeneralBuilder<M extends ModItem> implements BuildHandler {
		private final Class<M> type;
		private final String IdKey;
		
		@Override
		public ModItem create(final Element element) {
			try {
				final M item = type.getDeclaredConstructor().newInstance();
				
				final String idValue = element.getAttribute(IdKey);
				item.setId(idValue.isBlank() ? null : idValue);
				
				return ModItemBuilder.create(element, item);
			} catch (final Exception e) {
				log.warn("Handler failed for {}: {}", type.getSimpleName(), e.getMessage());
				return null;
			}
		}
	}
	
	@Slf4j
	@RequiredArgsConstructor
	protected static final class GeneralCreater<M extends ModItem> implements CreateHandler {
		private final Class<M> type;
		private final String IdKey;
		
		@Override
		public Element build(final Document document, final ModItem item) {
			final var typeName = ItemEntry.forClass(item.getClass()).fileName;
			final var el = document.createElement(typeName);
			for (Attribute<?> attr : item.getAttributes()) {
				el.setAttribute(attr.getName(), Attributes.serializeValue(attr));
			}
			return el;
		}
	}
	
	@Slf4j
	@RequiredArgsConstructor
	protected static final class StormBuilder implements BuildHandler {
		private final Class<Storm> type;
		private final String IdKey;
		
		@Override
		public ModItem create(final Element element) {
			try {
				final Storm item = type.getDeclaredConstructor().newInstance();
				
				item.setId("storm_" + Util.getRandomString(32));
				
				var data = StormService.parse(element);
				item.setStormData(data);
				
				return item;
			} catch (final Exception e) {
				log.warn("Handler failed for {}: {}", type.getSimpleName(), e.getMessage());
				return null;
			}
		}
	}
	
	@Slf4j
	@RequiredArgsConstructor
	protected static final class StormCreater implements CreateHandler {
		private final Class<Storm> type;
		private final String IdKey;
		
		@Override
		public Element build(final Document document, final ModItem item) {
			try {
				return StormService.serialize(((Storm) item).getStormData(), document);
			} catch (final Exception e) {
				log.warn("Creating Storm xml failed for {}: {}", item, e.getMessage());
				return null;
			}
		}
	}
}