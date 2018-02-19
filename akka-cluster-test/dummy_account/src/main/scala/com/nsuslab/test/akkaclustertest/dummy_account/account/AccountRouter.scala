package com.nsuslab.test.akkaclustertest.dummy_account.account

import com.nsuslab.test.akkaclustertest.dummy_account.AbstractRouterHelper
import com.nsuslab.test.akkaclustertest.dummy_account.account.verticles.{BuyInVerticle, LoginVerticle}
import io.vertx.lang.scala.json.JsonObject
import io.vertx.lang.scala.{ScalaVerticle, VertxExecutionContext}
import io.vertx.scala.core.{DeploymentOptions, Vertx}
import io.vertx.scala.ext.web.Router
import io.vertx.scala.ext.web.handler.BodyHandler

import scala.util.{Failure, Success}

object AccountRouter extends AbstractRouterHelper {
    override def deployVerticle(vertx: Vertx): Unit = {
        implicit val executionContext: VertxExecutionContext = VertxExecutionContext(vertx.getOrCreateContext())
        vertx.deployVerticleFuture(ScalaVerticle.nameForVerticle[LoginVerticle], DeploymentOptions().setWorker(true)).onComplete {
            case Success(stat)  => logger.info(s"successfully deployed verticle - LoginVerticle[$stat]")
            case Failure(cause) => logger.error("deploy verticle failed - LoginVerticle", cause)
        }
        vertx.deployVerticleFuture(ScalaVerticle.nameForVerticle[BuyInVerticle], DeploymentOptions().setWorker(true)).onComplete {
            case Success(stat)  => logger.info(s"successfully deployed verticle - BuyInVerticle[$stat]")
            case Failure(cause) => logger.error("deploy verticle failed - BuyInVerticle", cause)
        }
    }
    override def mountRouter(vertx: Vertx, mainRouter: Router): Unit = {
        implicit val executionContext: VertxExecutionContext = VertxExecutionContext(vertx.getOrCreateContext())
//        val router = Router.router(vertx)
//        mainRouter.mountSubRouter("/account", router)
//        router.route().handler(BodyHandler.create())
//        logger.info("/account router mounted")

        val eb = vertx.eventBus()
        mainRouter.get("/:userId").handler {ctx =>
            val userId = ctx.request().getParam("userId")
            logger.info(s"----- /:userId ($userId) -----")
            if(userId.nonEmpty){
                ctx.response().setStatusCode(200).end()
            } else ctx.response().setStatusCode(400).end()
        }

        mainRouter.post("/login").handler {ctx =>
            logger.info(s"----- /login -----")
            val json = ctx.getBodyAsJson()
            logger.info(s" :: $json")
            if (json.isDefined) {
                val data = json.get
                eb.sendFuture[String](LoginVerticle.addressLogin, data).onComplete(responseCallback(ctx))
            } else ctx.response().setStatusCode(400).end()
        }

        mainRouter.post("/buyin/:tourneyId/:playerId").handler {ctx =>
            val trxId = ctx.request().getParam("trxId").getOrElse("")
            val tourneyId = ctx.request().getParam("tourneyId").getOrElse("")
            val playerId = ctx.request().getParam("playerId").getOrElse("")
            logger.info(s"----- /buyin/$tourneyId/$playerId?trxId=$trxId -----")
            val json = new JsonObject()
            json.put("tourneyId", tourneyId)
            json.put("trxId", trxId)
            json.put("playerId", playerId)
            logger.info(s" :: $json")
            eb.sendFuture[String](BuyInVerticle.addressBuyIn, json).onComplete(responseCallback(ctx))
        }

    }
}
