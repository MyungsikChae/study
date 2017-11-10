package com.nsuslab.test.example.common.actor

import java.util.UUID

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterSharding

object Bot {
  private case object Tick
}

class Bot extends Actor with ActorLogging {
  import Bot._
  import context.dispatcher
  val tickTask: Cancellable = context.system.scheduler.schedule(1.seconds, 1.seconds, self, Tick)

  val fsmTestMode: Boolean = context.system.settings.config.getBoolean("private.test-mode.fsm-test")

  val postRegion: ActorRef = ClusterSharding(context.system).shardRegion(Post.shardName)
  val listingsRegion: ActorRef = ClusterSharding(context.system).shardRegion(if (fsmTestMode) AuthorListingFSM.shardName else AuthorListing.shardName)

  val from: String = Cluster(context.system).selfAddress.hostPort

  override def preStart(): Unit = {
    super.preStart()
    log.info(s"   ***   preStart : Bot from $from")
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info(s"   ***   postSop : Bot from $from")
    tickTask.cancel()
  }

  var n = 0
  val authors = Map(0 -> "Patrik", 1 -> "Martin", 2 -> "Roland", 3 -> "Björn", 4 -> "Endre")
  def currentAuthor = authors(n % authors.size)

  def receive: Receive = create

  val create: Receive = {
    case Tick =>
      val postId = UUID.randomUUID().toString
      n += 1
      val title = s"Post $n from $from"
      postRegion ! Post.AddPost(postId, Post.PostContent(currentAuthor, title, "..."))
      context.become(edit(postId))
  }

  def edit(postId: String): Receive = {
    case Tick =>
      postRegion ! Post.ChangeBody(postId, "Something very interesting ...")
      context.become(publish(postId))
  }

  def publish(postId: String): Receive = {
    case Tick =>
      postRegion ! Post.Publish(postId)
      context.become(list)
  }

  val list: Receive = {
    case Tick =>
      if (fsmTestMode)
        listingsRegion ! AuthorListingFSM.GetPosts(currentAuthor)
      else
        listingsRegion ! AuthorListing.GetPosts(currentAuthor)
    case AuthorListingFSM.Posts(summaries) =>
      log.info("Posts by {}: {}", currentAuthor, summaries.map(_.title).mkString("\n\t", "\n\t", ""))
      context.become(create)
    case AuthorListing.Posts(summaries) =>
      log.info("Posts by {}: {}", currentAuthor, summaries.map(_.title).mkString("\n\t", "\n\t", ""))
      context.become(create)
  }

}
