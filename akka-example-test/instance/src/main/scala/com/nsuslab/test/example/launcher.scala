package com.nsuslab.test.example

import scala.concurrent.duration._
import akka.actor.{ActorIdentity, ActorPath, ActorSystem, Identify, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.pattern.ask
import akka.util.Timeout
import com.nsuslab.test.example.common.actor.{AuthorListing, AuthorListingFSM, Bot, Post}
import com.typesafe.config.ConfigFactory

import scala.swing._

class UI extends MainFrame {
  def restrictHeight(s: Component) {
    s.maximumSize = new Dimension(Short.MaxValue, s.preferredSize.height)
  }

  title = "GUI Program #5"

  val nameField = new TextField { columns = 32 }
  val likeScala = new CheckBox("I like Scala")
  likeScala.selected = true
  val status1 = new RadioButton("학부생")
  val status2 = new RadioButton("대학원생")
  val status3 = new RadioButton("교수")
  status3.selected = true
  val statusGroup = new ButtonGroup(status1, status2, status3)
  val gender = new ComboBox(List("don't know", "female", "male"))
  val commentField = new TextArea { rows = 8; lineWrap = true; wordWrap = true }
  val pressMe = new ToggleButton("Press me!")
  pressMe.selected = true

  restrictHeight(nameField)
  restrictHeight(gender)

  contents = new BoxPanel(Orientation.Vertical) {
    contents += new BoxPanel(Orientation.Horizontal) {
      contents += new Label("My name")
      contents += Swing.HStrut(5)
      contents += nameField
    }
    contents += Swing.VStrut(5)
    contents += likeScala
    contents += Swing.VStrut(5)
    contents += new BoxPanel(Orientation.Horizontal) {
      contents += status1
      contents += Swing.HStrut(10)
      contents += status2
      contents += Swing.HStrut(10)
      contents += status3
    }
    contents += Swing.VStrut(5)
    contents += new BoxPanel(Orientation.Horizontal) {
      contents += new Label("Gender")
      contents += Swing.HStrut(20)
      contents += gender
    }
    contents += Swing.VStrut(5)
    contents += new Label("Comments")
    contents += Swing.VStrut(3)
    contents += new ScrollPane(commentField)
    contents += Swing.VStrut(5)
    contents += new BoxPanel(Orientation.Horizontal) {
      contents += pressMe
      contents += Swing.HGlue
      contents += Button("Close") { reportAndClose() }
    }
    for (e <- contents)
      e.xLayoutAlignment = 0.0
    border = Swing.EmptyBorder(10, 10, 10, 10)
  }

  def reportAndClose() {
    println("Your name: " + nameField.text)
    println("You like Scala: " + likeScala.selected)
    println("Undergraduate: " + status1.selected)
    println("Graduate: " + status2.selected)
    println("Professor: " + status3.selected)
    println("Gender: " + gender.selection.item +
      " (Index: " + gender.selection.index + ")")
    println("Comments: " + commentField.text)
    println("'Press me' is pressed: " + pressMe.selected)
    sys.exit(0)
  }
}

object launcher {
  def main(args: Array[String]): Unit = {
    startup(args)
//    val ui = new UI
//    ui.visible = true
  }

  def startup(args: Array[String]): Unit = {
    // Override the configuration of the port
    val config = ConfigFactory.load()

    val port = config.getString("akka.remote.netty.tcp.port")

    val fsmTestMode = config.getBoolean("private.test-mode.fsm-test")

    println(s" >>>> fsmTestMode : $fsmTestMode")

    // Create an Akka system
    val system = ActorSystem("ClusterSystem", config)

    def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {
      // Start the shared journal one one node (don't crash this SPOF)
      // This will not be needed with a distributed journal
      if (startStore)
        system.actorOf(Props[SharedLeveldbStore], "store")
      // register the shared journal
      import system.dispatcher
      implicit val timeout = Timeout(15.seconds)
      val f = system.actorSelection(path) ? Identify(None)
      f.onSuccess {
        case ActorIdentity(_, Some(ref)) => SharedLeveldbJournal.setStore(ref, system)
        case _ =>
          system.log.error("Shared journal not started at {}", path)
          system.terminate()
      }
      f.onFailure {
        case _ =>
          system.log.error("Lookup of shared journal at {} timed out", path)
          system.terminate()
      }
    }

    startupSharedJournal(system, startStore = port == "2551", path =
      ActorPath.fromString("akka.tcp://ClusterSystem@127.0.0.1:2551/user/store"))

    port match {
      case "2551" =>
        val authorListingRegion = args.isEmpty match {
          case true =>
            ClusterSharding(system).start(
              typeName = if(fsmTestMode) AuthorListingFSM.shardName else AuthorListing.shardName,
              entityProps = if(fsmTestMode) AuthorListingFSM.props() else AuthorListing.props() ,
              settings = ClusterShardingSettings(system),
              extractEntityId = if(fsmTestMode) AuthorListingFSM.idExtractor else AuthorListing.idExtractor,
              extractShardId = if(fsmTestMode) AuthorListingFSM.shardResolver else AuthorListing.shardResolver)
          case _ =>
            ClusterSharding(system).startProxy(
              typeName = if(fsmTestMode) AuthorListingFSM.shardName else AuthorListing.shardName,
              role = Option("list-sharding"),
              extractEntityId = if(fsmTestMode) AuthorListingFSM.idExtractor else AuthorListing.idExtractor,
              extractShardId = if(fsmTestMode) AuthorListingFSM.shardResolver else AuthorListing.shardResolver)
        }

        ClusterSharding(system).start(
          typeName = Post.shardName,
          entityProps = Post.props(authorListingRegion),
          settings = ClusterShardingSettings(system),
          extractEntityId = Post.idExtractor,
          extractShardId = Post.shardResolver)

      case "2552" =>
        val authorListingRegion = ClusterSharding(system).start(
          typeName = if(fsmTestMode) AuthorListingFSM.shardName else AuthorListing.shardName,
          entityProps = if(fsmTestMode) AuthorListingFSM.props() else AuthorListing.props(),
          settings = ClusterShardingSettings(system),
          extractEntityId = if(fsmTestMode) AuthorListingFSM.idExtractor else AuthorListing.idExtractor,
          extractShardId = if(fsmTestMode) AuthorListingFSM.shardResolver else AuthorListing.shardResolver)

        args.isEmpty match {
          case true =>
            ClusterSharding(system).start(
              typeName = Post.shardName,
              entityProps = Post.props(authorListingRegion),
              settings = ClusterShardingSettings(system),
              extractEntityId = Post.idExtractor,
              extractShardId = Post.shardResolver)
          case _ =>
            ClusterSharding(system).startProxy(
              typeName = Post.shardName,
              role = Option("post-sharding"),
              extractEntityId = Post.idExtractor,
              extractShardId = Post.shardResolver)
        }

      case _ =>
        ClusterSharding(system).startProxy(
          typeName = if(fsmTestMode) AuthorListingFSM.shardName else AuthorListing.shardName,
          role = Option("list-sharding"),
          extractEntityId = if(fsmTestMode) AuthorListingFSM.idExtractor else AuthorListing.idExtractor,
          extractShardId = if(fsmTestMode) AuthorListingFSM.shardResolver else AuthorListing.shardResolver)
        ClusterSharding(system).startProxy(
          typeName = Post.shardName,
          role = Option("post-sharding"),
          extractEntityId = Post.idExtractor,
          extractShardId = Post.shardResolver)
    }

    if (port != "2551" && port != "2552")
      system.actorOf(Props[Bot], "bot")

  }
}
