package com.nsuslab.test.akkaclustertest.manager

import akka.actor.{ActorSystem, Props}
import akka.cluster.sharding.ClusterSharding
import com.hazelcast.config.ClasspathXmlConfig
import com.hazelcast.core.{Hazelcast, HazelcastInstance, IMap}
import com.nsuslab.test.akkaclustertest.common.database.entity.GameDataInfo
import com.nsuslab.test.akkaclustertest.common.shard.{GameSharding, PlayerSharding, TableSharding}
import com.nsuslab.test.akkaclustertest.manager.service.ManagerService
import com.typesafe.config.{Config, ConfigFactory}

object launcher {
  def main(arg: Array[String]): Unit = {
    start()
  }

  def start() = {
    val config : Config = ConfigFactory.load()
    val system : ActorSystem = ActorSystem("ClusterTestSystem", config)
      val hazelcast = initHazelcast()

    ClusterSharding(system)
      .startProxy(typeName = PlayerSharding.shardName,
        role = Option(PlayerSharding.roleName),
        extractEntityId = PlayerSharding.idExtractor,
        extractShardId = PlayerSharding.shardResolver)

    ClusterSharding(system)
      .startProxy(typeName = TableSharding.shardName,
        role = Option(TableSharding.roleName),
        extractEntityId = TableSharding.idExtractor,
        extractShardId = TableSharding.shardResolver)

    ClusterSharding(system)
      .startProxy(typeName = GameSharding.shardName,
        role = Option(GameSharding.roleName),
        extractEntityId = GameSharding.idExtractor,
        extractShardId = GameSharding.shardResolver)

      system.registerOnTermination {
          hazelcast.shutdown()
      }

    system.actorOf(Props[ManagerService], name = "ManagerService")
  }

  def stop() = {

  }

    private def initHazelcast(): HazelcastInstance = {
        val cfg = new ClasspathXmlConfig("hazelcast.xml")
                .setInstanceName("hazelcast")
                .setProperty("hazelcast.logging.type", "slf4j")
                .setProperty("hazelcast.shutdownhook.enabled", "false")

        val instance: HazelcastInstance = Hazelcast.newHazelcastInstance(cfg)
        instance
    }
}
