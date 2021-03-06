package service

import device.service.DeviceApiService
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.junit.Test
import java.io.File
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.jvmName

class ServiceProxyCodeGen {
    val baseUrl = System.getProperty("user.dir") + "/src/main/kotlin"
    val list = arrayListOf(
            DeviceApiService::class
    )

    @Test
    fun gen(){
        list.forEach{
            val packageUrl = it.java.`package`.name.replace(".","/")
            val url = "$baseUrl/$packageUrl/"

            val eBProxyFileUrl = "$url${it.simpleName}VertxEBProxy.kt"
            val eBProxyFile = File(eBProxyFileUrl)
            if (!eBProxyFile.exists()) eBProxyFile.createNewFile()
            eBProxyFile.writeText(EBProxyTemplate(it))

            val proxyHandlerFileUrl = "$url${it.simpleName}ProxyHandler.kt"
            val proxyHandlerFile = File(proxyHandlerFileUrl)
            if (!proxyHandlerFile.exists()) proxyHandlerFile.createNewFile()
            proxyHandlerFile.writeText(ProxyHanderTemplate(it))
        }
    }

    fun EBProxyTemplate(clazz: KClass<*>):String{
        val result = """
${clazz.java.`package`}

import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.serviceproxy.ServiceException
import io.vertx.serviceproxy.ServiceExceptionMessageCodec
import io.vertx.kotlin.core.eventbus.sendAwait

class ${clazz.simpleName}VertxEBProxy constructor(private val vertx: Vertx, private val address: String, private val options: DeliveryOptions) : DeviceApiService {
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

${genMethod(clazz)}
}

""".trimIndent()
        return result
    }

    fun genMethod(clazz: KClass<*>): String {
        val sj = StringJoiner("")
        clazz.declaredFunctions.forEach {
            sj.add("""
    override suspend fun ${it.name}(${genArgs(it)}):${it.returnType.jvmErasure.simpleName} {
        val jsonArgs = JsonObject()
        ${putJson(it)}
        return getEventBusReplyValue("${it.name}", jsonArgs)
    }
"""
            )
        }
        return sj.toString()
    }

    private fun genArgs(function:KFunction<*>): String {
        val sj = StringJoiner(", ")
        function.valueParameters.forEach {
            sj.add("${it.name}:${it.type.jvmErasure.simpleName}")
        }
        return sj.toString()
    }

    private fun putJson(function:KFunction<*>): String {
        val sj = StringJoiner("\n        ")
        function.valueParameters.forEach {
            sj.add("jsonArgs.put(\"${it.name}\",${it.name})")
        }
        return sj.toString()
    }

    fun ProxyHanderTemplate(clazz: KClass<*>):String{
        val result = """
${clazz.java.`package`}

import app.App
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import io.vertx.serviceproxy.ProxyHandler
import io.vertx.serviceproxy.ServiceException
import io.vertx.serviceproxy.ServiceExceptionMessageCodec
import kotlinx.coroutines.launch

class ${clazz.simpleName}VertxProxyHandler(private val vertx: Vertx, private val service: ${clazz.simpleName}, topLevel: Boolean, private val timeoutSeconds: Long) : ProxyHandler() {
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
                ${genWhenStatement(clazz)}
                else -> throw IllegalStateException("Invalid action: ${"$"+"action"}")
                }
            }
        } catch (t: Throwable) {
            msg.reply(ServiceException(500, t.message))
            throw t
        }
    }
}

""".trimIndent()
        return result
    }

    fun genWhenStatement(clazz: KClass<*>): String {
        val sj = StringJoiner("")
        clazz.declaredFunctions.forEach{
            sj.add("""
                "${it.name}" -> {
                    msg.reply(
                        service.${it.name}(
                            ${genJsonPara(it)}
                        )
                    )
                }""")
        }
        return sj.toString()
    }

    fun genJsonPara(function: KFunction<*>): String {
        val sj = StringJoiner(",\n                            ")
        function.valueParameters.forEach {
            sj.add(getJsonGetStr(it.type, it.name!!))
        }
        return sj.toString()
    }

    fun getJsonGetStr(type:KType, key:String): String {
        return when(type.jvmErasure){
            Boolean::class -> "json.getBoolean(\"$key\")"
            Byte::class -> "json.getInteger(\"$key\").toByte()"
            Short::class -> "json.getInteger(\"$key\").toShort()"
            Int::class -> "json.getInteger(\"$key\")"
            Long::class -> "json.getLong(\"$key\")"
            Float::class -> "json.getFloat(\"$key\")"
            Double::class -> "json.getDouble(\"$key\")"
            String::class -> "json.getString(\"$key\")"
            JsonObject::class -> "json.getJsonObject(\"$key\")"
            JsonArray::class -> "json.getJsonArray(\"$key\")"
            else -> " type" + type.jvmErasure.jvmName + " is not found!!!"
        }
    }
}
