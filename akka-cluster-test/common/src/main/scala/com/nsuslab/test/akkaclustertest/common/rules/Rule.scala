package com.nsuslab.test.akkaclustertest.common.rules

import com.nsuslab.test.akkaclustertest.common.rules.CardNumberType.CardNumberType
import com.nsuslab.test.akkaclustertest.common.rules.CardShapeType.CardShapeType

case class Card(shape: CardShapeType, num: CardNumberType, owner: Option[Player] = None) extends Ordered[Card] {
    override def hashCode() = {
        num.id
    }
    override def compare(that: Card) = {
        num.id - that.num.id
    }
}

object CardShapeType extends Enumeration(1) {
    type CardShapeType = Value
    val Clover, Diamond, Heart, Spade = Value
}

object CardNumberType extends Enumeration(1) {
    type CardNumberType = Value
    val _A , _2, _3, _4, _5, _6, _7, _8, _9, _T, _J, _Q, _K = Value
}

trait AbstractPlayer {
    def getGPID: String
    def getNickName: String
}

trait AbstractHand {
    def bestHand
    def HoleCard
    def CommunityCard
}

case class Player(gpId: String, nickName: String) extends AbstractPlayer {
    override def getGPID: String = { gpId }
    override def getNickName = { nickName }
}

case class Hand(holeCard: Set[Card], communityCard: Set[Card]) extends AbstractHand {
    override def bestHand: Unit = ???
    override def HoleCard: Unit = ???
    override def CommunityCard: Unit = ???
}

trait GameRule {
    def sortCard
    def compareHands(data: Seq[(Player, Hand)])
}
trait Holdem extends GameRule {
    val HoleCard = 2
    val CommunityCard = 5
}

trait Omaha extends GameRule {
    val HoleCard = 4
    val CommunityCard = 5
}

trait OmahaHL extends GameRule {
    val HoleCard = 4
    val CommunityCard = 5
}

case class Pot(pot: Long = 0L, fee: Long = 0L)

trait BetRule {
    def getPotAmount(): Long
    def getFeeAmount(): Long
    def getCallAmount(): Long
    def getRaiseAmount(): (Long, Long)
}

trait NoLimit extends BetRule {
    var pot = Pot()
    override def getPotAmount() = { pot.pot }
    override def getFeeAmount() = { pot.fee }
    override def getCallAmount() = { 1L }
    override def getRaiseAmount() = { (1L, 2L) }
}

trait PotLimit extends BetRule {
    var pot = Pot()
    override def getPotAmount() = { pot.pot }
    override def getFeeAmount() = { pot.fee }
    override def getCallAmount() = { pot.pot }
    override def getRaiseAmount() = { (pot.pot, pot.pot * 2)}
}

trait Round
trait PreBetRound extends Round // Ante
trait FirstRound extends Round  // preflop
trait SecondRound extends Round // flop
trait ThirdRound extends Round  // river
trait ForthRound extends Round  // turn
trait FifthRound extends Round  // showdown

trait Rule[T <: GameRule, B <: BetRule, R <: Round]

trait HoldemNoLimitPre extends Rule[Holdem, NoLimit, PreBetRound]
trait HoldemNoLimitFir extends Rule[Holdem, NoLimit, FirstRound]
trait HoldemNoLimitSec extends Rule[Holdem, NoLimit, SecondRound]
trait HoldemNoLimitThi extends Rule[Holdem, NoLimit, ThirdRound]
trait HoldemNoLimitFor extends Rule[Holdem, NoLimit, ForthRound]

trait RuleHandler {
    def nextRound
    def nextTurn
}

class GameRuleHandler(rules: Iterator[Rule[GameRule, BetRule, Round]]) extends RuleHandler {
    override def nextRound: Unit = {}
    override def nextTurn: Unit = {}
}