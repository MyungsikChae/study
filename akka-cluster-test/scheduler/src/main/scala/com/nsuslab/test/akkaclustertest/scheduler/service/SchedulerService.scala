package com.nsuslab.test.akkaclustertest.scheduler.service

import scala.concurrent.duration._
import akka.cluster.Cluster
import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.cluster.ClusterEvent._
import com.hazelcast.core.Hazelcast
import com.nsuslab.test.akkaclustertest.common.message._
import java.lang.management.{ManagementFactory, MemoryMXBean}
import javax.management.ObjectName
import javax.management.remote.{JMXConnectorFactory, JMXServiceURL}

import scala.concurrent.ExecutionContext

class SchedulerService extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val system: ActorSystem = context.system
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
      import scala.collection.JavaConverters._

      log.info(" *** Receive a message: {}", msg)
      val jmxPeer = "127.0.0.1:5011"
      val jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://%s/jmxrmi".format(jmxPeer))

      val aaa = Map[String, AnyRef](javax.management.remote.JMXConnector.CREDENTIALS -> Array[String]("admin","q1w2e3"))

      val jmxc = JMXConnectorFactory.connect(jmxUrl, aaa.asJava)
      val connection = jmxc.getMBeanServerConnection

      println("\nDomains:")
      val domains = connection.getDomains
      for (domain <- domains) {
        println("\tDomain = " + domain)
      }

      println("\nMBeanServer default domain = " + connection.getDefaultDomain)

      println("\nMBean count = " + connection.getMBeanCount)
      println("\nQuery MBeanServer MBeans:")

      val names = connection.queryNames(null, null)

      for (name <- names.toArray) {
        println("\tObjectName = " + name.toString)
      }
//      val objName: ObjectName = new ObjectName("akkaclustertest.tables", {
//        import scala.collection.JavaConverters._
//        new java.util.Hashtable(
//          Map(
//            "name" -> "TableActor",
//            "type" -> "TS1509007950150-TE1"
//          ).asJava
//        )
//      })
//      println(" ** " + connection.getMBeanInfo(objName))
//      println(" ** " + connection.invoke(objName, "GetActorPath", null, null))

      val memProxy = ManagementFactory.newPlatformMXBeanProxy(connection, ManagementFactory.MEMORY_MXBEAN_NAME, classOf[MemoryMXBean])
      println("\n *** %s: %s\n".format(jmxPeer, memProxy.getHeapMemoryUsage))
      jmxc.close()

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

      context.system.scheduler.scheduleOnce(10.second, self, StartSystem())
    case msg =>
      log.warning(" *** Receive a unknown message: {} ", msg)
  }
}

object SchedulerService {
  def apply() = { new SchedulerService() }
}
