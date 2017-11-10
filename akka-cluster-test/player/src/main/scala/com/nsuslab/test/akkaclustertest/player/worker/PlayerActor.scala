package com.nsuslab.test.akkaclustertest.player.worker

import javax.management.ObjectName

import scala.concurrent.duration._
import akka.actor.{ActorLogging, ActorRef, Cancellable, PoisonPill}
import akka.cluster.sharding.ClusterSharding
import akka.persistence.SnapshotSelectionCriteria
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import akka.util.Timeout
import com.nsuslab.test.akkaclustertest.common.message._
import com.nsuslab.test.akkaclustertest.common.jmx.AkkaJmxRegister.{registerToMBeanServer, unregisterFromMBeanServer}
import com.nsuslab.test.akkaclustertest.common.jmx.JMXMBeanDataObject
import com.nsuslab.test.akkaclustertest.common.shard.TableSharding
import com.nsuslab.test.akkaclustertest.player.handler.{CommonHandler, IdleHandler, MyTurnHandler, WaitForMyTurnHandler}
import com.nsuslab.test.akkaclustertest.player.worker.PlayerActor.{PlayerData, PlayerEvent, PlayerState}

import scala.reflect.{ClassTag, classTag}

/* Issue :
 * 1. One of Table nodes was killed by kill -9 command. In this case, Each parts of cluster couldn't handle
 *    process like graceful-shutdown. Then some messages or events don't be buffered by cluster system.
 *
 * Checked Point
 * 1. If you define a overridden receiveRecover method, in case of recovery state,
 *    events are re-presented though a overridden receiveRecover method. not applyEvent
 * 2. If you persist a event with persist() method and don't define a overridden receiveRecover method,
 *    events are re-presented though a overridden applyEvent method.
 */

class PlayerActor extends PersistentFSM[PlayerState, PlayerData, PlayerEvent]
        with IdleHandler
        with WaitForMyTurnHandler
        with MyTurnHandler
        with CommonHandler
        with ActorLogging {

  import PlayerActor._
  import context.dispatcher
  import akka.pattern._

  implicit val timeout = Timeout(10.second)

  var chatScheduler: Cancellable = _

  val tableRegion: ActorRef = ClusterSharding(context.system).shardRegion(TableSharding.shardName)

  val enableChat: Boolean = false //context.system.settings.config.getBoolean("player-mode.enable-chat")

  val intervalChat: Long = 0L //context.system.settings.config.getLong("player-mode.interval-chat")

  override def persistenceId: String = self.path.parent.name + "-" + self.path.name

  override def domainEventClassTag: ClassTag[PlayerEvent] = classTag[PlayerEvent]

  startWith(Idle, EmptyPlayerData)

  val dataObject: JMXMBeanDataObject =
    JMXMBeanDataObject(self.path.parent.name+"/"+self.path.name, this.getClass.getSimpleName, stateName.toString, stateData.toString)

  protected val objName: ObjectName = new ObjectName("akkaclustertest.players", {
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
    log.info(s" *** preStart!! $stateName /$stateData")
    registerToMBeanServer(dataObject, objName)
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info(" *** postStop!!")
    if (chatScheduler != null) chatScheduler.cancel()
    unregisterFromMBeanServer(objName)
  }

  override def applyEvent(domainEvent: PlayerEvent, currentData: PlayerData): PlayerData = {
    log.info(" *** applyEvent : {} : [{} / {}]", domainEvent, stateName, stateData)
    domainEvent match {
      case msg: SetSequence =>
        currentData.setSequence(msg.seq, msg.maxCount, msg.gameMode).setGameAndTableId(msg.gameId, msg.tableId)
      case msg: LastNumber =>
        context.system.scheduler.scheduleOnce(1.second, self, SelectNumber(msg.lastNumber))
        currentData
      case AddLoseCount =>
        currentData.increase()
      case TestEvent =>
        currentData
    }
  }

  def logPrint(msg: String): Unit = {
    val flag = true
    if (flag) log.info(msg)
    else println(msg)
  }

  def preShutdownPlayer(): Unit = {
    context.parent ! CompleteTerminatingPlayerMessage(stateData.getGameId(), stateData.getTableId(), stateData.getSequence())
    self ! PoisonPill
  }

  def unHandledMessageHandler: StateFunction = {
    case Event(msg, _) =>
      log.warning(" *** Receive a unknown message: {} : [{} / {}]", msg, stateName, stateData)
      stay()
  }

  onTransition {
    case before -> after =>
      dataObject.stateName = after.toString
      dataObject.stateData = stateData.toString
      log.debug(" *** OnTransition changing state : {} -> {} : {}", before, after, stateData)
      if ((before == WaitForMyTurn || before == MyTurn) && after == Idle) {
        // --------------------------
        //tableRegion ! TableEnvelopeMessage(stateData.getGameId(), stateData.getTableId(), PlayerTerminateMessage(stateData.getSequence()))
        //context.stop(self)
        //self ! PoisonPill
        // --------------------------
        //context.parent ! Passivate(PoisonPill)
      }
  }

    when(Idle)(unHandledMessageHandler)
    when(WaitForMyTurn)(unHandledMessageHandler)
    when(MyTurn)(unHandledMessageHandler)

//  override def receiveRecover: Receive = {
//    case msg =>
//      logPrint(s" -***- [Recovery] receive a massage : $msg")
//  }

  override def loadSnapshot(persistenceId: String, criteria: SnapshotSelectionCriteria, toSequenceNr: Long): Unit = {
    super.loadSnapshot(persistenceId, criteria, toSequenceNr)
    log.debug(" *** loadSnapshot: {} : {} : {}", persistenceId, criteria, toSequenceNr)
  }

  override def onRecoveryCompleted(): Unit = {
    super.onRecoveryCompleted()
    log.debug(" *** onRecoveryCompleted: {} : {}", stateName, stateData)
  }

  override protected def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
    super.onRecoveryFailure(cause, event)
  }

}

