package com.nsuslab.test.example.common.actor

import scala.collection.immutable
import scala.concurrent.duration._
import akka.actor.{ActorLogging, FSM, PoisonPill, Props, ReceiveTimeout}
import akka.cluster.Cluster
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{PersistentActor, SnapshotOffer}
import com.nsuslab.test.example.common.Message.Command
import com.nsuslab.test.example.common.actor.AuthorListing.{ListData, ListState}

object AuthorListing {

  def props(): Props = Props(new AuthorListing)

  case class PostSummary(author: String, postId: String, title: String) extends Command
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

  case class ListData(count: Int = 0, author: String = "", list: Vector[PostSummary] = Vector.empty[PostSummary])

  trait ListState
  case object Idle extends ListState
  case object Running extends ListState
}

class AuthorListing extends PersistentActor with FSM[ListState, ListData] with ActorLogging {
  import AuthorListing._

  override def persistenceId: String = self.path.parent.name + "-" + self.path.name

  // passivate the entity when no activity
  context.setReceiveTimeout(2.minutes)

  val from: String = Cluster(context.system).selfAddress.hostPort

  var posts = Vector.empty[PostSummary]

  startWith(Running, ListData())

  override def preStart(): Unit = {
    super.preStart()
    log.info(s"   ***   preStart : AuthorListing from $from")
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info(s"   ***   postSop : AuthorListing from $from")
  }

  when(Idle) {
    case Event(_: Start, data) =>
      goto(Running) using data
  }

  when(Running) {
    case Event(s: PostSummary, data) =>
      persist(s) { evt =>
        posts :+= evt
        log.info("Post added to {}'s list: {}, {}", s.author, s.title, data)
        println(s"  :::   ($lastSequenceNr, ${posts.size}, ${posts.size % 10})")
        if (posts.size % 10 == 0) {
          println(s"       ++++++++++++++         ($lastSequenceNr, ${posts.size})")
          saveSnapshot(SnapShotData(posts))
          deleteMessages(lastSequenceNr)
        }
      }
      stay() using data.copy(count = data.count + 1, author = s.author)

    case Event(_: GetPosts, data) =>
      sender() ! Posts(posts)
      stay()

    case Event(_:ReceiveTimeout, data) =>
      context.parent ! Passivate(stopMessage = PoisonPill)
      stay()
  }

  def receiveCommand: Receive = {
    case s: PostSummary =>
      persist(s) { evt =>
        posts :+= evt
        log.info("Post added to {}'s list: {}", s.author, s.title)
        println(s"  :::   ($lastSequenceNr, ${posts.size}, ${posts.size % 10})")
        if (posts.size % 10 == 0) {
          println(s"       ++++++++++++++         ($lastSequenceNr, ${posts.size})")
          saveSnapshot(SnapShotData(posts))
          deleteMessages(lastSequenceNr)
        }
      }
    case GetPosts(_) =>
      sender() ! Posts(posts)
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = PoisonPill)
  }

  override def receiveRecover: Receive = {
    case evt: PostSummary =>
      log.info(s"   ***   recovery : PostSummary : ${evt.toString}")
      posts :+= evt
    case SnapshotOffer(_, snapshot: SnapShotData) =>
      log.info(s"   ***   recovery : SnapshotOffer : ${snapshot.toString}")
      posts = snapshot.data
  }

}