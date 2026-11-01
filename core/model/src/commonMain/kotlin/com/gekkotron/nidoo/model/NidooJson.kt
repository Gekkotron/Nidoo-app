package com.gekkotron.nidoo.model

import kotlinx.serialization.json.Json

/** Lenient JSON for everything Nidoo reads from the broker. */
val NidooJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    // Unknown enum values (e.g. a role added by a newer app) fall back to the default (null).
    coerceInputValues = true
}
