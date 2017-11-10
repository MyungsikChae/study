package com.nsuslab.test.akkaclustertest.table.worker

import javax.management.ObjectName

import akka.actor.{ActorLogging, ActorRef, PoisonPill}
import akka.cluster.sharding.ClusterSharding
import akka.persistence.{PersistentActor, SnapshotSelectionCriteria}
import com.nsuslab.test.akkaclustertest.common.jmx.JMXMBeanDataObject
import com.nsuslab.test.akkaclustertest.common.jmx.AkkaJmxRegister.{registerToMBeanServer, unregisterFromMBeanServer}
import com.nsuslab.test.akkaclustertest.common.message._
import com.nsuslab.test.akkaclustertest.common.shard.PlayerSharding
import com.nsuslab.test.akkaclustertest.table.handler.{CommonHandler, GamePlayHandler, InitialHandler, RecoverHandler}

/* Issue :
 *
 *
 * Checked Point :
 * 1. If you want to recover table shard with table entity on another table node when table node was killed by kill -9,
 *    cluster must recognize a node-down as soon as possible. So you mustn't set `auto-down-unreachable-after` to `off`.
 *    If you set `auto-down-unreachable-after` to `off`, cluster will be waiting to join the node continuously.
 *
 */

class TableActor extends PersistentActor
        with CommonHandler
        with InitialHandler
        with GamePlayHandler
        with RecoverHandler
        with ActorLogging {
  import TableActor._

  override def persistenceId: String = self.path.parent.name + "-" + self.path.name

  val playerRegion: ActorRef = ClusterSharding(context.system).shardRegion(PlayerSharding.shardName)

  var internalData = TableData(0, 0, 0, 0, 0, 0, Initializing)

  val dataObject: JMXMBeanDataObject =
    JMXMBeanDataObject(self.path.parent.name+"/"+self.path.name, this.getClass.getSimpleName, Idle.toString, internalData.toString)

  protected val objName: ObjectName = new ObjectName("akkaclustertest.tables", {
    import scala.collection.JavaConverters._
    new java.util.Hashtable(
      Map(
        "name" -> self.path.name,
        "type" -> self.path.parent.name
      ).asJava
    )
  })

  override def preStart(): Unit = {
    super.preStart()
    log.info(s" *** preStart!! $internalData")
    registerToMBeanServer(dataObject, objName)
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info(" *** postStop!!")
    unregisterFromMBeanServer(objName)
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    log.info(s" *** preRestart!! reason : ${reason.getMessage}")
  }

  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
    log.info(s" *** postRestart!! reason : ${reason.getMessage}")
  }

  def logPrint(msg: String): Unit = {
    val flag = true
    if (flag) log.info(msg)
    else println(msg)
  }

  def updateInternalDate(data: TableData): Unit = {
    internalData = data
    dataObject.stateName = internalData.tableState.toString
    dataObject.stateData = internalData.toString
  }

  def preShutdownTable(): Unit ={
    context.parent ! CompleteTerminatingTableMessage(internalData.gameId, internalData.tableId)
    sendTerminateMessageToPlayer()
    deleteSnapshots(SnapshotSelectionCriteria.Latest)
    self ! PoisonPill
  }

  def saveSingleSnapshot(snapshot: Any): Unit = {
//    deleteSnapshots(SnapshotSelectionCriteria.Latest)
    saveSnapshot(snapshot)
  }

  def sendTerminateMessageToPlayer(): Unit = {
    for (index <- internalData.playerList) {
      playerRegion ! PlayerEnvelopeMessage(index.gameId*10 + index.tableId, index.playerId, TerminatePlayerMessage(index.gameId, index.tableId, index.playerId))
    }

  }

  def unknownMessageHandler: Receive = {
    case msg =>
      log.warning(" *** Receive a unknown message: {}", msg)
  }

  def initialReceive: Receive = initialHandler orElse commonMessageHandler orElse unknownMessageHandler

  def gamePlayReceive: Receive = gameHandler orElse commonMessageHandler orElse unknownMessageHandler

  def receiveCommand: Receive = initialReceive

}

object TableActor {

  trait ActorState
  case object Creating extends ActorState
  case object Created extends ActorState

  trait TableState
  case object Initializing extends TableState
  case object Idle extends TableState
  case object Running extends TableState

  case object WaitingForShutdown extends TableState
  case object ReadyForShutdown extends TableState

  case object WaitingForUpgrade extends TableState
  case object ReadyForUpgrade extends TableState

  case class PlayerIdentifier(
          gameId: Long,
          tableId: Int,
          playerId: Int,
          state: ActorState)

  case class TableData(
          gameId: Long,
          tableId: Int,
          playerTurn: Int,
          lastNumber: Int,
          maxCount: Int,
          playerCount: Int,
          tableState: TableState,
          playerList: List[PlayerIdentifier] = Nil)

  def apply(): TableActor = {
    new TableActor()
  }
}
