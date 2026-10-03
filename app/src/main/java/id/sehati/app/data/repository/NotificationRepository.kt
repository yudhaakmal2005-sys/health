package id.sehati.app.data.repository

import id.sehati.app.core.util.Clock
import id.sehati.app.data.local.SehatiDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(private val db: SehatiDatabase, private val clock: Clock) {
    fun observe(userId: String) = db.systemDao().observeNotifications(userId)
    suspend fun markAllRead(userId: String) = db.systemDao().markAllRead(userId, clock.now())
}
