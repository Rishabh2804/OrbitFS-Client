package org.orbitfs.android.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.orbitfs.android.MainActivity
import org.orbitfs.android.R
import org.orbitfs.android.client.NsdHelper
import org.orbitfs.android.client.SatelliteServerLauncher
import org.orbitfs.android.data.SettingsRepository
import org.orbitfs.server.OrbitServerImpl
import timber.log.Timber
import java.io.File
import java.nio.file.Paths

class OrbitFSServerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeServers = mutableMapOf<Int, OrbitServerImpl>()
    private var nsdHelper: NsdHelper? = null
    
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        nsdHelper = NsdHelper(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, -1)?.takeIf { it > 0 } 
                  ?: settingsRepository.satellitePort.value
                  
        when (intent?.action) {
            ACTION_START -> startServer(port)
            ACTION_STOP -> stopServer(port)
            ACTION_STOP_ALL -> stopAll()
        }
        return START_STICKY
    }

    private fun startServer(port: Int) {
        if (activeServers.containsKey(port)) {
            Timber.w("Server already running on port $port")
            return
        }

        val pilotName = settingsRepository.username.value
        val avatarId = settingsRepository.avatarId.value
        val showHidden = settingsRepository.showHiddenFiles.value
        
        val rootPath = try {
            val dir = File(getExternalFilesDir(null), "shared").apply { mkdirs() }
            Paths.get(dir.absolutePath)
        } catch (e: Exception) {
             Timber.e(e, "Failed to resolve root path")
             return
        }

        serviceScope.launch {
            try {
                val server = SatelliteServerLauncher.create(port, rootPath, !showHidden)
                activeServers[port] = server
                
                nsdHelper?.registerService(port, pilotName, avatarId)
                
                updateForegroundState()
                
                Timber.i("OrbitFS Satellite starting on port $port")
                server.start()
            } catch (e: Exception) {
                Timber.e(e, "Failed to start OrbitFS server on port $port")
                activeServers.remove(port)
                updateForegroundState()
            }
        }
    }

    private fun stopServer(port: Int) {
        val server = activeServers.remove(port)
        if (server == null) return

        serviceScope.launch {
            try {
                server.stop()
                Timber.i("OrbitFS Satellite on port $port stopped")
            } catch (e: Exception) {
                Timber.e(e, "Error stopping server on port $port")
            } finally {
                updateForegroundState()
            }
        }
    }

    private fun stopAll() {
        val ports = activeServers.keys.toList()
        ports.forEach { stopServer(it) }
    }

    private fun updateForegroundState() {
        if (activeServers.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            val count = activeServers.size
            val content = if (count == 1) "Satellite active on port ${activeServers.keys.first()}"
                          else "$count Satellites orbiting in background"
            startForeground(NOTIFICATION_ID, createNotification(content))
        }
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, OrbitFSServerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OrbitFS Satellite")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_orbitfs_foreground)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_cancel, "Stop Satellite", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Satellite Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "orbitfs_satellite"
        private const val NOTIFICATION_ID = 2001
        
        const val ACTION_START = "org.orbitfs.android.ACTION_START_SATELLITE"
        const val ACTION_STOP = "org.orbitfs.android.ACTION_STOP_SATELLITE"
        const val ACTION_STOP_ALL = "org.orbitfs.android.ACTION_STOP_ALL_SATELLITES"
        
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_ROOT_URI = "extra_root_uri"
    }
}
