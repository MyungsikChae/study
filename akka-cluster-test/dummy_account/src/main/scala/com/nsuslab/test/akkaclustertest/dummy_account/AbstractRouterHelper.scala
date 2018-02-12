package com.nsuslab.test.akkaclustertest.dummy_account

import com.typesafe.scalalogging.LazyLogging
import io.vertx.core.eventbus.ReplyException
import io.vertx.core.http.HttpMethod
import io.vertx.lang.scala.json.JsonObject
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.eventbus.Message
import io.vertx.scala.ext.web.{Router, RoutingContext}

import scala.util.{Failure, Success, Try}

trait AbstractRouterHelper extends LazyLogging {
    def deployVerticle(vertx: Vertx): Unit
    def mountRouter(vertx: Vertx, mainRouter: Router): Unit
    protected def responseCallback(ctx: RoutingContext) = (m: Try[Message[String]]) => m match {
        case Success(result)                =>
            logger.debug(s"${ctx.request.method()} ${ctx.request.uri()} response successfully")
            ctx.response().end(result.body())
        case Failure(cause: ReplyException) =>
            logger.warn(s"${ctx.request.method()} ${ctx.request.uri()} request failed", cause)
            ctx.response().setStatusCode(cause.failureCode()).end()
        case Failure(cause)                 =>
            logger.error(s"${ctx.request.method()} ${ctx.request.uri()} request failed", cause)
            ctx.response().setStatusCode(500).setStatusMessage(cause.getMessage).end()
    }
    protected def noresponseCallback[T](ctx: RoutingContext) = (m: Try[Message[T]]) => m match {
        case Success(_)                     =>
            logger.debug(s"${ctx.request.method()} ${ctx.request.uri()} response successfully")
            ctx.response().setStatusCode(ctx.request.method() match {
                case HttpMethod.POST | HttpMethod.PUT => 201
                case _                                => 200
            }).end()
        case Failure(cause: ReplyException) =>
            logger.warn(s"${ctx.request.method()} ${ctx.request.uri()} request failed", cause)
            ctx.response().setStatusCode(cause.failureCode()).end()
        case Failure(cause)                 =>
            logger.error(s"${ctx.request.method()} ${ctx.request.uri()} request failed", cause)
            ctx.response().setStatusCode(500).setStatusMessage(cause.getMessage).end()
    }
    protected def extractUserId(ctx: RoutingContext) = ctx.session() match {
        case Some(session) =>
            val userId = session.get[String]("userId")
            if (userId == null) "scheduler"
            else userId
        case None          => "scheduler"
    }
    protected def fillParam(params: JsonObject, ctx: RoutingContext, paramName: String): String = {
        val value = ctx.request().getParam(paramName)
        if (value.isDefined) {
            params.put(paramName, value.get)
            value.get
        } else null
    }

}
