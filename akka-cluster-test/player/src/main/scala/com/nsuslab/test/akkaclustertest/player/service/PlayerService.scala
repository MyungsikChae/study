package com.nsuslab.test.akkaclustertest.player.service

import akka.cluster.Cluster
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.ClusterEvent._
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}

import scala.concurrent.ExecutionContext
import com.nsuslab.test.akkaclustertest.common.shard.PlayerSharding
import com.nsuslab.test.akkaclustertest.player.worker.PlayerActor

class PlayerService extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher

  val system = context.system
  val cluster = Cluster(system)

  cluster registerOnMemberUp {
    // Do something when this node become a member-up in a cluster
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
      log.info(" --- Member is Up: {}", member.address)
    case UnreachableMember(member) =>
      log.info(" --- Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info(" --- Member is Removed: {} after {}", member.address, previousStatus)
    case evt: MemberEvent =>
      log.info(" --- MemberEvent: {}", evt)
    case msg =>
      log.warning(" --- Receive a unknown message: {} ", msg)
  }
}

object PlayerService {
  def apply: PlayerService = { new PlayerService() }
}