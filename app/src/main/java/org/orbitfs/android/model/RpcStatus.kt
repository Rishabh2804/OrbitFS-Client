package org.orbitfs.android.model

import org.orbitfs.common.protocol.RPCStatus as JavaRpcStatus

enum class RpcStatus(val code: Int) {
    OK(1),
    ERROR(2),
    PONG(3);

    companion object {
        fun fromCode(code: Int): RpcStatus = entries.find { it.code == code } ?: ERROR
        fun fromJava(status: JavaRpcStatus): RpcStatus = when (status) {
            JavaRpcStatus.OK -> OK
            JavaRpcStatus.ERROR -> ERROR
            JavaRpcStatus.PONG -> PONG
        }
    }
}
