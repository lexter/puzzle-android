package com.lexterlabra.puzzle.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File

const val DATA_STORE_FILE = "puzzle.preferences_pb"

/** Preferences file in the app's private files dir. */
fun createDataStore(context: Context): DataStore<Preferences> = createDataStore(context.filesDir.resolve(DATA_STORE_FILE))

fun createDataStore(file: File): DataStore<Preferences> = PreferenceDataStoreFactory.create(produceFile = { file })
