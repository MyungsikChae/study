package com.nsuslab.test.akkaclustertest.game.worker

import javax.management.ObjectName

import akka.actor.{ActorLogging, ActorRef, PoisonPill}
import akka.cluster.sharding.ClusterSharding
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer, SnapshotSelectionCriteria}
import com.hazelcast.core.{Hazelcast, IMap}
import com.nsuslab.test.akkaclustertest.common.database.entity.GameDataInfo
import com.nsuslab.test.akkaclustertest.common.jmx.AkkaJmxRegister.{registerToMBeanServer, unregisterFromMBeanServer}
import com.nsuslab.test.akkaclustertest.common.jmx.JMXMBeanDataObject
import com.nsuslab.test.akkaclustertest.common.message._
import com.nsuslab.test.akkaclustertest.common.shard.TableSharding
import com.nsuslab.test.akkaclustertest.game.worker.GameActor._

class GameActor extends PersistentActor with ActorLogging {
  override def persistenceId: String = self.path.parent.name + "-" + self.path.name

  val hazelcast = Hazelcast.getHazelcastInstanceByName("hazelcast")

  val gameInfo: IMap[Long, GameDataInfo] = hazelcast.getMap("GAMEDATA_INFO")

  val tableRegion: ActorRef = ClusterSharding(context.system).shardRegion(TableSharding.shardName)

  var internalData = GameData(0)

  var dataObject: JMXMBeanDataObject =
    JMXMBeanDataObject(self.path.toStringWithoutAddress, this.getClass.getSimpleName, "Idle", internalData.toString)

  protected val objName: ObjectName = new ObjectName("akkaclustertest.games", {
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

  def preShutdownTable(): Unit ={
    context.parent ! CompleteTerminatingGameMessage(internalData.gameId)
    deleteSnapshots(SnapshotSelectionCriteria.Latest)
    self ! PoisonPill
  }

  private def saveSingleSnapshot(snapshot: Any): Unit = {
//    deleteSnapshots(SnapshotSelectionCriteria.Latest)
    saveSnapshot(snapshot)
  }

  def receiveCommand: Receive = {
    case msg: CommandMessage =>
      log.info(" *** Receive CommandMessage : {}", msg)
      val senderActorRef = sender()

      msg match {
        case message: CreateGameMessage =>
          val gameDataInfo = gameInfo.get(message.gameId)
          if (gameDataInfo != null) {
            internalData = internalData.copy(gameId = message.gameId)
            dataObject.objName = dataObject.objName + ":" + gameDataInfo.gameType
            for (index <- 1 to 2) {
              tableRegion ! TableEnvelopeMessage(message.gameId, index,
                CreateTableMessage(message.gameId, index, gameDataInfo.gameType, gameDataInfo.maxCount, gameDataInfo.playerCount))
              internalData = internalData.copy(tableList = TableIdentifier(message.gameId, index, Creating) +: internalData.tableList)
            }
            saveSingleSnapshot(internalData)
            senderActorRef ! CompleteCreatingGameMessage(message.gameId)
          }

        case message: TerminateGameMessage =>
          if (internalData.gameId == message.gameId) {
            internalData.tableList foreach { table =>
              table.state match {
                case Created | Creating =>
                  tableRegion ! TableEnvelopeMessage(table.gameId, table.tableId, TerminateTableMessage(table.gameId, table.tableId))
                  internalData = internalData.copy(tableList = internalData.tableList.map {
                    identifier =>
                      if(identifier.gameId == table.gameId && identifier.tableId == table.tableId)
                        identifier.copy(state = Terminating)
                      else
                        identifier
                  })
                case Terminating | Terminated =>
                  // Do Nothing
              }
            }
            preShutdownTable()
          }

        case _ =>
          log.warning(" *** Receive a unknown CommandMessage: {}", msg)
      }

    case msg: EventMessage =>
      log.info(" *** Receive EventMessage : {} : {}", msg, sender())

      msg match {
        case message: CompleteCreatingTableMessage =>
          internalData = internalData.copy(tableList = internalData.tableList.map {
            identifier =>
              if(identifier.gameId == message.gameId && identifier.tableId == message.tableId)
                identifier.copy(state = Created)
              else
                identifier
          })
          saveSingleSnapshot(internalData)

        case _ =>
          log.warning(" *** Receive a unknown EventMessage: {}", msg)
      }

    case msg =>
      log.warning(" *** Receive a unknown message: {}", msg)
  }

  override def receiveRecover: Receive = {
    case cmd: CommandMessage =>
      log.info(" -***- [Recover] Receive a {}", cmd)
      cmd match {
        case message : CreateGameMessage =>
          internalData = internalData.copy(gameId = message.gameId)
        case _ =>
          log.warning(" -***- [Recover] Receive a unknown message : {}", cmd)
      }

    case SnapshotOffer(_, snapshot: GameData) =>
      log.info(" -***- [Recover] Receive a {}", snapshot)
      internalData = snapshot

    case RecoveryCompleted =>
      log.info(" -***- [Recover] Receive a RecoveryCompleted")

    case msg =>
      log.warning(" -***- [Recover] Receive a unknown message : {}", msg)

  }

  override protected def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
    super.onRecoveryFailure(cause, event)
  }
}

object GameActor {
  trait ActorState
  case object Creating extends ActorState
  case object Created extends ActorState

  case object Terminating extends ActorState
  case object Terminated extends ActorState

  case class TableIdentifier(gameId: Long, tableId: Int, state: ActorState)
  case class GameData(gameId: Long, tableList: List[TableIdentifier] = Nil)

  def apply()= {
    new GameActor()
  }
}