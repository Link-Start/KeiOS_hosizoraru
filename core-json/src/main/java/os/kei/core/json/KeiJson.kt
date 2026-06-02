package os.kei.core.json

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

object KeiJson {
    val lenient: Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = true
            encodeDefaults = true
        }

    val pretty: Json =
        Json(lenient) {
            prettyPrint = true
        }
}

fun String.parseJsonElementOrNull(json: Json = KeiJson.lenient): JsonElement? {
    val value = trim()
    if (value.isBlank()) return null
    return runCatching { json.parseToJsonElement(value) }.getOrNull()
}

fun String.parseJsonObjectOrNull(json: Json = KeiJson.lenient): JsonObject? {
    return parseJsonElementOrNull(json)?.jsonObjectOrNull()
}

fun String.parseJsonArrayOrNull(json: Json = KeiJson.lenient): JsonArray? {
    return parseJsonElementOrNull(json)?.jsonArrayOrNull()
}

fun JsonElement?.jsonObjectOrNull(): JsonObject? {
    return this as? JsonObject
}

fun JsonElement?.jsonArrayOrNull(): JsonArray? {
    return this as? JsonArray
}

fun JsonElement?.jsonPrimitiveOrNull(): JsonPrimitive? {
    return this as? JsonPrimitive
}

fun JsonObject.optObject(key: String): JsonObject? {
    return this[key].jsonObjectOrNull()
}

fun JsonObject.optArray(key: String): JsonArray? {
    return this[key].jsonArrayOrNull()
}

fun JsonObject.optString(key: String, defaultValue: String = ""): String {
    return this[key].jsonPrimitiveOrNull()?.contentOrNull ?: defaultValue
}

fun JsonObject.optStringOrNull(key: String): String? {
    return this[key].jsonPrimitiveOrNull()?.contentOrNull
}

fun JsonObject.optInt(key: String, defaultValue: Int = 0): Int {
    return this[key].jsonPrimitiveOrNull()?.intOrNull ?: defaultValue
}

fun JsonObject.optLong(key: String, defaultValue: Long = 0L): Long {
    return this[key].jsonPrimitiveOrNull()?.longOrNull ?: defaultValue
}

fun JsonObject.optFloat(key: String, defaultValue: Float = 0f): Float {
    return this[key].jsonPrimitiveOrNull()?.floatOrNull ?: defaultValue
}

fun JsonObject.optDouble(key: String, defaultValue: Double = 0.0): Double {
    return this[key].jsonPrimitiveOrNull()?.doubleOrNull ?: defaultValue
}

fun JsonObject.optBoolean(key: String, defaultValue: Boolean = false): Boolean {
    return this[key].jsonPrimitiveOrNull()?.booleanOrNull ?: defaultValue
}

fun JsonObject.hasNonNull(key: String): Boolean {
    return containsKey(key) && this[key] !is JsonNull
}

fun JsonObject.toMutableJsonMap(): MutableMap<String, JsonElement> {
    return entries.associateTo(LinkedHashMap()) { entry ->
        entry.key to entry.value
    }
}

fun JsonArray.optObject(index: Int): JsonObject? {
    return getOrNull(index).jsonObjectOrNull()
}

fun JsonArray.optArray(index: Int): JsonArray? {
    return getOrNull(index).jsonArrayOrNull()
}

fun JsonArray.optString(index: Int, defaultValue: String = ""): String {
    return getOrNull(index).jsonPrimitiveOrNull()?.contentOrNull ?: defaultValue
}

inline fun <reified T> Json.decodeFromStringOrNull(raw: String): T? {
    if (raw.isBlank()) return null
    return try {
        decodeFromString<T>(raw)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

fun JsonObject.encodeCompact(json: Json = KeiJson.lenient): String {
    return json.encodeToString(JsonObject.serializer(), this)
}

fun JsonArray.encodeCompact(json: Json = KeiJson.lenient): String {
    return json.encodeToString(JsonArray.serializer(), this)
}
