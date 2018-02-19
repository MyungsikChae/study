package com.nsuslab.test.akkaclustertest.dummy_account.account.verticles


import com.google.gson
import com.google.gson.Gson
import com.nsuslab.test.akkaclustertest.dummy_account.account.dto.LoginDto
import io.vertx.core.json.{Json, JsonObject}
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.eventbus.EventBus


object LoginVerticle {
    val addressLogin = "ACCOUNT::LOGIN"
}

class LoginVerticle extends ScalaVerticle {
    override def start(): Unit = {
        val eb: EventBus = vertx.eventBus()
        eb.localConsumer[JsonObject](LoginVerticle.addressLogin).handler {
            msg =>
                val data = new Gson().toJson(LoginDto(msg.body().getString("userId"), msg.body().getString("userPw"), 200, "Success"))
                msg.reply(data)
        }
    }
}
