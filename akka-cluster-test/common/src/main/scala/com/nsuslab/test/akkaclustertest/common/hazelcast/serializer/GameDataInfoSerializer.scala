package com.nsuslab.test.akkaclustertest.common.hazelcast.serializer

import com.fasterxml.jackson.databind.ObjectReader
import com.hazelcast.nio.{ObjectDataInput, ObjectDataOutput}
import com.hazelcast.nio.serialization.StreamSerializer
import com.nsuslab.test.akkaclustertest.common.database.entity.GameDataInfo
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, ObjectReader, SerializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

object JsonUtil {
    private val om = new ObjectMapper() with ScalaObjectMapper
    om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new DefaultScalaModule)

    def reader(t: Class[_]): ObjectReader = om.readerFor(t)
    def writer() = om.writer()
    def mapper() = om
}

class GameDataInfoSerializer extends StreamSerializer[GameDataInfo] {
    private val reader: ObjectReader = JsonUtil.reader(classOf[GameDataInfo])
    private val writer = JsonUtil.writer()
    override def read(in: ObjectDataInput): GameDataInfo = {
        reader.readValue(in)
    }
    override def write(out: ObjectDataOutput, obj: GameDataInfo) = {
        writer.writeValue(out, obj)
    }
    override def destroy() = {}
    override def getTypeId = 101
}
