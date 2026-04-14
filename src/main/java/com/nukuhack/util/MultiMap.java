package com.nukuhack.util;

import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class MultiMap<K, V> implements Map<K, Set<V>> {
	
	@Delegate
	private final HashMap<K, Set<V>> map;
	
	public MultiMap() {
		this.map = new HashMap<>();
	}
	
	public MultiMap(int initialCapacity) {
		this.map = new HashMap<>(initialCapacity);
	}
	
	public MultiMap(int initialCapacity, float loadFactor) {
		this.map = new HashMap<>(initialCapacity, loadFactor);
	}
	
	/**
	 * Adds a value to the set associated with the key.
	 * Returns true if the value was added (wasn't already present).
	 */
	public boolean putValue(K key, V value) {
		Set<V> values = map.computeIfAbsent(key, k -> new HashSet<>());
		return values.add(value);
	}
	
	/**
	 * Removes a specific value from the key's set.
	 * Returns true if the value was present and removed.
	 */
	public boolean removeValue(K key, V value) {
		Set<V> values = map.get(key);
		if (values == null) {
			return false;
		}
		boolean removed = values.remove(value);
		if (values.isEmpty()) {
			map.remove(key);
		}
		return removed;
	}
	
	/**
	 * Gets all values associated with the key.
	 * Returns an empty set if key not present.
	 */
	public Set<V> getValues(K key) {
		Set<V> values = map.get(key);
		return values != null ? Collections.unmodifiableSet(values) : Collections.emptySet();
	}
	
	/**
	 * Checks if a specific value exists for the given key.
	 */
	public boolean containsValue(K key, V value) {
		Set<V> values = map.get(key);
		return values != null && values.contains(value);
	}
	
	public int fullSize() {
		return map.values().stream().mapToInt(Set::size).sum();
	}
	
	public Set<V> getAllValues() {
		Set<V> allValues = new HashSet<>();
		for (var values : map.values()) {
			allValues.addAll(values);
		}
		return Collections.unmodifiableSet(allValues);
	}
	
	@Override
	public String toString() {
		return map.toString();
	}
}