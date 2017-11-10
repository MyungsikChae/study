package com.nsuslab.test.akkaclustertest.network.service

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import com.nsuslab.test.akkaclustertest.common.database.entity.GameDataInfo
import com.nsuslab.test.akkaclustertest.common.message.{CreateGameMessage, GameEnvelopeMessage, PlayerTerminateMessage, StartSystem}
import com.nsuslab.test.akkaclustertest.common.shard.GameSharding

import scala.concurrent.ExecutionContext

class NetworkService extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher

  val system = ActorSystem("ClusterTestSystem")
  val cluster = Cluster(context.system)

  val gameShardRegion: ActorRef = ClusterSharding(system)
    .startProxy(typeName = GameSharding.shardName,
      role = Option(GameSharding.roleName),
      extractEntityId = GameSharding.idExtractor,
      extractShardId = GameSharding.shardResolver)

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
  }
  override def postStop(): Unit = cluster.unsubscribe(self)


  override def receive: Receive = {
    case MemberUp(member) =>
      log.info(" *** Member is Up: {}", member.address)
    case UnreachableMember(member) =>
      log.info(" *** Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info(" *** Member is Removed: {} after {}", member.address, previousStatus)
    case _: MemberEvent => // ignore

    case _ : StartSystem =>
      println("********************************")
      println("********************************")
      println("********************************")
      gameShardRegion ! GameEnvelopeMessage(0, 0, CreateGameMessage(0, "", 0, 0))
    case msg =>
      log.warning(" Receive a unknown message: {} ", msg)
  }
}

object NetworkService {
  def apply: NetworkService = { new NetworkService() }
}