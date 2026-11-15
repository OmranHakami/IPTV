package com.oh.tv.data.remote

import java.util.concurrent.Semaphore

/** Non-blocking connection admission: callers must release each acquired slot. */
internal class PairingConnectionLimiter(maxConcurrentClients: Int) {
    private val slots = Semaphore(maxConcurrentClients)

    fun tryAcquire(): Boolean = slots.tryAcquire()

    fun release() {
        slots.release()
    }
}
