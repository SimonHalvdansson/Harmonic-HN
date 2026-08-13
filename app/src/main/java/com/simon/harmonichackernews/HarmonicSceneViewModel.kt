package com.simon.harmonichackernews

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.simon.harmonichackernews.app.HarmonicSceneComposition

/** Retains one scene graph across configuration changes without moving it to Application scope. */
class HarmonicSceneViewModel(application: Application) : AndroidViewModel(application) {
    val scene: HarmonicSceneComposition = application.harmonicAppComposition.createScene()

    override fun onCleared() {
        scene.close()
        super.onCleared()
    }
}
