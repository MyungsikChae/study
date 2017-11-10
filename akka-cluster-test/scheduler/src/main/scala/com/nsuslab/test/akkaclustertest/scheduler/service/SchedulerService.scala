package com.nsuslab.test.akkaclustertest.scheduler.service

import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL

import akka.cluster.Cluster
import akka.actor.{Actor, ActorLogging}
import akka.cluster.ClusterEvent._
import akka.cluster.sharding.ClusterSharding
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.hazelcast.core.{Hazelcast, IMap}
import com.nsuslab.test.akkaclustertest.common.database.entity.GameDataInfo
import com.nsuslab.test.akkaclustertest.common.message._
import com.nsuslab.test.akkaclustertest.common.shard.GameSharding

import scala.concurrent.ExecutionContext

class SchedulerService extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val system = context.system
  val cluster = Cluster(system)

  val hazelcast = Hazelcast.getHazelcastInstanceByName("hazelcast")

//  system.actorOf(
//    ClusterSingletonManager.props(
//      singletonProps = Props(classOf[Consumer], queue, testActor),
//      terminationMessage = End,
//      settings = ClusterSingletonManagerSettings(system).withRole("worker")),
//    name = "consumer")
//
//  val proxy = system.actorOf(
//    ClusterSingletonProxy.props(
//      singletonManagerPath = "/user/consumer",
//      settings = ClusterSingletonProxySettings(system).withRole("worker")),
//    name = "consumerProxy")

  cluster registerOnMemberUp {
    // Do something when this node become a member-up in a cluster
    self ! StartSystem()
  }

  cluster registerOnMemberRemoved {
    // Do something when this node is detached from a cluster
  }

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberUp], classOf[MemberRemoved], classOf[UnreachableMember])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  override def receive: Receive = {
    case MemberUp(member) =>
      log.info(" *** Member is Up: {}", member.address)
    case UnreachableMember(member) =>
      log.info(" *** Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info(" *** Member is Removed: {} after {}", member.address, previousStatus)
    case evt: MemberEvent =>
      log.info(" *** MemberEvent: {}", evt)
    case msg : StartSystem =>
      log.info(" *** Receive a message: {}", msg)
//      val shardRegion = ClusterSharding(context.system).shardRegion(GameSharding.shardName)
//      val gameInfo: IMap[Long, GameDataInfo] = hazelcast.getMap("GAMEDATA_INFO")
//
//      println("  ----  " + gameInfo.values().size())
//
//      gameInfo.values().forEach {
//        item =>
//          println(s"--------${item.getGameId}")
//          shardRegion ! GameEnvelopeMessage(item.getGameId, item.getGameId,
//                            CreateGameMessage(item.getGameId, item.getGameType, item.getMaxCount, item.getPlayerCount))
//      }
    case msg =>
      log.warning(" *** Receive a unknown message: {} ", msg)
  }
}

object SchedulerService {
  def apply() = { new SchedulerService() }
}
