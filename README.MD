# vertx3 服务代理 支持kotlin的协程

+ 因为官方的服务发现只支持java客户端，而且maven相关配置麻烦
+ 你可以把ServiceProxyCodeGen.kt复制到你的test目录进行运行，生成你要的接口对应的操作类
+ 即可运行支持协程的服务代理，配合服务发现。
