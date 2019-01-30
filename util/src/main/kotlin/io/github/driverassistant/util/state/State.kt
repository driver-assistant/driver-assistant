package io.github.driverassistant.util.state

abstract class State<in ActionType : Action, out StateType : State<ActionType, StateType>> {
    open fun consume(action: ActionType): StateType {
        throw IllegalStateException("Invalid action ($action) have been passed to this state ($this)")
    }
}
