package com.sushant.gymmanager

import android.app.Application
import com.sushant.gymmanager.data.local.AppDatabase
import com.sushant.gymmanager.data.repository.BackupManager
import com.sushant.gymmanager.data.repository.GymRepository
import com.sushant.gymmanager.i18n.Lang
import com.sushant.gymmanager.util.DateUtils
import com.sushant.gymmanager.work.DailyWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

class GymApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { AppDatabase.build(this) }
    val repository by lazy { GymRepository(this, database) }
    val backupManager by lazy { BackupManager(this, repository) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            repository.ensureSettings()
            val s = repository.getSettings()
            DailyWorker.schedule(this@GymApp, s.reminderHour)
        }
        appScope.launch {
            repository.settings.map { it.language }.distinctUntilChanged().collect { lang ->
                DateUtils.locale = if (lang == Lang.HI) Locale("hi", "IN") else Locale.ENGLISH
            }
        }
    }
}
