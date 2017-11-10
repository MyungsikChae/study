package com.nsuslab.test.akkaclustertest.common.shard

import akka.actor.ActorRef
import akka.cluster.sharding.ShardCoordinator.{AbstractShardAllocationStrategy, ShardAllocationStrategy}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.ShardId
import com.nsuslab.test.akkaclustertest.common.message.{PlayerEnvelopeMessage, TestSystem}

import scala.collection.immutable
import scala.concurrent.Future

object PlayerSharding {
  val roleName: String = "player-sharding"
  val shardName: String = "Player"

  val idExtractor: ShardRegion.ExtractEntityId = {
    case PlayerEnvelopeMessage(a, b, c) => (s"PS$a-PE$b", c) //((Math.abs(b.hashCode) % 100).toString, c)
  }
  val shardResolver: ShardRegion.ExtractShardId = {
    case PlayerEnvelopeMessage(a, _, _) => s"PS$a" //(Math.abs(a.hashCode) % 100).toString
    case ShardRegion.StartEntity(id) => id.split("-")(0)
  }

}

class CustomPlayerShardAllocationStrategy(rebalanceThreshold: Int, maxSimultaneousRebalance: Int) extends ShardAllocationStrategy with Serializable {

  override def allocateShard(requester: ActorRef,
                             shardId: ShardId,
                             currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]]): Future[ActorRef] = {
    val (regionWithLeastShards, _) = currentShardAllocations.minBy { case (_, v) ⇒ v.size }
    Future.successful(regionWithLeastShards)
  }

  override def rebalance(currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]],
                         rebalanceInProgress: Set[ShardId]): Future[Set[ShardId]] = {
    if (rebalanceInProgress.size < maxSimultaneousRebalance) {
      val (regionWithLeastShards, leastShards) = currentShardAllocations.minBy { case (_, v) ⇒ v.size }
      val mostShards = currentShardAllocations.collect {
        case (_, v) ⇒ v.filterNot(s ⇒ rebalanceInProgress(s))
      }.maxBy(_.size)
      if (mostShards.size - leastShards.size >= rebalanceThreshold)
        Future.successful(mostShards.take(maxSimultaneousRebalance - rebalanceInProgress.size).toSet)
      else
        Future.successful(Set.empty[ShardId])
    } else
      Future.successful(Set.empty[ShardId])
  }
}
