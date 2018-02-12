package com.nsuslab.test.akkaclustertest.dummy_account

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.{DeserializationFeature, SerializationFeature}
import com.nsuslab.test.akkaclustertest.dummy_account.account.AccountRouter
import com.typesafe.scalalogging.LazyLogging
import io.vertx.core.json.Json
import io.vertx.ext.web.handler.LoggerFormat
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.ext.web.Router
import io.vertx.scala.ext.web.handler.{LoggerHandler, ResponseContentTypeHandler, SessionHandler, StaticHandler}
import io.vertx.scala.ext.web.sstore.LocalSessionStore

import scala.util.{Failure, Success}

class Launcher extends ScalaVerticle with LazyLogging {
    override def start(): Unit = {

        AccountRouter.deployVerticle(vertx)

        val router = Router.router(vertx)
        val sessionStore = LocalSessionStore.create(vertx, "DummyInstanceSession", 1800000)
        router.route.handler(SessionHandler.create(sessionStore))
        router.route.handler(LoggerHandler.create(immediate = false, LoggerFormat.SHORT))
        router.route.handler(ResponseContentTypeHandler.create())

        AccountRouter.mountRouter(vertx, router)

        import com.fasterxml.jackson.annotation.JsonInclude.Include
        Json.mapper.setSerializationInclusion(Include.NON_NULL)
        Json.mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)

        vertx.createHttpServer().requestHandler(router.accept _).listenFuture(port = 8181, host = "127.0.0.1").onComplete {
            case Success(server) => logger.info(s"start listening on ${server.actualPort()}")
            case Failure(cause)  => logger.error("start listening failed", cause)
        }
    }
}
