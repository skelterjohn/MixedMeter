package skelterjohn.mixedmeter

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val SEQUENCE_ITEMS_KEY = stringPreferencesKey("sequence_items")

sealed class SequenceItem {
    abstract val id: String

    data class PlainBpm(
        override val id: String = newSequenceItemId(),
        val bpm: Float,
        val selectedNote: String,
    ) : SequenceItem()

    data class MeterPattern(
        override val id: String = newSequenceItemId(),
        val bpm: Float,
        val selectedNote: String,
        val timeSignatures: List<TimeSignature>,
    ) : SequenceItem()
}

fun newSequenceItemId(): String = UUID.randomUUID().toString()

fun metronomeSnapshot(
    bpm: Float,
    selectedNote: String,
    timeSignatures: List<TimeSignature>,
): SequenceItem {
    return if (timeSignatures.isEmpty()) {
        SequenceItem.PlainBpm(bpm = bpm, selectedNote = selectedNote)
    } else {
        SequenceItem.MeterPattern(
            bpm = bpm,
            selectedNote = selectedNote,
            timeSignatures = timeSignatures,
        )
    }
}

fun Context.sequenceItemsFlow(): Flow<List<SequenceItem>> {
    return dataStore.data.map { preferences ->
        decodeSequenceItems(preferences[SEQUENCE_ITEMS_KEY] ?: "")
    }
}

suspend fun Context.appendSequenceItem(item: SequenceItem) {
    dataStore.edit { preferences ->
        val current = decodeSequenceItems(preferences[SEQUENCE_ITEMS_KEY] ?: "")
        preferences[SEQUENCE_ITEMS_KEY] = encodeSequenceItems(current + item)
    }
}

suspend fun Context.setSequenceItems(items: List<SequenceItem>) {
    dataStore.edit { preferences ->
        preferences[SEQUENCE_ITEMS_KEY] = encodeSequenceItems(items)
    }
}

suspend fun Context.removeSequenceItemAt(index: Int) {
    dataStore.edit { preferences ->
        val current = decodeSequenceItems(preferences[SEQUENCE_ITEMS_KEY] ?: "").toMutableList()
        if (index !in current.indices) return@edit
        current.removeAt(index)
        preferences[SEQUENCE_ITEMS_KEY] = encodeSequenceItems(current)
    }
}

suspend fun Context.removeSequenceItemById(id: String) {
    dataStore.edit { preferences ->
        val current = decodeSequenceItems(preferences[SEQUENCE_ITEMS_KEY] ?: "")
            .filterNot { it.id == id }
        preferences[SEQUENCE_ITEMS_KEY] = encodeSequenceItems(current)
    }
}

private fun encodeSequenceItems(items: List<SequenceItem>): String {
    return items.joinToString("\n") { encodeSequenceItem(it) }
}

private fun decodeSequenceItems(raw: String): List<SequenceItem> {
    if (raw.isBlank()) return emptyList()
    return raw.lineSequence()
        .mapNotNull { line -> decodeSequenceItem(line.trim()) }
        .toList()
}

private fun encodeSequenceItem(item: SequenceItem): String {
    return when (item) {
        is SequenceItem.PlainBpm -> listOf(
            "b",
            item.id,
            item.bpm.toString(),
            item.selectedNote,
        ).joinToString("|")

        is SequenceItem.MeterPattern -> listOf(
            "m",
            item.id,
            item.bpm.toString(),
            item.selectedNote,
            item.timeSignatures.joinToString(",") { "${it.numerator}/${it.denominator}" },
        ).joinToString("|")
    }
}

private fun decodeSequenceItem(line: String): SequenceItem? {
    val parts = line.split("|")
    return when (parts.firstOrNull()) {
        "b" -> decodePlainBpm(parts)
        "m" -> decodeMeterPattern(parts)
        else -> null
    }
}

private fun decodePlainBpm(parts: List<String>): SequenceItem.PlainBpm? {
    return when {
        parts.size >= 4 -> SequenceItem.PlainBpm(
            id = parts[1],
            bpm = parts[2].toFloatOrNull() ?: return null,
            selectedNote = parts[3],
        )

        parts.size >= 3 -> SequenceItem.PlainBpm(
            id = newSequenceItemId(),
            bpm = parts[1].toFloatOrNull() ?: return null,
            selectedNote = parts[2],
        )

        else -> null
    }
}

private fun decodeMeterPattern(parts: List<String>): SequenceItem.MeterPattern? {
    val timeSignaturePartIndex: Int
    val id: String
    val bpm: Float
    val selectedNote: String

    if (parts.size >= 5) {
        id = parts[1]
        bpm = parts[2].toFloatOrNull() ?: return null
        selectedNote = parts[3]
        timeSignaturePartIndex = 4
    } else if (parts.size >= 4) {
        id = newSequenceItemId()
        bpm = parts[1].toFloatOrNull() ?: return null
        selectedNote = parts[2]
        timeSignaturePartIndex = 3
    } else {
        return null
    }

    val timeSignatures = parts[timeSignaturePartIndex].split(",")
        .filter { it.contains("/") }
        .mapNotNull { segment ->
            val sigParts = segment.split("/")
            if (sigParts.size != 2) return@mapNotNull null
            val numerator = sigParts[0].toIntOrNull() ?: return@mapNotNull null
            val denominator = sigParts[1].toIntOrNull() ?: return@mapNotNull null
            TimeSignature(numerator, denominator)
        }
    if (timeSignatures.isEmpty()) return null

    return SequenceItem.MeterPattern(
        id = id,
        bpm = bpm,
        selectedNote = selectedNote,
        timeSignatures = timeSignatures,
    )
}
