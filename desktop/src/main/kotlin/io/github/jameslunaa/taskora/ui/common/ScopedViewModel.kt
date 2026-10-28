package io.github.jameslunaa.taskora.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * A ViewModel that lives while this composable stays in the composition with the same [key],
 * and is cleared when it leaves or the key changes. Desktop has no navigation back stack to
 * scope ViewModels to, so each pane scopes its own.
 */
@Composable
inline fun <reified VM : ViewModel> scopedViewModel(key: Any, noinline create: () -> VM): VM {
    val owner = remember(key) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    return viewModel(viewModelStoreOwner = owner) { create() }
}
