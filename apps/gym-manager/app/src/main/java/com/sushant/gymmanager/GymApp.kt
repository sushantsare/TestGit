package com.sushant.gymmanager

import android.app.Application
import com.sushant.gymmanager.data.local.AppDatabase
import com.sushant.gymmanager.data.repository.BackupManager
import com.sushant.gymmanager.data.repository.GymRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GymApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { AppDatabase.build(this) }
    val repository by lazy { GymRepository(this, database) }
    val backupManager by lazy { BackupManager(this, repository) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.ensureSettings() }
    }
}
