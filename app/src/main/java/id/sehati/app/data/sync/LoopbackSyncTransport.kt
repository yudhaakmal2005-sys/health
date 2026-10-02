package id.sehati.app.data.sync

/**
 * HANYA UNTUK MODE DEMO: server simulasi di perangkat. Tidak ada data yang dikirim ke jaringan.
 * UI wajib melabelinya "Simulasi demo". Idempoten: kiriman ulang dibalas DUPLICATE.
 */
class LoopbackSyncTransport(private val seen: MutableSet<String> = mutableSetOf()) : SyncTransport {
    override val label = "Simulasi demo (tidak dikirim ke server)"

    override suspend fun push(request: SyncPushRequest): TransportResult = TransportResult.Success(
        request.items.map {
            val dup = !seen.add(it.id)
            SyncAckDto(it.id, if (dup) AckStatus.DUPLICATE else AckStatus.OK, "demo-${it.type}-${it.entityId.takeLast(8)}")
        },
    )
}
