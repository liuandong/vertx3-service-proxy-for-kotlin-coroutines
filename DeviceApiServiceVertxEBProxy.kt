package device.serviceProxy

import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.serviceproxy.ServiceException
import io.vertx.serviceproxy.ServiceExceptionMessageCodec
import io.vertx.kotlin.core.eventbus.sendAwait

class DeviceApiServiceVertxEBProxy constructor(private val vertx: Vertx, private val address: String, private val options: DeliveryOptions) : DeviceApiService {
    private val closed: Boolean = false
    init {
        try {
            this.vertx.eventBus().registerDefaultCodec(ServiceException::class.java, ServiceExceptionMessageCodec())
        } catch (ex: IllegalStateException) {
            ex.printStackTrace()
        }
    }

    suspend fun <T> getEventBusReplyValue(action:String, jsonArgs:JsonObject):T{
        if (closed) {
            throw (IllegalStateException("Proxy is closed"))
        }
        val _deliveryOptions = options
        _deliveryOptions.addHeader("action", action)
        val message = vertx.eventBus().sendAwait<T>(address, jsonArgs, _deliveryOptions)
        return message.body()
    }


    override suspend fun bindImei(deviceId:Int, imei:Long):Boolean {
        val jsonArgs = JsonObject()
        jsonArgs.put("deviceId",deviceId)
        jsonArgs.put("imei",imei)
        return getEventBusReplyValue("bindImei", jsonArgs)
    }

    override suspend fun command(deviceId:Int, cmd:Byte, amount:Short):Boolean {
        val jsonArgs = JsonObject()
        jsonArgs.put("deviceId",deviceId)
        jsonArgs.put("cmd",cmd)
        jsonArgs.put("amount",amount)
        return getEventBusReplyValue("command", jsonArgs)
    }

    override suspend fun hasDeviceId(deviceId:Int):Boolean {
        val jsonArgs = JsonObject()
        jsonArgs.put("deviceId",deviceId)
        return getEventBusReplyValue("hasDeviceId", jsonArgs)
    }

    override suspend fun online(deviceId:Int):Boolean {
        val jsonArgs = JsonObject()
        jsonArgs.put("deviceId",deviceId)
        return getEventBusReplyValue("online", jsonArgs)
    }

    override suspend fun work(deviceId:Int, time:Short, amount:Short, openid:String):Boolean {
        val jsonArgs = JsonObject()
        jsonArgs.put("deviceId",deviceId)
        jsonArgs.put("time",time)
        jsonArgs.put("amount",amount)
        jsonArgs.put("openid",openid)
        return getEventBusReplyValue("work", jsonArgs)
    }

}
