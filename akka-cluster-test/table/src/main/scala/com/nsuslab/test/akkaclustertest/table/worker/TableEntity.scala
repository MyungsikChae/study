package com.nsuslab.test.akkaclustertest.table.worker

import scala.concurrent.duration._
import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, PoisonPill, Props, Terminated}
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ShardRegion.Passivate
import com.nsuslab.test.akkaclustertest.common.hazelcast.repository.TableMaintenance
import com.nsuslab.test.akkaclustertest.common.message.{CompleteTerminatingTableMessage, CreateTableMessage, ReadyForUpgradeMessage}
import com.nsuslab.test.akkaclustertest.common.shard.TableSharding

class TableEntity extends Actor with ActorLogging {

    var service: ActorRef = Actor.noSender

    println(s" *** TableEntity : ${self.path.parent.name} / ${self.path.name}")

    override def preStart(): Unit = {
        super.preStart()
        TableMaintenance.tryFinishShutdown(self.path.parent.name)
    }
    override def postStop(): Unit = {
        super.postStop()
    }

    override def supervisorStrategy =
        OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 2.seconds) {
            case _: Exception => Restart
        }

    override def receive = {
        case m: CreateTableMessage if service == Actor.noSender =>
            service = context.actorOf(Props[TableActor], "TableActor")
            service forward m
            context watch service

        case m: CompleteTerminatingTableMessage =>
            if (service != Actor.noSender) context unwatch service
            service = Actor.noSender
            context.parent ! Passivate(PoisonPill)

        case Terminated(who) =>
            if (service != Actor.noSender && service.equals(who)) {
                context unwatch service
                service = Actor.noSender
            }

        case ReadyForUpgradeMessage =>
            TableMaintenance.tryStartShutdown(self.path.parent.name)
            self ! PoisonPill

        case m =>
            if (service != Actor.noSender) service forward m

    }

}
