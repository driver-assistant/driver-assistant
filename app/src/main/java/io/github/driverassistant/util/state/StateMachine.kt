package io.github.driverassistant.util.state

open class StateMachine<in ActionType : Action, StateType : State<ActionType, StateType>>(initialState: StateType) {
    private var currentState: StateType = initialState

    fun make(action: ActionType) {
        synchronized(this) {
            try {
                val nextState = currentState.consume(action)

                if (nextState !== currentState) {
                    println("${currentState.javaClass.simpleName} -> ${nextState.javaClass.simpleName}")
                }

                currentState = nextState
            } catch (t: Throwable) {
                throw StateSwitchException("Can't switch to the new state", t)
            }
        }
    }

    companion object {
        class StateSwitchException(message: String, cause: Throwable) : Exception(message, cause) {
            override fun toString(): String {
                return "$message, cause: $cause"
            }
        }
    }
}
