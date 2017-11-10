package com.nsuslab.test.akkaclustertest.common.shard

import akka.cluster.sharding.ShardRegion
import com.nsuslab.test.akkaclustertest.common.message._

object GameSharding {
  val roleName: String = "game-sharding"
  val shardName: String = "Game"

  val idExtractor: ShardRegion.ExtractEntityId = {
    case GameEnvelopeMessage(a, b, c) => (s"GS$a-GE$b", c) //((Math.abs(b.hashCode) % 100).toString, c)
  }
  val shardResolver: ShardRegion.ExtractShardId = {
    case GameEnvelopeMessage(a, _, _) => s"GS$a" //(Math.abs(a.hashCode) % 100).toString
    case ShardRegion.StartEntity(id) => id.split("-")(0)
  }

}
