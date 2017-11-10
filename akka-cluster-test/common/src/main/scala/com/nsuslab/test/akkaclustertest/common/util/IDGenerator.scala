package com.nsuslab.test.akkaclustertest.common.util

import java.time.ZonedDateTime

import com.hazelcast.core.{Hazelcast, HazelcastInstance}
import com.noctarius.snowcast.{Snowcast, SnowcastEpoch, SnowcastSequencer, SnowcastSystem}

import scala.collection.mutable

object IDGenerator {
    private val sequencerMap: mutable.Map[String, SnowcastSequencer] = mutable.Map.empty
    private var snowcast: Option[Snowcast] = None

    def init(): Unit = {
        val hazelcastInstance: HazelcastInstance = Hazelcast.getHazelcastInstanceByName("hazelcast")
        if (hazelcastInstance != null) snowcast = Some(SnowcastSystem.snowcast(hazelcastInstance, 3))
    }

    def apply(name: String, prefix: String, epochYear: Int): IdGenerator =
        new IdGenerator(prefix, getOrCreate(name, epochYear).get)

    def apply(name: String, prefix: String, mid: String, epochYear: Int): IdGenerator =
        new IdGenerator(prefix + ":" + mid, getOrCreate(name, epochYear).get)

    private def getOrCreate(name: String, epochYear: Int): Option[SnowcastSequencer] = {
        val maybeSequencer = sequencerMap.get(name)
        if (maybeSequencer.nonEmpty) {
            maybeSequencer
        } else {
            if (snowcast.nonEmpty) {
                val sequencer: SnowcastSequencer = snowcast.get.createSequencer(name,
                    SnowcastEpoch.byInstant(ZonedDateTime.now().minusYears(epochYear).toInstant))
                sequencerMap += name -> sequencer
                Some(sequencer)
            } else None
        }
    }
    def destroy(): Unit = if (snowcast.isDefined && sequencerMap.nonEmpty) {
        sequencerMap.values.foreach(snowcast.get.destroySequencer)
        snowcast = None
    }
}

class IdGenerator(val name: String, val sequencer: SnowcastSequencer) {
    def next: String = name + ":" + sequencer.next()
}