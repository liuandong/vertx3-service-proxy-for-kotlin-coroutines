package device.serviceProxy

interface DeviceApiService {
    suspend fun hasDeviceId(deviceId: Int): Boolean
    suspend fun online(deviceId: Int): Boolean
    suspend fun work(deviceId: Int, time: Short, amount: Short, openid: String): Boolean
    suspend fun command(deviceId: Int, cmd: Byte, amount: Short): Boolean
    suspend fun bindImei(deviceId: Int, imei: Long): Boolean
}
