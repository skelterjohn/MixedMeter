package skelterjohn.mixedmeter

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val SEQUENCE_ITEMS_KEY = stringPreferencesKey("sequence_items")
private val SEQUENCE_NAME_KEY = stringPreferencesKey("sequence_name")
private val SAVED_SEQUENCES_KEY = stringPreferencesKey("saved_sequences")
/** @deprecated Migrated into [SAVED_SEQUENCES_KEY] on read. */
private val SAVED_SEQUENCE_ITEMS_KEY = stringPreferencesKey("saved_sequence_items")

private const val SAVED_RECORD_SEP = "\n###\n"

data class SavedSequence(
    val id: String,
    val name: String,
    val items: List<SequenceItem>,
)

sealed class SequenceItem {
    abstract val id: String
    abstract val repeatCount: Int

    data class PlainBpm(
        override val id: String = newSequenceItemId(),
        val bpm: Float,
        val selectedNote: String,
        override val repeatCount: Int = 1,
    ) : SequenceItem()

    data class MeterPattern(
        override val id: String = newSequenceItemId(),
        val bpm: Float,
        val selectedNote: String,
        val timeSignatures: List<TimeSignature>,
        val beatClickActive: List<List<Boolean>> = emptyList(),
        override val repeatCount: Int = 1,
    ) : SequenceItem()
}

fun newSequenceItemId(): String = UUID.randomUUID().toString()

