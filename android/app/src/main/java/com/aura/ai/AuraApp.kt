package com.aura.ai

import android.app.Application
import android.content.Context
import com.aura.ai.data.ApiClient
import com.aura.ai.data.AuraRepository
import com.aura.ai.data.BillingManager
import com.aura.ai.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AuraApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    lateinit var repository: AuraRepository
        private set
    lateinit var billing: BillingManager
        private set

    override fun onCreate() {
        super.onCreate()
        val session = SessionStore(this)
        repository = AuraRepository(this, session, ApiClient(session))
        billing = BillingManager(this, repository, appScope)
    }
}

val Context.auraApp: AuraApp get() = applicationContext as AuraApp
