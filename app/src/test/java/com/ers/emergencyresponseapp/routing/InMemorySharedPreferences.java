package com.ers.emergencyresponseapp.routing;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A JVM-safe SharedPreferences implementation for persistence unit tests. */
final class InMemorySharedPreferences implements SharedPreferences {
    private static final Object REMOVED = new Object();

    private final Map<String, Object> values = new HashMap<>();
    private final Set<OnSharedPreferenceChangeListener> listeners =
            Collections.newSetFromMap(new IdentityHashMap<>());

    @Override
    public synchronized Map<String, ?> getAll() {
        Map<String, Object> snapshot = new HashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            snapshot.put(entry.getKey(), copyIfSet(entry.getValue()));
        }
        return snapshot;
    }

    @Override
    public synchronized String getString(String key, String defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : (String) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized Set<String> getStringSet(String key, Set<String> defaultValues) {
        Object value = values.get(key);
        if (value == null) {
            return defaultValues == null ? null : new HashSet<>(defaultValues);
        }
        return new HashSet<>((Set<String>) value);
    }

    @Override
    public synchronized int getInt(String key, int defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : (Integer) value;
    }

    @Override
    public synchronized long getLong(String key, long defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : (Long) value;
    }

    @Override
    public synchronized float getFloat(String key, float defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : (Float) value;
    }

    @Override
    public synchronized boolean getBoolean(String key, boolean defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : (Boolean) value;
    }

    @Override
    public synchronized boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new MemoryEditor();
    }

    @Override
    public synchronized void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener
    ) {
        listeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public synchronized void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener
    ) {
        listeners.remove(listener);
    }

    private static Object copyIfSet(Object value) {
        if (value instanceof Set<?>) {
            return new HashSet<>((Set<?>) value);
        }
        return value;
    }

    private void commitChanges(boolean clearFirst, Map<String, Object> updates) {
        List<String> changedKeys = new ArrayList<>();
        List<OnSharedPreferenceChangeListener> listenerSnapshot;

        synchronized (this) {
            if (clearFirst) {
                changedKeys.addAll(values.keySet());
                values.clear();
            }

            for (Map.Entry<String, Object> update : updates.entrySet()) {
                String key = update.getKey();
                Object value = update.getValue();
                if (value == REMOVED) {
                    if (values.remove(key) != null && !changedKeys.contains(key)) {
                        changedKeys.add(key);
                    }
                } else if (!Objects.equals(values.get(key), value)) {
                    values.put(key, copyIfSet(value));
                    if (!changedKeys.contains(key)) {
                        changedKeys.add(key);
                    }
                }
            }

            listenerSnapshot = new ArrayList<>(listeners);
        }

        for (String key : changedKeys) {
            for (OnSharedPreferenceChangeListener listener : listenerSnapshot) {
                listener.onSharedPreferenceChanged(this, key);
            }
        }
    }

    private final class MemoryEditor implements Editor {
        private final Map<String, Object> updates = new LinkedHashMap<>();
        private boolean clearFirst;

        @Override
        public Editor putString(String key, String value) {
            updates.put(Objects.requireNonNull(key), value == null ? REMOVED : value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> value) {
            updates.put(
                    Objects.requireNonNull(key),
                    value == null ? REMOVED : new HashSet<>(value)
            );
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            updates.put(Objects.requireNonNull(key), value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            updates.put(Objects.requireNonNull(key), value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            updates.put(Objects.requireNonNull(key), value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            updates.put(Objects.requireNonNull(key), value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            updates.put(Objects.requireNonNull(key), REMOVED);
            return this;
        }

        @Override
        public Editor clear() {
            clearFirst = true;
            return this;
        }

        @Override
        public boolean commit() {
            commitChanges(clearFirst, new LinkedHashMap<>(updates));
            return true;
        }

        @Override
        public void apply() {
            commit();
        }
    }
}
