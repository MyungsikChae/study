package com.nsuslab.test.akkaclustertest.dummy_account.account.verticles

import java.util.UUID

import com.google.gson.Gson
import com.nsuslab.test.akkaclustertest.dummy_account.account.dto.BuyInDto
import io.vertx.core.json.{Json, JsonObject}
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.eventbus.EventBus

object BuyInVerticle {
    val addressBuyIn = "ACCOUNT::BUYIN"
}

class BuyInVerticle extends ScalaVerticle {
    override def start(): Unit = {
        val eb: EventBus = vertx.eventBus()
        eb.localConsumer[JsonObject](BuyInVerticle.addressBuyIn).handler {
            msg =>
                val uuid = UUID.randomUUID()
                val data = new Gson().toJson(BuyInDto(uuid.toString, msg.body().getLong("buyInAmount"), 200, "Success"))
                msg.reply(data)
        }
    }
}
