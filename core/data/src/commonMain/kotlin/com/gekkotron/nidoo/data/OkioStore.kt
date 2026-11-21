package com.gekkotron.nidoo.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import okio.FileSystem
import okio.Path

/** One JSON file per store, written atomically through a scratch file, on the IO dispatcher of [scope]. */
internal fun <T> okioStore(
    path: Path,
    serializer: OkioSerializer<T>,
    corruptionHandler: ReplaceFileCorruptionHandler<T>,
    scope: CoroutineScope,
): DataStore<T> = DataStoreFactory.create(
    storage = OkioStorage(FileSystem.SYSTEM, serializer, producePath = { path }),
    corruptionHandler = corruptionHandler,
    scope = CoroutineScope(scope.coroutineContext + Dispatchers.IO),
)