object PlayerActor {
  trait PlayerData {
    def increase(): PlayerData
    def setSequence(seq: Int, maxCount: Int, gameMode: String): PlayerData
    def setGameAndTableId(gameId: Long, tableId: Int): PlayerData
    def empty(): PlayerData
    def getSequence(): Int
    def getMaxCount(): Int
    def getGameId(): Long
    def getTableId(): Int
    def getGameMode(): String
  }
  case object EmptyPlayerData extends PlayerData {
    def increase() = NonEmptyPlayerData(0, 0, "", 0, 0, 1)
    def setSequence(seq: Int, maxCount: Int, gameMode: String) = NonEmptyPlayerData(seq, maxCount, gameMode, 0, 0, 0)
    def setGameAndTableId(gameId: Long, tableId: Int) = NonEmptyPlayerData(0, 0, "", gameId, tableId, 0)
    def empty() = this
    def getSequence() = 0
    def getMaxCount() = 0
    def getGameId() = 0
    def getTableId() = 0
    def getGameMode() = ""
  }
  case class NonEmptyPlayerData(seq: Int, maxCount: Int, gameMode: String, gameId: Long, tableId: Int, loseCount: Int) extends PlayerData {
    def increase() = this.copy(loseCount = this.loseCount + 1)
    def setSequence(seq: Int, maxCount: Int, gameMode: String) = this.copy(seq = seq, maxCount = maxCount, gameMode = gameMode)
    def setGameAndTableId(gameId: Long, tableId: Int) = this.copy(gameId = gameId, tableId = tableId)
    def empty() = EmptyPlayerData
    def getSequence() = this.seq
    def getMaxCount() = this.maxCount
    def getGameId() = this.gameId
    def getTableId() = this.tableId
    def getGameMode() = this.gameMode
  }

  trait PlayerState extends FSMState
  case object Idle extends PlayerState {
    override def identifier: String = "Idle"
  }
  case object WaitForMyTurn extends PlayerState {
    override def identifier: String = "WaitForMyTurn"
  }
  case object MyTurn extends PlayerState {
    override def identifier: String = "MyTurn"
  }

  trait PlayerEvent
  case class SetSequence(seq: Int, maxCount: Int, gameMode: String, gameId: Long, tableId: Int) extends PlayerEvent
  case class LastNumber(lastNumber: Int) extends PlayerEvent
  case object AddLoseCount extends PlayerEvent
  case object TestEvent extends PlayerEvent

  trait InternalEvent
  case object Tick extends InternalEvent

  def apply: PlayerActor = new PlayerActor()
}
