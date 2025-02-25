package com.sschr15.scripting

import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.path.*

class SerializableBackedMutableMap<K, V>(
    private val map: MutableMap<K, V>
) : MutableMap<K, V> by map {
    constructor(path: Path?) : this(mutableMapOf()) {
        if (path == null || path.notExists() || !path.isRegularFile()) return

        ObjectInputStream(GZIPInputStream(path.inputStream())).use { ois ->
            @Suppress("UNCHECKED_CAST")
            map.putAll(ois.readObject() as Map<K, V>)
        }
    }

    override fun toString() = map.toString()
    override fun hashCode() = map.hashCode()
    override fun equals(other: Any?) = map == other

    fun save(path: Path) {
        path.deleteIfExists()
        ObjectOutputStream(GZIPOutputStream(path.outputStream())).use { oos ->
            oos.writeObject(map)
        }
    }
}
