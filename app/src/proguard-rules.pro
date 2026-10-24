
# 备份通过 Gson 按“字段名 = 数据库列名”序列化 Room 实体；混淆会改字段名，导致备份读到全 null 的实体。
-keepclassmembers class me.kafuuneko.rpclient.libs.room.entity.** { <fields>; }
