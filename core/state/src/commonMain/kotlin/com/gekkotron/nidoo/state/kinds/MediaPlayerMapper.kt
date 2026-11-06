package com.gekkotron.nidoo.state.kinds

import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.state.mqtt.MqttBindings
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * The hub's media player contract: the whole state as JSON on `state_topic`, sent on every change
 * (a missing field is null), and `{"command": …}` JSON on `command_topic`.
 */
object MediaPlayerMapper : KindMapper {
    val STATES = setOf("playing", "paused", "idle")

    /** Every command, in the order the sheet shows them. */
    private val FEATURES = listOf("play", "pause", "next", "previous", "play_media")

    /** What a player accepts when it publishes no `supported_features`. */
    private val DEFAULT_FEATURES = listOf("play", "pause", "next", "previous")

    private val HEX = Regex("^#[0-9a-fA-F]{6}$")

    fun features(b: MqttBindings): List<String> {
        val listed = b.strList("supported_features") ?: DEFAULT_FEATURES
        return FEATURES.filter { it in listed }
    }

    override fun stateTopics(b: MqttBindings): Set<String> = setOfNotNull(b.str("state_topic"))

    override fun decode(b: MqttBindings, topic: String, payload: String, previous: StateValue): Decoded {
        if (topic != b.str("state_topic")) return Decoded.Ignored
        val json = try {
            Json.parseToJsonElement(payload) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: return Decoded.Error("media player state is not a JSON object")
        fun text(key: String) = (json[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        fun seconds(key: String) = (json[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it >= 0.0 }
        val state = text("state")?.lowercase()?.takeIf { it in STATES } ?: return Decoded.Ignored
        if (state == "idle") return Decoded.State(StateValue.MediaPlayer(state, null, null, null, null, null, null, null, null))
        return Decoded.State(
            StateValue.MediaPlayer(
                state = state,
                title = text("title"),
                artist = text("artist"),
                album = text("album"),
                imageUrl = text("image_url")?.takeIf { url ->
                    url.lowercase().let { it.startsWith("https://") || it.startsWith("http://") }
                },
                color = text("color")?.takeIf { HEX.matches(it) }?.lowercase(),
                durationS = seconds("duration_s"),
                positionS = seconds("position_s"),
                positionAtMs = text("position_at")?.let(::epochMs),
            ),
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun epochMs(at: String): Long? = runCatching { Instant.parse(at).toEpochMilliseconds() }.getOrNull()

    override fun encode(b: MqttBindings, command: Command, previous: StateValue): Encoded {
        val supported = features(b)
        val payload = when (command) {
            is Command.MediaAction ->
                if (command.action in FEATURES && command.action != "play_media" && command.action in supported) {
                    buildJsonObject { put("command", command.action) }
                } else {
                    return Encoded.NotSupported("media player action '${command.action}' not supported")
                }
            is Command.PlayMedia -> {
                val query = command.query.trim()
                if ("play_media" !in supported) return Encoded.NotSupported("this player cannot search")
                if (query.isEmpty()) return Encoded.NotSupported("the query is empty")
                buildJsonObject {
                    put("command", "play_media")
                    put("query", query)
                }
            }
            else -> return Encoded.NotSupported("media player cannot $command")
        }
        val topic = b.str("command_topic") ?: return Encoded.NotSupported("no command_topic configured")
        return Encoded.Publish(listOf(outgoing(b, topic, payload.toString())))
    }

    /** Optimistic, like the vacuum: the guess shows at once and the next state message corrects it. */
    override fun feedbackTopics(b: MqttBindings, command: Command): Set<String> = emptySet()

    /** Play and pause flip the state; next, previous and a search show no guess (the title is unknown). */
    override fun predict(command: Command, previous: StateValue): StateValue? {
        val prev = previous as? StateValue.MediaPlayer ?: return null
        return when ((command as? Command.MediaAction)?.action) {
            "play" -> prev.copy(state = "playing")
            "pause" -> prev.copy(state = "paused")
            else -> null
        }
    }
}
