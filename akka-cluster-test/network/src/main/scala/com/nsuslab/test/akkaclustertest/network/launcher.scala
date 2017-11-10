package com.nsuslab.test.akkaclustertest.network

import akka.actor.{ActorSystem, Props}
import com.nsuslab.test.akkaclustertest.network.service.NetworkService
import com.typesafe.config.{Config, ConfigFactory}

object launcher {
  def main(args: Array[String]): Unit = {
    start()
  }

  def start() = {
    println("=================================")
    val config : Config = ConfigFactory.load()
    val system : ActorSystem = ActorSystem("ClusterTestSystem", config)
    system.actorOf(Props[NetworkService], name = "NetworkService")
    println("=================================")
  }

  def stop() = {

  }

}
