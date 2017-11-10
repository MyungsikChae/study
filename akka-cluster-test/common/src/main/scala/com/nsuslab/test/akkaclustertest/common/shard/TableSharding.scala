package com.nsuslab.test.akkaclustertest.common.shard

import akka.actor.ActorRef
import akka.cluster.sharding.ShardCoordinator.ShardAllocationStrategy
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.ShardId
import com.nsuslab.test.akkaclustertest.common.hazelcast.repository.TableMaintenance
import com.nsuslab.test.akkaclustertest.common.message.TableEnvelopeMessage

import scala.collection.{immutable, mutable}
import scala.concurrent.Future

object TableSharding {
  val roleName: String = "table-sharding"
  val shardName: String = "Table"

  val idExtractor: ShardRegion.ExtractEntityId = {
    case TableEnvelopeMessage(a, b, c) => (s"TS$a-TE$b", c) //((Math.abs(b.hashCode) % 100).toString, c)
  }
  val shardResolver: ShardRegion.ExtractShardId = {
    case TableEnvelopeMessage(a, _, _) => s"TS$a" //(Math.abs(a.hashCode) % 100).toString
    case ShardRegion.StartEntity(id) => id.split("-")(0)
  }

}

class CustomTableShardAllocationStrategy(rebalanceThreshold: Int, maxSimultaneousRebalance: Int) extends ShardAllocationStrategy with Serializable {
  private val emptyRebalanceResult = Future.successful(Set.empty[ShardId])

//  override def allocateShard(requester: ActorRef, shardId: ShardId,
//          currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]]): Future[ActorRef] = {
//    val (regionWithLeastShards, _) = currentShardAllocations.minBy { case (_, v) ⇒ v.size }
//    Future.successful(regionWithLeastShards)
//  }
//
//  override def rebalance(
//          currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]],
//          rebalanceInProgress:     Set[ShardId]): Future[Set[ShardId]] = {
//    if (rebalanceInProgress.size < maxSimultaneousRebalance) {
//      val (regionWithLeastShards, leastShards) = currentShardAllocations.minBy { case (_, v) ⇒ v.size }
//      val mostShards = currentShardAllocations.collect {
//        case (_, v) ⇒ v.filterNot(s ⇒ rebalanceInProgress(s))
//      }.maxBy(_.size)
//      if (mostShards.size - leastShards.size >= rebalanceThreshold)
//        Future.successful(mostShards.take(maxSimultaneousRebalance - rebalanceInProgress.size).toSet)
//      else
//        emptyRebalanceResult
//    } else emptyRebalanceResult
//  }

  override def allocateShard(requester: ActorRef,
                             shardId: ShardId,
                             currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]]): Future[ActorRef] = {

    println(s" --- requester : ${requester.path.toStringWithoutAddress}")
    println(s" --- shardId : $shardId")
    println(s" --- currentShardAllocations : $currentShardAllocations")
    val (regionWithLeastShards, _) = currentShardAllocations
            .filterNot { case (ref, shardId) =>
              val bool = TableMaintenance.isOnShutdown(shardId.toString())
              println(s" --- $ref : $bool")
              bool
            }
            .minBy { case (_, v) => v.size }
    println(s" --- regionWithLeastShards : $regionWithLeastShards")
    Future.successful(regionWithLeastShards)
  }

  override def rebalance(currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]],
                         rebalanceInProgress: Set[ShardId]): Future[Set[ShardId]] = {
    println("rebalance called ...............................................")
    println(s" +++ currentShardAllocations : $currentShardAllocations")
    println(s" +++ rebalanceInProgress : $rebalanceInProgress")
    if (!TableMaintenance.isOnUpgrade) {
      if (rebalanceInProgress.size < maxSimultaneousRebalance) {
        val (_, leastShards) = currentShardAllocations.minBy { case (_, v) => v.size }
        val mostShards = currentShardAllocations.collect {
          case (_, v) => v.filterNot(s => rebalanceInProgress(s))
        }.maxBy(_.size)
        if (mostShards.size - leastShards.size >= rebalanceThreshold) {
          val set = {
            var i = 0
            for {id <- mostShards
                 if i < maxSimultaneousRebalance - rebalanceInProgress.size
                 if TableMaintenance.tryStartHandOff(id)}
              yield {
                i += 1
                id
              }
          }.toSet
          Future.successful(set)
        }
        else emptyRebalanceResult
      } else emptyRebalanceResult
    } else {
        if (false) {
            val ids: Iterator[ShardId] = currentShardAllocations
                    .filter { case (ref, shardId) =>
                        val bool = TableMaintenance.isOnShutdown(shardId.toString())
                        println(s" +++ $ref : $bool")
                        bool
                    }
                    .flatMap { case (_, shardIds) => shardIds }
                    .filterNot(s => rebalanceInProgress(s)).toIterator
            println(s" +++ ids : $ids")
            val ret = mutable.Set[ShardId]()
            while (ids.hasNext && ret.size < maxSimultaneousRebalance - rebalanceInProgress.size) {
                val id = ids.next()
                if (TableMaintenance.tryStartHandOff(id)) ret += id
            }
            println(s" +++ ret : $ret")
            Future.successful(ret.toSet)
        } else {
            emptyRebalanceResult
        }
    }

  }
}