package com.nsuslab.test.akkaclustertest.player.handler

import akka.actor.PoisonPill
import com.nsuslab.test.akkaclustertest.common.message._
import com.nsuslab.test.akkaclustertest.player.worker.PlayerActor
import com.nsuslab.test.akkaclustertest.player.worker.PlayerActor._

trait WaitForMyTurnHandler {
    this: PlayerActor =>

    def waitForTurnMessageHandler: StateFunction = {
        case Event(msg: CommandMessage, data) =>
            log.debug(" *** Receive CommandMessage : {} : [{} / {}]", msg, stateName, data)
            stay()

        case Event(msg: EventMessage, data) =>
            log.debug(" *** Receive EventMessage : {} : [{} / {}]", msg, stateName,stateData)
            val mySeq = data.getSequence()
            msg match {
                case WhoTurn(`mySeq`, lastNumber) =>
                    persist(TestEvent) { evt =>
                        log.debug(" *** Success persist {}", evt)
                    }
                    goto (MyTurn) applying LastNumber(lastNumber)
                case WhoLose(`mySeq`) =>
                    stay() applying AddLoseCount
                case GameEnd =>
                    if (chatScheduler != null) chatScheduler.cancel()
                    goto (Idle) andThen {
                        _ => saveStateSnapshot()
                    }
                case _ =>
                    stay()
            }
    }

    when(PlayerActor.WaitForMyTurn)(waitForTurnMessageHandler)
}
