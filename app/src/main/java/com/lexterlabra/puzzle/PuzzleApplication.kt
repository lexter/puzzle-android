package com.lexterlabra.puzzle

import android.app.Application
import com.lexterlabra.puzzle.data.ScoreRepository
import com.lexterlabra.puzzle.data.SettingsRepository
import com.lexterlabra.puzzle.data.createDataStore
import com.lexterlabra.puzzle.p2p.MatchHub

/** Owns the hand-wired [AppContainer], one instance per process. */
class PuzzleApplication : Application() {
    val container: AppContainer by lazy {
        val store = createDataStore(this)
        AppContainer(SettingsRepository(store), ScoreRepository(store), MatchHub(this))
    }
}
