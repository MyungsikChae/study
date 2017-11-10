package com.nsuslab.test.akkaclustertest.table.handler

import akka.actor.PoisonPill

import scala.concurrent.duration._
import com.nsuslab.test.akkaclustertest.common.message._
import com.nsuslab.test.akkaclustertest.table.worker.TableActor

trait GamePlayHandler {
    this: TableActor =>
    import context.dispatcher
    import TableActor._

    def gameHandler: Receive = {
        case msg: CommandMessage =>
            log.debug(" *** Receive CommandMessage : {}", msg)

            msg match {
                case msg: TerminateTableMessage =>
                    internalData.tableState match {
                        case Idle =>
                            updateInternalDate (internalData.copy (tableState = ReadyForShutdown))
                        case Running =>
                            updateInternalDate (internalData.copy (tableState = WaitingForShutdown))
                        case _ =>
                    }

                case msg: ChooseNumber =>
                    internalData.tableState match {
                        case Idle =>
                        case Running | WaitingForShutdown | WaitingForUpgrade =>
                            logPrint(s"    **  [G${internalData.gameId}/T${internalData.tableId}] player-${msg.playerId} choose number(${msg.selectedNumber}) !!!")
                            if (msg.selectedNumber == internalData.maxCount) {
                                logPrint(s"    **  [G${internalData.gameId}/T${internalData.tableId}] Lose player-${msg.playerId} !!!")
                                for (index <- internalData.playerList) {
                                    playerRegion ! PlayerEnvelopeMessage(index.gameId*10 + index.tableId, index.playerId, WhoLose(msg.playerId))
                                }
                                self ! GameEnd
                            } else {
                                val playerTurn = (internalData.playerTurn % internalData.playerCount) + 1
                                updateInternalDate(internalData.copy(playerTurn = internalData.playerTurn + 1, lastNumber = msg.selectedNumber))
                                logPrint(s"    **  [G${internalData.gameId}/T${internalData.tableId}] Round Sequence-${internalData.playerTurn} Turn to player-$playerTurn !!!")
                                for (index <- internalData.playerList) {
                                    playerRegion ! PlayerEnvelopeMessage(index.gameId*10 + index.tableId, index.playerId, WhoTurn(playerTurn, internalData.lastNumber))
                                }
                            }
                            saveSingleSnapshot(internalData)
                            logPrint("    ---------------------------------------------------")
                        case _ =>
                    }
            }

        case msg: EventMessage =>
            log.debug(" *** Receive EventMessage : {}", msg)

            msg match {
                case GameStart =>
                    internalData.tableState match {
                        case Idle =>
                            logPrint(s"    **  [G${internalData.gameId}/T${internalData.tableId}] Game Start !!!")
                            updateInternalDate(internalData.copy(playerTurn = 0, lastNumber = 0, tableState = Running))
                            if (internalData.playerList.nonEmpty) {
                                for (index <- internalData.playerList) {
                                    playerRegion ! PlayerEnvelopeMessage(index.gameId*10 + index.tableId, index.playerId, GameStart)
                                }
                                context.system.scheduler.scheduleOnce(1.second, self, TurnStart)
                                logPrint("    ---------------------------------------------------")
                            } else {
                                logPrint("    ---------------------------------------------------")
                                logPrint("                DONE - There are no players")
                                logPrint("    ---------------------------------------------------")
                            }
                        case ReadyForUpgrade =>
                            saveSingleSnapshot(internalData)
                            context.parent ! ReadyForUpgradeMessage
                        case ReadyForShutdown =>
                            preShutdownTable()
                            self ! PoisonPill
                        case _ =>
                    }

                case TurnStart =>
                    internalData.tableState match {
                        case Running =>
                            val playerTurn = (internalData.playerTurn % internalData.playerCount) + 1
                            updateInternalDate(internalData.copy(playerTurn = playerTurn, lastNumber = 0))
                            for (index <- internalData.playerList) {
                                playerRegion ! PlayerEnvelopeMessage(index.gameId*10 + index.tableId, index.playerId, WhoTurn(playerTurn, internalData.lastNumber))
                            }
                        case _ =>
                    }

                case GameEnd =>
                    logPrint(s"    **  [G${internalData.gameId}/T${internalData.tableId}] Game End !!!")
                    for (index <- internalData.playerList) {
                        playerRegion ! PlayerEnvelopeMessage(index.gameId*10 + index.tableId, index.playerId, GameEnd)
                    }
                    internalData.tableState match {
                        case Running            =>
                            updateInternalDate(internalData.copy(playerTurn = 0, lastNumber = 0, tableState = Idle))
                            saveSingleSnapshot(internalData)
                            context.system.scheduler.scheduleOnce(2.second, self, GameStart)
                        case WaitingForUpgrade =>
                            updateInternalDate(internalData.copy(playerTurn = 0, lastNumber = 0, tableState = ReadyForShutdown))
                            saveSingleSnapshot(internalData)
                            context.parent ! ReadyForUpgradeMessage
                        case WaitingForShutdown =>
                            updateInternalDate(internalData.copy(playerTurn = 0, lastNumber = 0, tableState = ReadyForShutdown))
                            preShutdownTable()
                            self ! PoisonPill
                        case _ =>
                    }
                    logPrint("    ---------------------------------------------------")
            }
    }
}
