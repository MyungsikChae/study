package com.nsuslab.test.akkaclustertest.table.handler

import akka.actor.PoisonPill

import scala.concurrent.duration._
import com.nsuslab.test.akkaclustertest.common.message._
import com.nsuslab.test.akkaclustertest.table.worker.TableActor

trait InitialHandler {
    this: TableActor =>
    import context.dispatcher
    import TableActor._

    def initialHandler: Receive = {
        case msg: CommandMessage =>
            log.debug(" *** Receive CommandMessage : {}", msg)
            logPrint(s"    initializing game ......")
            val senderActorRef = sender()

            msg match {
                case msg: TerminateTableMessage =>
                    updateInternalDate (internalData.copy (tableState = WaitingForShutdown))

                case message : CreateTableMessage =>
                    updateInternalDate(
                        internalData.copy(
                            gameId = message.gameId,
                            tableId = message.tableId,
                            maxCount = message.maxCount,
                            playerCount = message.playerCount))

                    for (index <- 1 to message.playerCount) {
                        playerRegion ! PlayerEnvelopeMessage(message.gameId*10 + message.tableId, index,
                                        CreatePlayerMessage(message.gameId, message.tableId, index, message.maxCount, message.gameMode))
                        updateInternalDate(
                            internalData.copy(
                                playerList = PlayerIdentifier(message.gameId, message.tableId, index, Creating) +: internalData.playerList))
                    }

                    senderActorRef ! CompleteCreatingTableMessage(message.gameId, message.tableId)
            }

        case msg: EventMessage =>
            log.debug(" *** Receive EventMessage : {}", msg)
            msg match {
                case msg: CompleteCreatingPlayerMessage =>
                    updateInternalDate(internalData.copy(playerList = internalData.playerList.map {
                        identifier =>
                            if (identifier.gameId == msg.gameId && identifier.tableId == msg.tableId && identifier.playerId == msg.playerId)
                                identifier.copy(state = Created)
                            else
                                identifier
                    }))
                    internalData.tableState match {
                        case Initializing =>
                            if (internalData.playerList.count(_.state == Created) == internalData.playerCount) {
                                logPrint(s"    initialized game !!!")
                                updateInternalDate(internalData.copy(tableState = Idle))
                                context.become(gamePlayReceive)
                                context.system.scheduler.scheduleOnce(2.second, self, GameStart)
                            }
                            saveSingleSnapshot(internalData)
                        case WaitingForUpgrade =>
                            if (internalData.playerList.count(_.state == Created) == internalData.playerCount) {
                                logPrint(s"    shutdown game !!!")
                                updateInternalDate(internalData.copy(tableState = ReadyForUpgrade))
                            }
                            context.parent ! ReadyForUpgradeMessage
                        case WaitingForShutdown =>
                            if (internalData.playerList.count(_.state == Created) == internalData.playerCount) {
                                logPrint(s"    shutdown game !!!")
                                updateInternalDate(internalData.copy(tableState = ReadyForShutdown))
                            }
                            preShutdownTable()
                            self ! PoisonPill
                        case _ =>
                    }
            }
    }
}
