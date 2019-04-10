package device.serviceProxy

import app.App
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import io.vertx.serviceproxy.ProxyHandler
import io.vertx.serviceproxy.ServiceException
import io.vertx.serviceproxy.ServiceExceptionMessageCodec
import kotlinx.coroutines.launch

class DeviceApiServiceVertxProxyHandler(private val vertx: Vertx, private val service: DeviceApiService, topLevel: Boolean, private val timeoutSeconds: Long) : ProxyHandler() {
    private val timerID: Long
    private var lastAccessed: Long = 0

    constructor(vertx: Vertx, service: DeviceApiService, timeoutInSecond: Long = 300) : this(vertx, service, true, timeoutInSecond)

    init {
        try {
            this.vertx.eventBus().registerDefaultCodec(ServiceException::class.java,
                    ServiceExceptionMessageCodec())
        } catch (ex: IllegalStateException) {
        }

        if (timeoutSeconds != -1L && !topLevel) {
            var period = timeoutSeconds * 1000 / 2
            if (period > 10000) {
                period = 10000
            }
            this.timerID = vertx.setPeriodic(period) { this.checkTimedOut(it) }
        } else {
            this.timerID = -1
        }
        accessed()
    }


    private fun checkTimedOut(id: Long) {
        val now = System.nanoTime()
        if (now - lastAccessed > timeoutSeconds * 1000000000) {
            close()
        }
    }

    override fun close() {
        if (timerID != -1L) {
            vertx.cancelTimer(timerID)
        }
        super.close()
    }

    private fun accessed() {
        this.lastAccessed = System.nanoTime()
    }

    override fun handle(msg: Message<JsonObject>) {
        try {
            val json = msg.body()
            val action = msg.headers().get("action") ?: throw IllegalStateException("action not specified")
            accessed()
            App.launch(App.coroutineContext){
                when (action) {
                
                "bindImei" -> {
                    msg.reply(
                        service.bindImei(
                            json.getInteger("deviceId"),
                            json.getLong("imei")
                        )
                    )
                }
                "command" -> {
                    msg.reply(
                        service.command(
                            json.getInteger("deviceId"),
                            json.getInteger("cmd").toByte(),
                            json.getInteger("amount").toShort()
                        )
                    )
                }
                "hasDeviceId" -> {
                    msg.reply(
                        service.hasDeviceId(
                            json.getInteger("deviceId")
                        )
                    )
                }
                "online" -> {
                    msg.reply(
                        service.online(
                            json.getInteger("deviceId")
                        )
                    )
                }
                "work" -> {
                    msg.reply(
                        service.work(
                            json.getInteger("deviceId"),
                            json.getInteger("time").toShort(),
                            json.getInteger("amount").toShort(),
                            json.getString("openid")
                        )
                    )
                }
                else -> throw IllegalStateException("Invalid action: $action")
                }
            }
        } catch (t: Throwable) {
            msg.reply(ServiceException(500, t.message))
            throw t
        }
    }
}
