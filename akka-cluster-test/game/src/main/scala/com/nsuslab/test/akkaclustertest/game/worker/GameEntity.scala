package com.nsuslab.test.akkaclustertest.game.worker

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated}
import akka.cluster.sharding.ShardRegion.Passivate
import com.nsuslab.test.akkaclustertest.common.message.{CompleteTerminatingGameMessage, CreateGameMessage}

class GameEntity extends Actor with ActorLogging {
    var service: ActorRef = Actor.noSender

    override def preStart(): Unit = {
        super.preStart()
    }

    override def postStop(): Unit = {
        super.postStop()
    }

    override def receive = {
        case m: CreateGameMessage if service == Actor.noSender =>
            service = context.actorOf(Props[GameActor], "GameActor")
            service forward m
            context watch service

        case m: CompleteTerminatingGameMessage =>
            if (service != Actor.noSender) context unwatch service
            service = Actor.noSender
            context.parent ! Passivate(PoisonPill)

        case Terminated(who) =>
            if (service != Actor.noSender && service.equals(who)) {
                context unwatch service
                service = Actor.noSender
            }

        case m =>
            if (service != Actor.noSender) service forward m

    }
}
