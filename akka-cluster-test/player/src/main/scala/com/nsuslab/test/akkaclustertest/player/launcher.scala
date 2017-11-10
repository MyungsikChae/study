package com.nsuslab.test.akkaclustertest.player

import akka.actor.{ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import com.nsuslab.test.akkaclustertest.common.message.EntityGracefulShutDownMessage
import com.nsuslab.test.akkaclustertest.common.shard.{CustomPlayerShardAllocationStrategy, PlayerSharding, TableSharding}
import com.nsuslab.test.akkaclustertest.player.service.PlayerService
import com.nsuslab.test.akkaclustertest.player.worker.PlayerEntity
import com.typesafe.config.{Config, ConfigFactory}

object launcher {
  def main(arg: Array[String]): Unit = {
    start()
  }

  def start() = {
    val config : Config = ConfigFactory.load()
    val system : ActorSystem = ActorSystem("ClusterTestSystem", config)
//    val hazelcast = HazelcastExtension.get(system).hazelcast

    ClusterSharding(system)
      .startProxy(typeName = TableSharding.shardName,
        role = Option(TableSharding.roleName),
        extractEntityId = TableSharding.idExtractor,
        extractShardId = TableSharding.shardResolver)

    ClusterSharding(system)
      .start(typeName = PlayerSharding.shardName,
        entityProps = Props[PlayerEntity],
        settings = ClusterShardingSettings(system),
        extractEntityId = PlayerSharding.idExtractor,
        extractShardId = PlayerSharding.shardResolver,
        allocationStrategy = new CustomPlayerShardAllocationStrategy(2, 1),
        handOffStopMessage = EntityGracefulShutDownMessage)

    system.actorOf(Props[PlayerService], name = "PlayerService")

    system.registerOnTermination {
//      hazelcast.shutdown()
    }
  }

  def stop() = {

  }
}
