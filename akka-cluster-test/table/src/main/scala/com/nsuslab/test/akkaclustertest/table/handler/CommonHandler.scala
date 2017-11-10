package com.nsuslab.test.akkaclustertest.table.handler

import akka.actor.{PoisonPill, Terminated}
import akka.persistence.SaveSnapshotSuccess
import com.nsuslab.test.akkaclustertest.common.message._
import com.nsuslab.test.akkaclustertest.table.worker.TableActor

trait CommonHandler {
    this: TableActor =>
    import TableActor._

    def commonMessageHandler: Receive = {
        case msg: SystemMessage =>
            log.info(" *** SystemMessage: {}", msg)
            msg match {
                case UpgradeMessage =>
                    internalData.tableState match {
                        case Idle =>
                            updateInternalDate(internalData.copy(tableState = ReadyForUpgrade))
                        case Running =>
                            updateInternalDate(internalData.copy(tableState = WaitingForUpgrade))
                        case _ =>
                    }

                case EntityGracefulShutDownMessage =>
                    self ! PoisonPill

                case msg: PlayerTerminateMessage =>
                    updateInternalDate(internalData.copy(playerList = internalData.playerList.filter(_.playerId != msg.playerId)))
            }

        case msg: ChatMessage =>
            sender() ! ChatMessageAck(msg.uuid)

        case Terminated(who) =>
            log.debug(" *** Terminated: {}", who)

        case msg: SaveSnapshotSuccess =>
            log.debug(" *** SaveSnapshotSuccess: {} : {}", msg, internalData)
    }
}
