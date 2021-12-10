package com.sap.connector.server.framework.service.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AutoFIFOConcurrentMap<K,V> extends ConcurrentHashMap<K,V> {
    transient List<K> keyList;
    int maxSerialSize;

    public AutoFIFOConcurrentMap() {
    }

    public AutoFIFOConcurrentMap(int maxSerialSize) {
        super();
        this.maxSerialSize = maxSerialSize;
        keyList = new CopyOnWriteArrayList<>();
    }

    public AutoFIFOConcurrentMap(int initialCapacity,int maxSerialSize) {
        super(initialCapacity);
        this.maxSerialSize = maxSerialSize;
        keyList = new ArrayList<>();
    }

    public AutoFIFOConcurrentMap(Map<? extends K, ? extends V> m) {
        super(m);
    }

    public AutoFIFOConcurrentMap(int initialCapacity, float loadFactor, int maxSerialSize) {
        super(initialCapacity, loadFactor);
        this.maxSerialSize = maxSerialSize;
        keyList = new ArrayList<>();
    }

    public AutoFIFOConcurrentMap(int initialCapacity, float loadFactor, int concurrencyLevel, int maxSerialSize) {
        super(initialCapacity, loadFactor, concurrencyLevel);
        this.maxSerialSize = maxSerialSize;
        keyList = new ArrayList<>();
    }

    @Override
    public V put(K key, V value) {
        if(maxSerialSize > 0) {

            if(super.containsKey(key)) {
                keyList.remove(key);
            }

            keyList.add(key);

            while (keyList.size() > maxSerialSize) {
                remove(keyList.remove(0));
            }
        }
        return super.put(key, value);
    }

    @Override
    public V remove(Object key) {
        if(keyList!=null) {
            keyList.remove(key);
        }
        return super.remove(key);
    }
}
