package skelterjohn.mixedmeter

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val SEQUENCE_ITEMS_KEY = stringPreferencesKey("sequence_items")

sealed class SequenceItem {
    data class PlainBpm(
        val bpm: Float,
        val selectedNote: String,
    ) : SequenceItem()

    data class MeterPattern(
        val bpm: Float,
        val selectedNote: String,
        val timeSignatures: List<TimeSignature>,
    ) : SequenceItem()
}

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

suspend fun Context.removeSequenceItemAt(index: Int) {
    dataStore.edit { preferences ->
        val current = decodeSequenceItems(preferences[SEQUENCE_ITEMS_KEY] ?: "").toMutableList()
        if (index !in current.indices) return@edit
        current.removeAt(index)
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
            item.bpm.toString(),
            item.selectedNote,
        ).joinToString("|")

        is SequenceItem.MeterPattern -> listOf(
            "m",
            item.bpm.toString(),
            item.selectedNote,
            item.timeSignatures.joinToString(",") { "${it.numerator}/${it.denominator}" },
        ).joinToString("|")
    }
}

private fun decodeSequenceItem(line: String): SequenceItem? {
    val parts = line.split("|")
    return when (parts.firstOrNull()) {
        "b" -> {
            if (parts.size < 3) return null
            SequenceItem.PlainBpm(
                bpm = parts[1].toFloatOrNull() ?: return null,
                selectedNote = parts[2],
            )
        }

        "m" -> {
            if (parts.size < 4) return null
            val timeSignatures = parts[3].split(",")
                .filter { it.contains("/") }
                .mapNotNull { segment ->
                    val sigParts = segment.split("/")
                    if (sigParts.size != 2) return@mapNotNull null
                    val numerator = sigParts[0].toIntOrNull() ?: return@mapNotNull null
                    val denominator = sigParts[1].toIntOrNull() ?: return@mapNotNull null
                    TimeSignature(numerator, denominator)
                }
            if (timeSignatures.isEmpty()) return null
            SequenceItem.MeterPattern(
                bpm = parts[1].toFloatOrNull() ?: return null,
                selectedNote = parts[2],
                timeSignatures = timeSignatures,
            )
        }

        else -> null
    }
}
