package com.sushant.bahikhata.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sushant.bahikhata.App
import com.sushant.bahikhata.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** One view model shared by every screen (the data set is small and always needed together). */
class AppVm(app: Application) : AndroidViewModel(app) {
    val a = app as App
    val repo = a.repo
    val prefs = a.prefs

    private fun <T> Flow<T>.st(init: T) = stateIn(viewModelScope, SharingStarted.Eagerly, init)

    val settings = prefs.settings
    val accounts = repo.accounts.st(emptyList())
    val categories = repo.categories.st(emptyList())
    val entries = repo.entries.st(emptyList())
    val people = repo.people.st(emptyList())
    val udhar = repo.udhar.st(emptyList())
    val budgets = repo.budgets.st(emptyList())
    val goals = repo.goals.st(emptyList())
    val recurring = repo.recurring.st(emptyList())
    val merchants = repo.merchants.st(emptyList())
    val attCounts = repo.attachmentCounts.map { l -> l.associate { it.entryId to it.n } }.st(emptyMap())
    val smsPending = repo.smsPending.st(emptyList())
    val smsLook = repo.smsLook.st(emptyList())
    val smsIgnored = repo.smsIgnored.st(emptyList())

    /** True once the first database read has arrived (avoids flashing empty states). */
    val loaded = repo.entries.map { true }.st(false)

    val nets: StateFlow<Map<Long, Long>> = combine(accounts, entries) { a, e -> Ledger.nets(a, e) }.st(emptyMap())

    val catMap = categories.map { l -> l.associateBy { it.id } }.st(emptyMap())
    val accMap = accounts.map { l -> l.associateBy { it.id } }.st(emptyMap())
    val personMap = people.map { l -> l.associateBy { it.id } }.st(emptyMap())

    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }

    fun setTheme(t: ThemeMode) = prefs.update { it.copy(theme = t) }
    fun toggleHide() = prefs.update { it.copy(hideAmounts = !it.hideAmounts) }
}
