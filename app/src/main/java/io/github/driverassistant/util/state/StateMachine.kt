package io.github.driverassistant.util.state

import android.util.Log

open class StateMachine<in ActionType : Action, StateType : State<ActionType, StateType>>(initialState: StateType) {
    private var currentState: StateType = initialState

    fun make(action: ActionType) {
        synchronized(this) {
            try {
                val nextState = currentState.consume(action)

                if (nextState !== currentState) {
                    Log.i(TAG, "${currentState.javaClass.simpleName} -> ${nextState.javaClass.simpleName}")
                }

                currentState = nextState
            } catch (t: Throwable) {
                throw StateSwitchException("Can't switch to the new state", t)
            }
        }
    }

    companion object {
        private const val TAG = "StateMachine"

        class StateSwitchException(message: String, cause: Throwable) : Exception(message, cause) {
            override fun toString(): String {
                return "$message, cause: $cause"
            }
        }
    }
}
