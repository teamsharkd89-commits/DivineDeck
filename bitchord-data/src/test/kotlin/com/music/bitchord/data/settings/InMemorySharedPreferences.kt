package com.music.bitchord.data.settings

import android.content.SharedPreferences

class InMemorySharedPreferences(
    val backingMap: MutableMap<String, Any?> = mutableMapOf()
) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = backingMap
    override fun getString(key: String?, defValue: String?): String? = (backingMap[key] as? String) ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (backingMap[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = (backingMap[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (backingMap[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (backingMap[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (backingMap[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = backingMap.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor(backingMap)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class Editor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) pending[key] = values
            return this
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) { removals.add(key); pending.remove(key) }
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            clearAll = true; pending.clear(); removals.clear()
            return this
        }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clearAll) map.clear()
            removals.forEach { map.remove(it) }
            pending.forEach { (k, v) -> if (v != null) map[k] = v else map.remove(k) }
        }
    }
}
