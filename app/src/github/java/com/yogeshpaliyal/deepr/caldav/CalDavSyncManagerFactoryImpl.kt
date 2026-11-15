package com.yogeshpaliyal.deepr.caldav

import android.content.Context
import com.yogeshpaliyal.deepr.DeeprQueries
import com.yogeshpaliyal.deepr.preference.AppPreferenceDataStore
import kotlinx.coroutines.CoroutineScope

object CalDavSyncManagerFactoryImpl : CalDavSyncManagerFactory {
    override fun create(
        context: Context,
        deeprQueries: DeeprQueries,
        preferenceDataStore: AppPreferenceDataStore,
        appScope: CoroutineScope,
    ): CalDavSyncManager =
        CalDavSyncManagerImpl(
            context = context,
            deeprQueries = deeprQueries,
            preferenceDataStore = preferenceDataStore,
            appScope = appScope,
        )
}
