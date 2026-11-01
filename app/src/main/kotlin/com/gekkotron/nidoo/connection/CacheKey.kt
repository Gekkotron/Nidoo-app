package com.gekkotron.nidoo.connection

import com.gekkotron.nidoo.data.ConnectionProfile

/** Which house an entity cache belongs to; no credentials (entity cache spec §3.3). */
fun ConnectionProfile.Direct.cacheKey(): String = "${local.host}:${local.port}:$prefix"
