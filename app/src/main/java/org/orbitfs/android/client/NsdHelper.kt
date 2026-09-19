package org.orbitfs.android.client

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

class NsdHelper(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    
    val discoveredServices = MutableStateFlow<Set<NsdServiceInfo>>(emptySet())

    fun registerService(port: Int, pilotName: String, avatarId: String) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceType = SERVICE_TYPE
            serviceName = "OrbitFS-$pilotName"
            setPort(port)
            // Use setAttribute for metadata like avatarId
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute("avatarId", avatarId)
            }
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredServiceName = info.serviceName
                Timber.d("NSD: Service registered: ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Timber.e("NSD: Service registration failed: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                registeredServiceName = null
                Timber.d("NSD: Service unregistered: ${info.serviceName}")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Timber.e("NSD: Service unregistration failed: $errorCode")
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun startDiscovery() {
        if (discoveryListener != null) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Timber.d("NSD: Discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Timber.d("NSD: Service found: ${service.serviceName}")
                if (service.serviceType.contains("orbitfs")) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Timber.e("NSD: Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val hostAddress = serviceInfo.host?.hostAddress
                            Timber.d("NSD: Service resolved: ${serviceInfo.serviceName} at $hostAddress:${serviceInfo.port}")
                            
                            // FILTER SELF: Skip if name matches OR if IP is localhost/device IP
                            val isSelf = serviceInfo.serviceName == registeredServiceName
                            
                            if (!isSelf) {
                                discoveredServices.update { current ->
                                    // Also filter out redundant entries for the same service name
                                    current.filter { it.serviceName != serviceInfo.serviceName }.toSet() + serviceInfo
                                }
                            } else {
                                Timber.d("NSD: Filtered out self: ${serviceInfo.serviceName}")
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                discoveredServices.update { current ->
                    current.filter { it.serviceName != service.serviceName }.toSet()
                }
            }

            override fun onDiscoveryStopped(regType: String) {}
            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) { nsdManager.stopServiceDiscovery(this) }
            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) { nsdManager.stopServiceDiscovery(this) }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        discoveryListener = null
        discoveredServices.value = emptySet()
    }

    fun unregisterService() {
        registrationListener?.let { nsdManager.unregisterService(it) }
        registrationListener = null
        registeredServiceName = null
    }

    companion object {
        private const val SERVICE_TYPE = "_orbitfs._tcp"
        
        /**
         * Global tracking of the local service name to filter out self from Radar.
         */
        @Volatile
        var registeredServiceName: String? = null
    }
}
