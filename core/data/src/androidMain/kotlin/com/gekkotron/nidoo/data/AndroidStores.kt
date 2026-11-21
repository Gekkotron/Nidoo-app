package com.gekkotron.nidoo.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toOkioPath
import java.io.File

// The Android paths the app has always used: an upgrade reads the same files.

fun AppPreferences.Companion.create(context: Context, scope: CoroutineScope): AppPreferences =
    create(context.filesDir.resolve(AppPreferences.FILE), scope)

fun AppPreferences.Companion.create(file: File, scope: CoroutineScope): AppPreferences = create(file.toOkioPath(), scope)

fun ProfileRepository.Companion.create(context: Context, secrets: SecretStore, scope: CoroutineScope): ProfileRepository =
    create(context.filesDir.resolve(ProfileRepository.FILE), secrets, scope)

fun ProfileRepository.Companion.create(file: File, secrets: SecretStore, scope: CoroutineScope): ProfileRepository =
    create(file.toOkioPath(), secrets, scope)

/** In `noBackupFilesDir`, which is never backed up or transferred. */
fun FileEntityCache.Companion.create(context: Context, scope: CoroutineScope): FileEntityCache =
    create(context.noBackupFilesDir.resolve(FileEntityCache.FILE), scope)

fun FileEntityCache.Companion.create(file: File, scope: CoroutineScope): FileEntityCache = create(file.toOkioPath(), scope)
