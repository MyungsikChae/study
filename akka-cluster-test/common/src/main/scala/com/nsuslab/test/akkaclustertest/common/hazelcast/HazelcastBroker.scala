package com.nsuslab.test.akkaclustertest.common.hazelcast

import com.hazelcast.core._

object HazelcastBroker {
    private lazy val hazelcast = Hazelcast.getHazelcastInstanceByName("hazelcast")
    def getMap[K, V](name: String): IMap[K, V] = hazelcast.getMap[K, V](name)
    def getQueue[T](name: String): IQueue[T] = hazelcast.getQueue[T](name)
    def getList[T](name: String): IList[T] = hazelcast.getList[T](name)
    def getSet[T](name: String): ISet[T] = hazelcast.getSet[T](name)
    def getMultiMap[K, V](name: String): MultiMap[K, V] = hazelcast.getMultiMap[K, V](name)
}
