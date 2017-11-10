package com.nsuslab.test.example.common.actor

import scala.collection.immutable
import scala.concurrent.duration._
import akka.actor.{ActorLogging, PoisonPill, Props, ReceiveTimeout}
import akka.cluster.Cluster
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import com.nsuslab.test.example.common.actor.AuthorListingFSM.{ListData, ListEvent, ListState}

import scala.reflect._

object AuthorListingFSM {

  def props(): Props = Props(new AuthorListingFSM)

  case class PostSummary(author: String, postId: String, title: String)
  case class GetPosts(author: String)
  case class Posts(list: immutable.IndexedSeq[PostSummary])
  case class Start()

  case class SnapShotData(data: Vector[PostSummary])

  val idExtractor: ShardRegion.ExtractEntityId = {
    case s: PostSummary => (s.author, s)
    case m: GetPosts    => (m.author, m)
  }

  val shardResolver: ShardRegion.ExtractShardId = {
    case s: PostSummary   => (math.abs(s.author.hashCode) % 100).toString
    case GetPosts(author) => (math.abs(author.hashCode) % 100).toString
  }

  val shardName: String = "AuthorListing"

  trait ListData {
    def add(item: PostSummary): ListData
    def setAuthor(author: String): ListData
    def empty(): ListData
  }
  case object EmptyListData extends ListData {
    def add(item: PostSummary) = NonEmptyListData("", 1, item +: Vector.empty[PostSummary])
    def setAuthor(author: String) = NonEmptyListData(author = author, 0, Vector.empty[PostSummary])
    def empty() = this
  }
  case class NonEmptyListData(author: String, count: Int, list: Vector[PostSummary]) extends ListData {
    def add(item: PostSummary) = this.copy(count = this.count + 1, list = item +: this.list)
    def setAuthor(author: String) = this.copy(author = author)
    def empty() = EmptyListData
  }

  trait ListEvent
  case class AddedItem(item: PostSummary) extends ListEvent
  case class SetAuthor(author: String) extends ListEvent

  trait ListState extends FSMState
  case object Idle extends ListState {
    override def identifier: String = "Idle"
  }
  case object Running extends ListState {
    override def identifier: String = "Running"
  }
}

class AuthorListingFSM extends PersistentFSM[ListState, ListData, ListEvent] with ActorLogging {
  import AuthorListingFSM._

  override def domainEventClassTag: ClassTag[ListEvent] = classTag[ListEvent]

  override def persistenceId: String = self.path.parent.name + "-" + self.path.name

  // passivate the entity when no activity
  context.setReceiveTimeout(2.minutes)

  val from: String = Cluster(context.system).selfAddress.hostPort

  var posts = Vector.empty[PostSummary]

  startWith(Running, EmptyListData)

  override def preStart(): Unit = {
    super.preStart()
    log.info(s"   ***   preStart : AuthorListing from $from")
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info(s"   ***   postSop : AuthorListing from $from")
  }

  // 1) Event was persisted by 'applying' method on state function
  // 2) Call sequence : 'when(Running)' > 'applyEvent' >  'andThen handler'
  // 3) When saveStateSnapshot() was called, all event message were persisted are deleted.
  // 4) In the recovery process, StateData are restored when FSM was started, and Persisted Events are reproduced to 'applyEvent' method.

  override def applyEvent(domainEvent: ListEvent, currentData: ListData): ListData = {
    log.info(s"   ***   applyEvent : $domainEvent / $currentData")
    domainEvent match {
      case AddedItem(item) => currentData.add(item).setAuthor(item.author)
    }
  }

  when(Running) {
    case Event(s: PostSummary, data) =>
      stay() applying AddedItem(s) andThen {
        case data: NonEmptyListData =>
          if (data.count % 10 ==0) saveStateSnapshot()
        case EmptyListData => saveStateSnapshot()
      }

    case Event(_: GetPosts, data) =>
      data match {
        case list: NonEmptyListData => sender() ! Posts(list.list)
      }
      stay()

    case Event(_:ReceiveTimeout, _) =>
      context.parent ! Passivate(stopMessage = PoisonPill)
      stay()
  }

}