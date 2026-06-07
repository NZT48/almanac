package com.example.almanac

import android.app.Application
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.example.almanac.data.file.DownloadsTextSink
import com.example.almanac.data.file.WeeklyDigestFormatter
import com.example.almanac.data.healthconnect.HealthConnectSource
import com.example.almanac.data.notion.NotionApi
import com.example.almanac.data.notion.NotionCredentialsStore
import com.example.almanac.data.notion.NotionSource
import com.example.almanac.data.settings.SettingsRepository
import com.example.almanac.domain.usecase.ExportDataUseCase
import com.example.almanac.domain.usecase.ReadHealthDataUseCase

class AlmanacApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(private val context: Context) {

    val healthConnect: HealthConnectClient? by lazy {
        val sdkStatus = HealthConnectClient.getSdkStatus(context)
        if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    private val source: HealthConnectSource? by lazy {
        healthConnect?.let { HealthConnectSource(it) }
    }

    val formatter: WeeklyDigestFormatter by lazy { WeeklyDigestFormatter() }
    val sink: DownloadsTextSink by lazy { DownloadsTextSink(context.applicationContext, formatter) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context.applicationContext) }

    val notionCredentials: NotionCredentialsStore by lazy {
        NotionCredentialsStore(context.applicationContext)
    }
    val notionApi: NotionApi by lazy { NotionApi(notionCredentials) }
    val notionSource: NotionSource by lazy { NotionSource(notionApi) }

    val readUseCase: ReadHealthDataUseCase? by lazy {
        source?.let { ReadHealthDataUseCase(it) }
    }
    val exportUseCase: ExportDataUseCase by lazy { ExportDataUseCase(sink) }
}
