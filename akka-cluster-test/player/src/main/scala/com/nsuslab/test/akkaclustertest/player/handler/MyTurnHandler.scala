package com.nsuslab.test.akkaclustertest.player.handler

import akka.actor.PoisonPill
import com.nsuslab.test.akkaclustertest.common.message._
import com.nsuslab.test.akkaclustertest.common.Constant._
import com.nsuslab.test.akkaclustertest.player.worker.PlayerActor
import com.nsuslab.test.akkaclustertest.player.worker.PlayerActor._

trait MyTurnHandler {
    this: PlayerActor =>

    def myTurnMessageHandler: StateFunction = {
        case Event(msg: CommandMessage, data) =>
            log.debug(" *** Receive CommandMessage : {} : [{} / {}]", msg, stateName, data)
            stay()

        case Event(msg: EventMessage, data) =>
            log.debug(" *** Receive EventMessage : {} : [{} / {}]", msg, stateName, data)
            msg match {
                case msg: SelectNumber =>
                    if (msg.lastNumber == stateData.getMaxCount() - 1) {
                        tableRegion ! TableEnvelopeMessage(stateData.getGameId(), stateData.getTableId(), ChooseNumber(stateData.getSequence(), stateData.getMaxCount()))
                    } else {
                        val number = stateData.getGameMode() match {
                            case `GameTypeThirtyOne` =>
                                Math.ceil(Math.random() * (Math.min(msg.lastNumber + 3, stateData.getMaxCount() - 1) - msg.lastNumber)) + msg.lastNumber
                            case `GameTypeSimpleIncrease` =>
                                msg.lastNumber + 1
                            case _ =>
                                msg.lastNumber + 1
                        }
                        logPrint(s"    **  [G${stateData.getGameId()}/T${stateData.getTableId()}] player-${stateData.getSequence()} -> number [ $number ]")
                        tableRegion ! TableEnvelopeMessage(stateData.getGameId(), stateData.getTableId(), ChooseNumber(stateData.getSequence(), number.toInt))
                    }
                    goto (WaitForMyTurn) andThen {
                        _ => saveStateSnapshot()
                    }
                case _ =>
                    stay()
            }
    }

    when(PlayerActor.MyTurn)(myTurnMessageHandler)
}
