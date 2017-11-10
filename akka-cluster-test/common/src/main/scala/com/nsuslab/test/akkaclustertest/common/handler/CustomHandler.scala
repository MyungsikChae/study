package com.nsuslab.test.akkaclustertest.common.handler

import akka.persistence.fsm.PersistentFSMBase

trait CustomHandler[S, D, E] {
    this: PersistentFSMBase[S, D, E] =>
    def commonMessageHandler: StateFunction
    def initialMessageHandler: StateFunction
    def runningMessageHandler: StateFunction
    def finalMessageHandler: StateFunction
    def unknownMessageHandler: StateFunction

    def defaultInitialHandler = initialMessageHandler orElse commonMessageHandler orElse unknownMessageHandler
    def defaultRunningHandler = runningMessageHandler orElse commonMessageHandler orElse unknownMessageHandler
    def defaultFinalHandler = finalMessageHandler orElse commonMessageHandler orElse unknownMessageHandler
}
