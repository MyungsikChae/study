package com.nsuslab.test.akkaclustertest.manager.service

import javax.management.ObjectName
import javax.management.remote.{JMXConnectorFactory, JMXServiceURL}

import akka.cluster.Cluster
import akka.actor.{Actor, ActorLogging}
import akka.cluster.ClusterEvent._
import com.hazelcast.core.Hazelcast
import com.nsuslab.test.akkaclustertest.common.jmx.AkkaJmxRegister.{registerToMBeanServer, unregisterFromMBeanServer}
import com.nsuslab.test.akkaclustertest.common.jmx.JMXMBeanManagerObject
import com.nsuslab.test.akkaclustertest.common.message.StartSystem

import scala.concurrent.ExecutionContext

class ManagerService extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val system = context.system
  val cluster = Cluster(system)

  val hazelcast = Hazelcast.getHazelcastInstanceByName("hazelcast")
  val dataObject: JMXMBeanManagerObject = JMXMBeanManagerObject(self.path.parent.name+"/"+self.path.name, this.getClass.getSimpleName)

  protected val objName: ObjectName = new ObjectName("akkaclustertest.manager", {
    import scala.collection.JavaConverters._
    new java.util.Hashtable(
      Map(
        "name" -> self.path.name,
        "type" -> self.path.parent.name
      ).asJava
    )
  })

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
    super.preStart()
    registerToMBeanServer(dataObject, objName)
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberUp], classOf[MemberRemoved], classOf[UnreachableMember])
  }

  override def postStop(): Unit = {
    super.postStop()
    unregisterFromMBeanServer(objName)
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

    case StartSystem =>

    case msg =>
      log.warning(" *** Receive a unknown message: {} ", msg)
  }
}

object ManagerService {
  def apply() = { new ManagerService() }
}