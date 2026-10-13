package hrshd1eux.flowgrab.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import hrshd1eux.flowgrab.util.PreferenceUtil.getBoolean
import hrshd1eux.flowgrab.util.PreferenceUtil.getInt
import hrshd1eux.flowgrab.util.PreferenceUtil.getString
import hrshd1eux.flowgrab.util.PreferenceUtil.updateBoolean
import hrshd1eux.flowgrab.util.PreferenceUtil.updateInt
import hrshd1eux.flowgrab.util.PreferenceUtil.updateString

inline val String.booleanState: MutableState<Boolean>
    @Composable
    get() {
        val key = this
        val state = remember(key) { mutableStateOf(key.getBoolean()) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, key) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    state.value = key.getBoolean()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        return remember(key) {
            object : MutableState<Boolean> {
                override var value: Boolean
                    get() = state.value
                    set(v) {
                        state.value = v
                        key.updateBoolean(v)
                    }
                override fun component1(): Boolean = value
                override fun component2(): (Boolean) -> Unit = { value = it }
            }
        }
    }

inline val String.stringState: MutableState<String>
    @Composable
    get() {
        val key = this
        val state = remember(key) { mutableStateOf(key.getString()) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, key) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    state.value = key.getString()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        return remember(key) {
            object : MutableState<String> {
                override var value: String
                    get() = state.value
                    set(v) {
                        state.value = v
                        key.updateString(v)
                    }
                override fun component1(): String = value
                override fun component2(): (String) -> Unit = { value = it }
            }
        }
    }

inline val String.intState: MutableIntState
    @Composable
    get() {
        val key = this
        val state = remember(key) { mutableIntStateOf(key.getInt()) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, key) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    state.intValue = key.getInt()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        return remember(key) {
            object : MutableIntState {
                override var intValue: Int
                    get() = state.intValue
                    set(v) {
                        state.intValue = v
                        key.updateInt(v)
                    }
                override var value: Int
                    get() = intValue
                    set(v) {
                        intValue = v
                    }
                override fun component1(): Int = intValue
                override fun component2(): (Int) -> Unit = { intValue = it }
            }
        }
    }