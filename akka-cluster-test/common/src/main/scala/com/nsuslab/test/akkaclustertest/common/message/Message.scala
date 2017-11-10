package com.nsuslab.test.akkaclustertest.common.message

import java.util.UUID

trait Message

trait CommandMessage extends Message

trait EventMessage extends Message

trait SystemMessage extends Message

case class StartSystem()

/* Message for creation actor */
case class CreateGameMessage(gameId: Long, gameType: String, maxCount: Int, playerCount: Int) extends CommandMessage

case class CreateTableMessage(gameId: Long, tableId: Int, gameMode: String, maxCount: Int, playerCount: Int) extends CommandMessage

case class CreatePlayerMessage(gameId: Long, tableId: Int, playerId: Int, maxCount: Int, gameMode: String) extends CommandMessage

case class CompleteCreatingGameMessage(gameId: Long) extends EventMessage

case class CompleteCreatingTableMessage(gameId: Long, tableId: Int) extends EventMessage

case class CompleteCreatingPlayerMessage(gameId: Long, tableId: Int, playerId: Int) extends EventMessage

/* Message for terminating actor */
case class TerminateGameMessage(gameId: Long) extends CommandMessage

case class TerminateTableMessage(gameId: Long, tableId: Int) extends CommandMessage

case class TerminatePlayerMessage(gameId: Long, tableId: Int, playerId: Int) extends CommandMessage

case class CompleteTerminatingGameMessage(gameId: Long) extends EventMessage

case class CompleteTerminatingTableMessage(gameId: Long, tableId: Int) extends EventMessage

case class CompleteTerminatingPlayerMessage(gameId: Long, tableId: Int, playerId: Int) extends EventMessage

/* message for envelope include shard id */
case class GameEnvelopeMessage(gameShardId: Long, gameEntityId: Long, msg: Message) extends Message

case class TableEnvelopeMessage(tableShardId: Long, tableEntityId: Int, msg: Message) extends Message

case class PlayerEnvelopeMessage(playerShardId: Long, playerEntityId: Int, msg: Message) extends Message

/* Message for game play control */
case object GameStart extends EventMessage

case object TurnStart extends EventMessage

case object GameEnd extends EventMessage

case class WhoTurn(playerId: Int, lastNumber: Int) extends EventMessage

case class WhoLose(playerId: Int) extends EventMessage

case class SelectNumber(lastNumber: Int) extends EventMessage

case class ChooseNumber(playerId: Int, selectedNumber: Int) extends CommandMessage

/* Message for chatting */
case class ChatMessage(uuid: UUID) extends Message

case class ChatMessageAck(uuid: UUID) extends Message

/* Message for machine shutdown control */
case object EntityGracefulShutDownMessage extends SystemMessage

case object TableShutDownMessage extends SystemMessage

case object UpgradeMessage extends SystemMessage

case object ReadyForUpgradeMessage extends SystemMessage

case class PlayerTerminateMessage(playerId: Int) extends SystemMessage

/* test message */
case class TestSystem(shardId: String, entityId: String) extends Message