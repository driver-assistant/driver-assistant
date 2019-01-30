package io.github.driverassistant.state

import io.github.driverassistant.util.state.State

abstract class MainScreenActivityState : State<MainScreenActivityAction, MainScreenActivityState>() {

    override fun consume(action: MainScreenActivityAction): MainScreenActivityState = when (action) {
        is ImageShotAction -> this

        else -> super.consume(action)
    }
}
