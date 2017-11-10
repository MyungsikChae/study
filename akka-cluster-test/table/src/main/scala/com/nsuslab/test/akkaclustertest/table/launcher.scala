package com.nsuslab.test.akkaclustertest.table

import scala.concurrent.duration._
import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.util.Timeout
import com.hazelcast.config.ClasspathXmlConfig
import com.hazelcast.core.Hazelcast
import com.nsuslab.test.akkaclustertest.common.message._
import com.nsuslab.test.akkaclustertest.common.shard.{CustomTableShardAllocationStrategy, PlayerSharding, TableSharding}
import com.nsuslab.test.akkaclustertest.table.service.TableService
import com.nsuslab.test.akkaclustertest.table.worker.TableEntity
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{ExecutionContext, Future}

object launcher {
    def main(arg: Array[String]): Unit = {
        start()
    }

    def start() = {
        import akka.pattern.ask
        import system.dispatcher

        val config : Config = ConfigFactory.load()
        val system : ActorSystem = ActorSystem("ClusterTestSystem", config)
        //    val hazelcast = HazelcastExtension.get(system).hazelcast
        val hazelcast = startHazelcast(system)

        ClusterSharding(system)
                .startProxy(typeName = PlayerSharding.shardName,
                    role = Option(PlayerSharding.roleName),
                    extractEntityId = PlayerSharding.idExtractor,
                    extractShardId = PlayerSharding.shardResolver)

        ClusterSharding(system)
                .start(typeName = TableSharding.shardName,
                    entityProps = Props[TableEntity],
                    settings = ClusterShardingSettings(system),
                    extractEntityId = TableSharding.idExtractor,
                    extractShardId = TableSharding.shardResolver,
                    allocationStrategy = new CustomTableShardAllocationStrategy(2, 1),
                    handOffStopMessage = EntityGracefulShutDownMessage)

        CoordinatedShutdown.get(system).addJvmShutdownHook(
            () -> System.out.println("custom JVM shutdown hook...")
        )

        val tableService = system.actorOf(Props[TableService], name = "TableService")

        CoordinatedShutdown(system).addTask(
            CoordinatedShutdown.PhaseBeforeServiceUnbind, "Table Node Shutdown Pre-process 1") { () =>
            implicit val timeout: Timeout = Timeout(5.seconds)
            ( tableService ? TableShutDownMessage).map(_ => Done)
        }

        CoordinatedShutdown(system).addTask(
            CoordinatedShutdown.PhaseBeforeServiceUnbind, "Table Node Shutdown Pre-process 2") { () =>
            implicit val timeout: Timeout = Timeout(5.seconds)
            ( tableService ? TableShutDownMessage).map(_ => Done)
        }

        system.registerOnTermination {
            hazelcast.shutdown()
        }
    }

    def stop() = {

    }

    private def startHazelcast(system: ActorSystem) = {
        val conf = new ClasspathXmlConfig("hazelcast.xml")
                .setInstanceName("hazelcast")
                .setProperty("hazelcast.logging.type", "slf4j")
                .setProperty("hazelcast.shutdownhook.enabled", "false")
        val hazelcast = Hazelcast.newHazelcastInstance(conf)

//        CoordinatedShutdown(system)
//                .addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "hazelcastDown")(() => {
//                    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
//                    Future {
//                        hazelcast.shutdown()
//                        akka.Done
//                    }
//                })
        hazelcast
    }

}
