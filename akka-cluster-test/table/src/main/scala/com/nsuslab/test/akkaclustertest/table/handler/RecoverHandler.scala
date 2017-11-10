package com.nsuslab.test.akkaclustertest.table.handler

import scala.concurrent.duration._
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import com.nsuslab.test.akkaclustertest.common.message.{CommandMessage, CreateTableMessage, GameStart}
import com.nsuslab.test.akkaclustertest.table.worker.TableActor
import com.nsuslab.test.akkaclustertest.table.worker.TableActor.{Idle, ReadyForShutdown, TableData}

trait RecoverHandler {
    this: TableActor =>
    import context.dispatcher

    override def receiveRecover: Receive = {
        case cmd: CommandMessage =>
            log.info(" -***- [Recover] Receive a {}", cmd)
            cmd match {
                case message : CreateTableMessage =>
                    updateInternalDate(internalData.copy(gameId = message.gameId, tableId = message.tableId))
                case _ =>
                    log.warning(" -***- [Recover] Receive a unknown message : {}", cmd)
            }

        case SnapshotOffer(_, snapshot: TableData) =>
            log.info(" -***- [Recover] Receive a {}", snapshot)
            updateInternalDate(snapshot)

        case RecoveryCompleted =>
            log.info(" -***- [Recover] Receive a RecoveryCompleted")
            if (internalData.gameId !=0 && internalData.tableId !=0 && internalData.playerList.nonEmpty) {
                context.become(gamePlayReceive)
                if (internalData.lastNumber == 0 && internalData.playerTurn == 0) {
                    updateInternalDate(internalData.copy(tableState = Idle))
                    context.system.scheduler.scheduleOnce(2.second, self, GameStart)
                } else {
                    //          val playerTurn = (internalData.playerTurn % playerCount) + 1
                    //          for (index <- internalData.playerList) {
                    //            playerRegion ! PlayerEnvelopeMessage(index.gameId + index.tableId, index.playerId, WhoTurn(playerTurn, internalData.lastNumber))
                    //          }
                }
            }

        case msg =>
            log.warning(" -***- [Recover] Receive a unknown message : {}", msg)
    }

}
