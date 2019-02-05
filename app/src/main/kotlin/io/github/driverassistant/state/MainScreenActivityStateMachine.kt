package io.github.driverassistant.state

import io.github.driverassistant.state.implementation.PausedState
import io.github.driverassistant.util.state.StateMachine

class MainScreenActivityStateMachine : StateMachine<MainScreenActivityAction, MainScreenActivityState>(PausedState())
