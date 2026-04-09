package com.nukuhack.util;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MultiMap<K, V> implements Map<K, Set<V>> {
    
    private final Map<K, Set<V>> map;
    
    public MultiMap() {
        this.map = new HashMap<>();
    }
    
    public MultiMap(int initialCapacity) {
        this.map = new HashMap<>(initialCapacity);
    }
    
    public MultiMap(int initialCapacity, float loadFactor) {
        this.map = new HashMap<>(initialCapacity, loadFactor);
    }
    
    // Core operations
    
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
        if (values != null) {
            boolean removed = values.remove(value);
            if (values.isEmpty()) {
                map.remove(key);
            }
            return removed;
        }
        return false;
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
    
    // Standard Map interface implementation
    
    @Override
    public int size() {
        return map.size();
    }
    
    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }
    
    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }
    
    @Override
    public boolean containsValue(Object value) {
        for (Set<V> values : map.values()) {
            if (values.contains(value)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public Set<V> get(Object key) {
        Set<V> values = map.get(key);
        return values != null ? Collections.unmodifiableSet(values) : null;
    }
    
    @Override
    public Set<V> put(K key, Set<V> value) {
        if (value == null) {
            throw new NullPointerException("Value set cannot be null");
        }
        Set<V> newSet = new HashSet<>(value);
        Set<V> oldSet = map.put(key, newSet);
        return oldSet != null ? Collections.unmodifiableSet(oldSet) : null;
    }
    
    @Override
    public Set<V> remove(Object key) {
        Set<V> removed = map.remove(key);
        return removed != null ? Collections.unmodifiableSet(removed) : null;
    }
    
    @Override
    public void putAll(Map<? extends K, ? extends Set<V>> m) {
        for (Entry<? extends K, ? extends Set<V>> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }
    
    @Override
    public void clear() {
        map.clear();
    }
    
    @Override
    public Set<K> keySet() {
        return Collections.unmodifiableSet(map.keySet());
    }
    
    @Override
    public Collection<Set<V>> values() {
        Collection<Set<V>> values = map.values();
        List<Set<V>> unmodifiableValues = new ArrayList<>(values.size());
        for (Set<V> set : values) {
            unmodifiableValues.add(Collections.unmodifiableSet(set));
        }
        return Collections.unmodifiableCollection(unmodifiableValues);
    }
    
    @Override
    public Set<Entry<K, Set<V>>> entrySet() {
        Set<Entry<K, Set<V>>> entries = new HashSet<>();
        for (Entry<K, Set<V>> entry : map.entrySet()) {
            entries.add(new AbstractMap.SimpleImmutableEntry<>(
                entry.getKey(),
                Collections.unmodifiableSet(entry.getValue())
            ));
        }
        return Collections.unmodifiableSet(entries);
    }
    
    // Useful default methods
    
    @Override
    public Set<V> getOrDefault(Object key, Set<V> defaultValue) {
        Set<V> values = map.get(key);
        return values != null ? Collections.unmodifiableSet(values) : defaultValue;
    }
    
    @Override
    public Set<V> putIfAbsent(K key, Set<V> value) {
        if (value == null) {
            throw new NullPointerException("Value set cannot be null");
        }
        Set<V> newSet = new HashSet<>(value);
        Set<V> existing = map.putIfAbsent(key, newSet);
        return existing != null ? Collections.unmodifiableSet(existing) : null;
    }
    
    @Override
    public Set<V> computeIfAbsent(K key, Function<? super K, ? extends Set<V>> mappingFunction) {
        Set<V> result = map.computeIfAbsent(key, k -> {
            Set<V> newSet = mappingFunction.apply(k);
            return newSet != null ? new HashSet<>(newSet) : null;
        });
        return result != null ? Collections.unmodifiableSet(result) : null;
    }
    
    @Override
    public Set<V> computeIfPresent(K key, BiFunction<? super K, ? super Set<V>, ? extends Set<V>> remappingFunction) {
        Set<V> result = map.computeIfPresent(key, (k, v) -> {
            Set<V> newSet = remappingFunction.apply(k, Collections.unmodifiableSet(v));
            return newSet != null ? new HashSet<>(newSet) : null;
        });
        return result != null ? Collections.unmodifiableSet(result) : null;
    }
    
    // Unsupported complex operations
    
    @Override
    public boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException("Use removeValue(K, V) instead");
    }
    
    @Override
    public boolean replace(K key, Set<V> oldValue, Set<V> newValue) {
        throw new UnsupportedOperationException("Not implemented");
    }
    
    @Override
    public Set<V> replace(K key, Set<V> value) {
        throw new UnsupportedOperationException("Not implemented");
    }
    
    @Override
    public void replaceAll(BiFunction<? super K, ? super Set<V>, ? extends Set<V>> function) {
        throw new UnsupportedOperationException("Not implemented");
    }
    
    @Override
    public Set<V> compute(K key, BiFunction<? super K, ? super Set<V>, ? extends Set<V>> remappingFunction) {
        throw new UnsupportedOperationException("Not implemented");
    }
    
    @Override
    public Set<V> merge(K key, Set<V> value, BiFunction<? super Set<V>, ? super Set<V>, ? extends Set<V>> remappingFunction) {
        throw new UnsupportedOperationException("Not implemented");
    }
    
    // Utility methods
    
    public int totalValueCount() {
        int count = 0;
        for (Set<V> values : map.values()) {
            count += values.size();
        }
        return count;
    }
    
    public Set<V> getAllValues() {
        Set<V> allValues = new HashSet<>();
        for (Set<V> values : map.values()) {
            allValues.addAll(values);
        }
        return Collections.unmodifiableSet(allValues);
    }
    
    @Override
    public String toString() {
        return map.toString();
    }
}