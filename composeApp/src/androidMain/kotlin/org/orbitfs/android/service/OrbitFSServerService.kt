package org.orbitfs.android.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Environment
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.orbitfs.android.MainActivity
import org.orbitfs.android.R
import org.orbitfs.common.client.OrbitRadar
import org.orbitfs.common.client.SatelliteServerLauncher
import org.orbitfs.common.data.SettingsRepository
import org.orbitfs.common.util.PlatformContext
import org.orbitfs.server.OrbitServerImpl
import timber.log.Timber
import java.io.File
import java.nio.file.Paths

class OrbitFSServerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeServers = mutableMapOf<Int, OrbitServerImpl>()
    private var radar: OrbitRadar? = null
    
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        println("OrbitService: onCreate")
        val platformContext = PlatformContext(applicationContext)
        settingsRepository = SettingsRepository(platformContext)
        radar = OrbitRadar(platformContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("OrbitService: onStartCommand ${intent?.action}")
        
        startForeground(NOTIFICATION_ID, createNotification("Starting Satellite..."))

        val port = intent?.getIntExtra(EXTRA_PORT, -1)?.takeIf { it > 0 } 
                  ?: settingsRepository.satellitePort.value
                  
        when (intent?.action) {
            ACTION_START -> startServer(port)
            ACTION_STOP -> stopServer(port)
            ACTION_STOP_ALL -> stopAll()
            else -> startServer(port)
        }
        return START_STICKY
    }

    private fun startServer(port: Int) {
        if (activeServers.containsKey(port)) {
            println("OrbitService: Server already running on $port")
            return
        }

        val pilotName = settingsRepository.username.value
        val avatarId = settingsRepository.avatarId.value
        val showHidden = settingsRepository.showHiddenFiles.value
        val nodeId = settingsRepository.nodeId.value
        
        val rootPath = try {
            // FORCE NEW DEFAULTS
            val dir = File(Environment.getExternalStorageDirectory(), "Test").apply {
                if (!exists()) mkdirs()
            }
            Paths.get(dir.absolutePath).toAbsolutePath().normalize()
        } catch (e: Exception) {
             println("OrbitService: Root path failure: ${e.message}")
             stopSelf()
             return
        }

        serviceScope.launch {
            try {
                println("OrbitService: FORCED ROOT for port $port at $rootPath")
                val server = SatelliteServerLauncher.create(port, rootPath, !showHidden)
                activeServers[port] = server
                
                radar?.registerService(port, pilotName, avatarId, nodeId)
                
                withContext(Dispatchers.Main) {
                    updateForegroundState()
                    settingsRepository.setSatelliteEnabled(true)
                }
                
                println("OrbitService: Satellite START on port $port")
                server.start()
            } catch (e: Exception) {
                println("OrbitService: Fatal launch error ${e.message}")
                activeServers.remove(port)
                withContext(Dispatchers.Main) {
                    updateForegroundState()
                }
            }
        }
    }

    private fun stopServer(port: Int) {
        println("OrbitService: Stopping port $port")
        val server = activeServers.remove(port)
        radar?.unregisterService()
        
        serviceScope.launch {
            try {
                server?.stop()
            } catch (e: Exception) {
            } finally {
                withContext(Dispatchers.Main) {
                    updateForegroundState()
                }
            }
        }
    }

    private fun stopAll() {
        activeServers.keys.toList().forEach { stopServer(it) }
    }

    private fun updateForegroundState() {
        if (activeServers.isEmpty()) {
            settingsRepository.setSatelliteEnabled(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            val count = activeServers.size
            val content = if (count == 1) "Satellite active on port ${activeServers.keys.first()}"
                          else "$count Satellites orbiting in background"
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, createNotification(content))
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
            .setSmallIcon(R.mipmap.ic_launcher)
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
        println("OrbitService: onDestroy")
        radar?.unregisterService()
        activeServers.values.forEach { 
            try { it.stop() } catch (e: Exception) {}
        }
        activeServers.clear()
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
    }
}
