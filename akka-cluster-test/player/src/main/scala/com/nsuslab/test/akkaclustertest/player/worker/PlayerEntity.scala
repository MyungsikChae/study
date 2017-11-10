package com.nsuslab.test.akkaclustertest.player.worker

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated}
import akka.cluster.sharding.ShardRegion.Passivate
import com.nsuslab.test.akkaclustertest.common.message.{CompleteTerminatingPlayerMessage, CreatePlayerMessage}

class PlayerEntity extends Actor with ActorLogging {
    var service: ActorRef = Actor.noSender

    override def preStart(): Unit = {
        super.preStart()
     }
    override def postStop(): Unit = {
        super.postStop()
    }

    override def receive = {
        case m: CreatePlayerMessage if service == Actor.noSender =>
            service = context.actorOf(Props[PlayerActor], "Player")
            service forward m
            context watch service

        case m: CompleteTerminatingPlayerMessage =>
            if (service != Actor.noSender) context unwatch service
            service = Actor.noSender
            context.parent ! Passivate(PoisonPill)

        case Terminated(who) =>
            if (service != Actor.noSender && service.equals(who)) {
                context.unwatch(service)
                service = Actor.noSender
            }

        case m =>
            if (service != Actor.noSender) service forward m

    }

}
