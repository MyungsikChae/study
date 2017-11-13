package com.nsuslab.test.akkaclustertest.table.service

import akka.cluster.Cluster
import akka.actor.{Actor, ActorLogging}
import akka.cluster.ClusterEvent._
import com.nsuslab.test.akkaclustertest.common.message._

import scala.concurrent.ExecutionContext

class TableService extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val system = context.system
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
    case TableShutDownMessage =>
      log.info(" --- Receive a message: TableShutDownMessage")
      sender() ! TableShutDownMessage
    case msg =>
      log.warning(" --- Receive a unknown message: {} ", msg)
  }
}

object TableService {
  def apply: TableService = { new TableService() }
}