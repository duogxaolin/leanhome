package com.leanhome.app

import android.app.Application
import com.leanhome.core.data.LeanHomeDataContainer

/**
 * LeanHome application — wires up the data container on app launch.
 */
class LeanHomeApp : Application() {

    lateinit var dataContainer: LeanHomeDataContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        dataContainer = LeanHomeDataContainer(this)
    }

    companion object {
        lateinit var instance: LeanHomeApp
            private set
    }
}
