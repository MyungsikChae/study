package com.nsuslab.test.akkaclustertest.player.handler

import akka.actor.PoisonPill

import scala.concurrent.duration._
import com.nsuslab.test.akkaclustertest.common.message._
import com.nsuslab.test.akkaclustertest.player.worker.PlayerActor
import com.nsuslab.test.akkaclustertest.player.worker.PlayerActor._

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

trait IdleHandler {
    this: PlayerActor =>
    import context.dispatcher

    def idleMessageHandler: StateFunction = {
        case Event(msg: CommandMessage, data) =>
            log.debug(" *** Receive CommandMessage : {} : [{} / {}]", msg, stateName, stateData)
            msg match {
                case msg: CreatePlayerMessage =>
                    sender ! CompleteCreatingPlayerMessage(msg.gameId, msg.tableId, msg.playerId)
                    stay() applying SetSequence(msg.playerId, msg.maxCount, msg.gameMode, msg.gameId, msg.tableId) andThen {
                        case data: NonEmptyPlayerData => saveStateSnapshot()
                        case EmptyPlayerData => saveStateSnapshot()
                    }
                case msg: TerminatePlayerMessage =>
                    preShutdownPlayer()
                    stay()
                case _ =>
                    stay()
            }

        case Event(msg: EventMessage, data) =>
            log.debug(" *** Receive EventMessage : {} : [{} / {}]", msg, stateName, stateData)
            msg match {
                case GameStart =>
                    if (enableChat)
                        chatScheduler = context.system.scheduler.schedule(1.second, FiniteDuration(intervalChat, MILLISECONDS), self, Tick)
                    goto (WaitForMyTurn)
                case _ =>
                    stay()
            }
    }

    when(PlayerActor.Idle)(idleMessageHandler)
}
