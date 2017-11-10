package com.nsuslab.test.akkaclustertest.player.handler

import java.util.UUID

import akka.actor.PoisonPill
import akka.persistence.SaveSnapshotSuccess
import com.nsuslab.test.akkaclustertest.common.message._
import com.nsuslab.test.akkaclustertest.player.worker.PlayerActor
import com.nsuslab.test.akkaclustertest.player.worker.PlayerActor._

trait CommonHandler {
    this: PlayerActor =>
    import context.dispatcher
    import akka.pattern._

    def commonMessageHandler: StateFunction = {
        case Event(msg: SystemMessage, _) =>
            log.info(" *** SystemMessage: {}", msg)
            msg match {
                case EntityGracefulShutDownMessage =>
                    self ! PoisonPill
                    stay()
                case _ =>
                    stay()
            }

        case Event(Tick, data) =>
            val uuid = UUID.randomUUID()
            logPrint(s" [  ? ?  ] send chat message : $uuid")
            tableRegion ? TableEnvelopeMessage(data.getGameId(), data.getTableId(), ChatMessage(uuid)) map {
                case ChatMessageAck(`uuid`) =>
                    logPrint(s" [  O K  ] receive ack : $uuid")
                case msg: ChatMessageAck =>
                    logPrint(s" [NOMATCH] receive ack : ${msg.uuid}")
            } recover {
                case msg: AskTimeoutException =>
                    log.error(msg, s" [Exception] ${msg.getMessage}")
            }
            stay()

        case Event(msg: SaveSnapshotSuccess, _) =>
            log.debug(" *** Receive a message: {} : [{} / {}]", msg, stateName, stateData)
            stay()
    }

    when(PlayerActor.Idle)(commonMessageHandler)
    when(PlayerActor.WaitForMyTurn)(commonMessageHandler)
    when(PlayerActor.MyTurn)(commonMessageHandler)
}
