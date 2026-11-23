package com.gekkotron.nidoo.format

import java.text.Normalizer

actual fun nfd(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