fun metronomeSnapshot(
    bpm: Float,
    selectedNote: String,
    timeSignatures: List<TimeSignature>,
    beatClickActive: List<List<Boolean>> = emptyList(),
): SequenceItem {
    return if (timeSignatures.isEmpty()) {
        SequenceItem.PlainBpm(bpm = bpm, selectedNote = selectedNote)
    } else {
        SequenceItem.MeterPattern(
            bpm = bpm,
            selectedNote = selectedNote,
            timeSignatures = timeSignatures,
            beatClickActive = reconcileBeatClickActive(beatClickActive, timeSignatures),
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

fun Context.sequenceNameFlow(): Flow<String> {
    return dataStore.data.map { preferences ->
        preferences[SEQUENCE_NAME_KEY] ?: ""
    }
}

suspend fun Context.setSequenceName(name: String) {
    dataStore.edit { preferences ->
        preferences[SEQUENCE_NAME_KEY] = name.trim().take(80)
    }
}

fun Context.savedSequencesFlow(): Flow<List<SavedSequence>> {
    return dataStore.data.map { preferences -> decodeAllSavedSequences(preferences) }
}

fun Context.hasSavedSequenceFlow(): Flow<Boolean> {
    return savedSequencesFlow().map { it.isNotEmpty() }
}

suspend fun Context.saveNamedSequence(name: String, items: List<SequenceItem>): Boolean {
    val trimmed = name.trim().take(80)
    if (trimmed.isEmpty()) return false
    dataStore.edit { preferences ->
        val current = decodeAllSavedSequences(preferences).toMutableList()
        val existing = current.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        val id = existing?.id ?: newSequenceItemId()
        current.removeAll { it.id == id }
        current.add(SavedSequence(id = id, name = trimmed, items = items))
        preferences[SAVED_SEQUENCES_KEY] = encodeSavedSequences(current)
        preferences[SEQUENCE_NAME_KEY] = trimmed
        preferences.remove(SAVED_SEQUENCE_ITEMS_KEY)
    }
    return true
}

suspend fun Context.loadNamedSequenceIntoWorkspace(saved: SavedSequence) {
    dataStore.edit { preferences ->
        preferences[SEQUENCE_ITEMS_KEY] = encodeSequenceItems(saved.items)
        preferences[SEQUENCE_NAME_KEY] = saved.name
    }
}

suspend fun Context.deleteSavedSequence(id: String) {
    dataStore.edit { preferences ->
        val updated = decodeAllSavedSequences(preferences).filterNot { it.id == id }
        if (updated.isEmpty()) {
            preferences.remove(SAVED_SEQUENCES_KEY)
            preferences.remove(SAVED_SEQUENCE_ITEMS_KEY)
        } else {
            preferences[SAVED_SEQUENCES_KEY] = encodeSavedSequences(updated)
            preferences.remove(SAVED_SEQUENCE_ITEMS_KEY)
        }
    }
}

private fun decodeAllSavedSequences(preferences: Preferences): List<SavedSequence> {
    val multi = decodeSavedSequences(preferences[SAVED_SEQUENCES_KEY] ?: "")
    if (multi.isNotEmpty()) return multi
    val legacy = decodeSequenceItems(preferences[SAVED_SEQUENCE_ITEMS_KEY] ?: "")
    if (legacy.isEmpty()) return emptyList()
    return listOf(
        SavedSequence(
            id = newSequenceItemId(),
            name = "Saved",
            items = legacy,
        ),
    )
}

private fun encodeSavedSequences(records: List<SavedSequence>): String {
    if (records.isEmpty()) return ""
    return records.joinToString(SAVED_RECORD_SEP) { record ->
        buildString {
            append(record.id)
            append('\n')
            append(record.name)
            append('\n')
            append(encodeSequenceItems(record.items))
        }
    }
}

private fun decodeSavedSequences(raw: String): List<SavedSequence> {
    if (raw.isBlank()) return emptyList()
    return raw.split(SAVED_RECORD_SEP).mapNotNull { block ->
        val trimmed = block.trim()
        if (trimmed.isEmpty()) return@mapNotNull null
        val newline = trimmed.indexOf('\n')
        if (newline < 0) return@mapNotNull null
        val id = trimmed.substring(0, newline).trim()
        val rest = trimmed.substring(newline + 1)
        val nameEnd = rest.indexOf('\n')
        if (nameEnd < 0) return@mapNotNull null
        val name = rest.substring(0, nameEnd).trim()
        val itemsRaw = rest.substring(nameEnd + 1)
        if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
        SavedSequence(id = id, name = name, items = decodeSequenceItems(itemsRaw))
    }
}

suspend fun Context.updateSequenceItemRepeatCount(id: String, repeatCount: Int) {
    val count = repeatCount.coerceAtLeast(1)
    dataStore.edit { preferences ->
        val updated = decodeSequenceItems(preferences[SEQUENCE_ITEMS_KEY] ?: "").map { item ->
            when {
                item.id != id -> item
                item is SequenceItem.PlainBpm -> item.copy(repeatCount = count)
                item is SequenceItem.MeterPattern -> item.copy(repeatCount = count)
                else -> item
            }
        }
        preferences[SEQUENCE_ITEMS_KEY] = encodeSequenceItems(updated)
    }
}

fun sequenceItemsContentKey(items: List<SequenceItem>): String = encodeSequenceItems(items)

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
            item.repeatCount.toString(),
        ).joinToString("|")

        is SequenceItem.MeterPattern -> {
            val fields = mutableListOf(
                "m",
                item.id,
                item.bpm.toString(),
                item.selectedNote,
                item.timeSignatures.joinToString(",") { "${it.numerator}/${it.denominator}" },
                item.repeatCount.toString(),
            )
            if (item.beatClickActive.any { section -> section.any { !it } }) {
                fields.add(encodeBeatClickActive(item.beatClickActive))
            }
            fields.joinToString("|")
        }
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
        parts.size >= 5 -> SequenceItem.PlainBpm(
            id = parts[1],
            bpm = parts[2].toFloatOrNull() ?: return null,
            selectedNote = parts[3],
            repeatCount = parts[4].toIntOrNull()?.coerceAtLeast(1) ?: 1,
        )

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

    val repeatCount: Int
    if (parts.size >= 6) {
        id = parts[1]
        bpm = parts[2].toFloatOrNull() ?: return null
        selectedNote = parts[3]
        timeSignaturePartIndex = 4
        repeatCount = parts[5].toIntOrNull()?.coerceAtLeast(1) ?: 1
    } else if (parts.size >= 5) {
        id = parts[1]
        bpm = parts[2].toFloatOrNull() ?: return null
        selectedNote = parts[3]
        timeSignaturePartIndex = 4
        repeatCount = 1
    } else if (parts.size >= 4) {
        id = newSequenceItemId()
        bpm = parts[1].toFloatOrNull() ?: return null
        selectedNote = parts[2]
        timeSignaturePartIndex = 3
        repeatCount = 1
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

    val beatClickActive = reconcileBeatClickActive(
        if (parts.size >= 7) decodeBeatClickActive(parts[6]) else emptyList(),
        timeSignatures,
    )

    return SequenceItem.MeterPattern(
        id = id,
        bpm = bpm,
        selectedNote = selectedNote,
        timeSignatures = timeSignatures,
        beatClickActive = beatClickActive,
        repeatCount = repeatCount,
    )
}
