package com.gekkotron.nidoo.state.kinds

import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.StateValue.MediaPlayer
import com.gekkotron.nidoo.model.StateValue.Unknown
import com.gekkotron.nidoo.state.bindings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class MediaPlayerMapperTest {
    private val player = bindings(
        "media_player",
        """{"state_topic":"m/player/state","command_topic":"m/player/set",
           "supported_features":["play","pause","next","previous","play_media"]}""",
    )

    private fun decode(payload: String, previous: com.gekkotron.nidoo.model.StateValue = Unknown) =
        MediaPlayerMapper.decode(player, "m/player/state", payload, previous)

    private fun sent(encoded: Encoded): JsonObject {
        assertIs<Encoded.Publish>(encoded)
        val message = encoded.messages.single()
        assertEquals("m/player/set", message.topic)
        assertEquals(false, message.retain)
        return Json.parseToJsonElement(message.payload) as JsonObject
    }

    @Test
    fun `full state`() {
        assertEquals(
            Decoded.State(
                MediaPlayer(
                    state = "playing", title = "Song", artist = "Band", album = "Record",
                    imageUrl = "https://img.example/c.jpg", color = "#3d2466",
                    durationS = 431.0, positionS = 42.5, positionAtMs = 1_793_960_400_000,
                ),
            ),
            decode(
                """{"state":"playing","title":"Song","artist":"Band","album":"Record",
                   "image_url":"https://img.example/c.jpg","color":"#3d2466",
                   "duration_s":431,"position_s":42.5,"position_at":"2026-11-06T10:20:00Z"}""",
            ),
        )
    }

    @Test
    fun `a message replaces the whole state`() {
        val previous = MediaPlayer("playing", "A", "Band", "R", null, "#112233", 100.0, 1.0, 5)
        assertEquals(
            Decoded.State(MediaPlayer("paused", "B", null, null, null, null, null, null, null)),
            decode("""{"state":"paused","title":"B"}""", previous),
        )
    }

    @Test
    fun `idle clears every other field`() {
        assertEquals(
            Decoded.State(MediaPlayer("idle", null, null, null, null, null, null, null, null)),
            decode("""{"state":"idle","title":"x","color":"#000000","duration_s":3}"""),
        )
    }

    @Test
    fun `a loading song has no position_at`() {
        assertEquals(
            Decoded.State(MediaPlayer("playing", "B", null, null, null, null, 200.0, 0.0, null)),
            decode("""{"state":"playing","title":"B","duration_s":200,"position_s":0,"position_at":null}"""),
        )
    }

    @Test
    fun `bad values are dropped`() {
        val decoded = decode(
            """{"state":"playing","color":"red; x","image_url":"javascript:x","duration_s":-1,
               "position_s":"x","position_at":"yesterday"}""",
        )
        assertEquals(Decoded.State(MediaPlayer("playing", null, null, null, null, null, null, null, null)), decoded)
        val upper = decode("""{"state":"playing","color":"#3D2466"}""")
        assertEquals("#3d2466", ((upper as Decoded.State).value as MediaPlayer).color)
    }

    @Test
    fun `unknown state word is ignored`() {
        assertEquals(Decoded.Ignored, decode("""{"state":"buffering"}"""))
        assertIs<Decoded.Error>(decode("not json"))
        assertEquals(Decoded.Ignored, MediaPlayerMapper.decode(player, "other/topic", "{}", Unknown))
    }

    @Test
    fun `commands publish json`() {
        for (action in listOf("play", "pause", "next", "previous")) {
            assertEquals(Json.parseToJsonElement("""{"command":"$action"}"""), sent(MediaPlayerMapper.encode(player, Command.MediaAction(action), Unknown)))
        }
        assertEquals(
            Json.parseToJsonElement("""{"command":"play_media","query":"du Daft Punk"}"""),
            sent(MediaPlayerMapper.encode(player, Command.PlayMedia(" du Daft Punk "), Unknown)),
        )
    }

    @Test
    fun `unsupported command is refused`() {
        val small = bindings(
            "media_player",
            """{"state_topic":"m/player/state","command_topic":"m/player/set","supported_features":["play","pause"]}""",
        )
        assertIs<Encoded.NotSupported>(MediaPlayerMapper.encode(small, Command.MediaAction("next"), Unknown))
        assertIs<Encoded.NotSupported>(MediaPlayerMapper.encode(small, Command.PlayMedia("x"), Unknown))
        assertIs<Encoded.NotSupported>(MediaPlayerMapper.encode(player, Command.PlayMedia("  "), Unknown))
        assertIs<Encoded.NotSupported>(MediaPlayerMapper.encode(player, Command.TurnOn(), Unknown))
    }

    @Test
    fun `features default without the key`() {
        val bare = bindings("media_player", """{"state_topic":"s","command_topic":"c"}""")
        assertEquals(listOf("play", "pause", "next", "previous"), MediaPlayerMapper.features(bare))
        assertEquals(listOf("play", "pause", "next", "previous", "play_media"), MediaPlayerMapper.features(player))
    }

    @Test
    fun `prediction`() {
        val playing = MediaPlayer("playing", "A", null, null, null, null, null, null, null)
        assertEquals("paused", (MediaPlayerMapper.predict(Command.MediaAction("pause"), playing) as MediaPlayer).state)
        assertEquals("playing", (MediaPlayerMapper.predict(Command.MediaAction("play"), playing.copy(state = "paused")) as MediaPlayer).state)
        assertNull(MediaPlayerMapper.predict(Command.MediaAction("next"), playing))
        assertNull(MediaPlayerMapper.predict(Command.MediaAction("previous"), playing))
        assertNull(MediaPlayerMapper.predict(Command.PlayMedia("x"), playing))
    }
}
