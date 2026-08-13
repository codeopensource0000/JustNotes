package code.opensource0000.justnotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

// The default viewModel() factory only knows how to construct a ViewModel
// from an Application (and optionally a SavedStateHandle) — it can't pass
// extra constructor arguments, like which PinManager to use. This wraps any
// zero-arg constructor lambda into a ViewModelProvider.Factory so custom
// arguments can still be supplied where needed.
class SimpleViewModelFactory<T : ViewModel>(private val creator: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
}
