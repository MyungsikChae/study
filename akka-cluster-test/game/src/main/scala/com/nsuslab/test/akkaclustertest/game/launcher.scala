package com.nsuslab.test.akkaclustertest.game

import java.util.Date

import akka.actor.{ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import com.nsuslab.test.akkaclustertest.common.shard.{GameSharding, TableSharding}
import com.nsuslab.test.akkaclustertest.game.service.GameService
import com.nsuslab.test.akkaclustertest.game.worker.GameEntity
import com.typesafe.config.{Config, ConfigFactory}
import com.hazelcast.config.{ClasspathXmlConfig, Config => HZConfig}
import com.hazelcast.core.{Hazelcast, HazelcastInstance, IMap, IQueue}
import com.nsuslab.test.akkaclustertest.common.database.entity.{GameDataInfo, GameLaunchInfo}
import com.nsuslab.test.akkaclustertest.common.database.jpa.JpaHelper
import com.nsuslab.test.akkaclustertest.common.message.CompleteCreatingPlayerMessage

object launcher {
    def main(arg: Array[String]): Unit = {
        start()
    }

    def start() = {
        val config : Config = ConfigFactory.load()
        val system : ActorSystem = ActorSystem("ClusterTestSystem", config)
        //val hazelcast = HazelcastExtension.get(system).hazelcast

        val hazelcast = initHazelcast()

        startRecordJPAEntity(system)

        ClusterSharding(system)
                .startProxy(typeName = TableSharding.shardName,
                    role = Option(TableSharding.roleName),
                    extractEntityId = TableSharding.idExtractor,
                    extractShardId = TableSharding.shardResolver)

        ClusterSharding(system)
                .start(typeName = GameSharding.shardName,
                    entityProps = Props[GameEntity],
                    settings = ClusterShardingSettings(system),
                    extractEntityId = GameSharding.idExtractor,
                    extractShardId = GameSharding.shardResolver)

        system.actorOf(Props[GameService], name = "GameService")

        system.registerOnTermination {
            hazelcast.shutdown()
        }
    }

    def stop() = {

    }

    private def initHazelcast(): HazelcastInstance = {
        val cfg = new ClasspathXmlConfig("hazelcast.xml")
                .setInstanceName("hazelcast")
                .setProperty("hazelcast.logging.type", "slf4j")
                .setProperty("hazelcast.shutdownhook.enabled", "false")

        val instance: HazelcastInstance = Hazelcast.newHazelcastInstance(cfg)

        val mapTester: IMap[Long, GameDataInfo] = instance.getMap("GAMEDATA_INFO")

//        mapTester.delete(1509007950149L)
//        mapTester.delete(1509007950150L)

//        val a = new GameDataInfo()
//        val b = new GameDataInfo()
//
//        a.setGameId(1509007950149L)
//        a.setGameName("Simple counting game")
//        a.setGameType("simple-increase")
//        a.setMaxCount(41)
//        a.setPlayerCount(4)
//
//        b.setGameId(1509007950150L)
//        b.setGameName("Baskin robbins thirty-one game")
//        b.setGameType("thirty-one")
//        b.setMaxCount(31)
//        b.setPlayerCount(6)
//
//        mapTester.put(a.getGameId, a)
//        mapTester.put(b.getGameId, b)

        println(mapTester.values())

        instance
    }

    private def startRecordJPAEntity(system: ActorSystem): Unit = {
        val em = JpaHelper.createSession
        val tx = em.getTransaction
        val host = system.settings.config.getString("akka.remote.netty.tcp.hostname")
        val port = system.settings.config.getString("akka.remote.netty.tcp.port")

        val startTime = new Date().getTime

        try {
            tx.begin()
            val entity = new GameLaunchInfo(startTime)
            entity.setGameName("Mason-Game")
            entity.setGameType("test_game")
            entity.setLocation(system.name+s"-$host:$port")
            em.persist(entity)
            tx.commit()
        } catch {
            case _ : Throwable => tx.rollback()
        } finally {
            em.close()
        }
    }

}
