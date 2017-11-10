package com.nsuslab.test.akkaclustertest.common.hazelcast.repository

import akka.actor.ActorRef
import com.hazelcast.core.IMap
import com.nsuslab.test.akkaclustertest.common.hazelcast.HazelcastBroker

object TableMaintenance {
    def getJoinMap(tableId: String): IMap[String, Boolean] = HazelcastBroker.getMap[String, Boolean]("TABLE:JOIN-TABLE:" + tableId)

    // upgrade
    private val ON_UPGRADE: Int = 1
    private val NOT_ON_UPGRADE: Int = 0
    //
    private val downingRegions: IMap[String, Integer] = HazelcastBroker.getMap[String, Integer]("TABLE-SHARDREGIONs-DOWNING")
    def isOnUpgrade: Boolean = downingRegions.containsValue(ON_UPGRADE)
    def isOnShutdown(regionAddress: String): Boolean = downingRegions.get(regionAddress) == ON_UPGRADE
    def tryStartShutdown(regionAddress: String): Boolean = {
        if (downingRegions.containsKey(regionAddress)) downingRegions.replace(regionAddress, NOT_ON_UPGRADE, ON_UPGRADE)
        else downingRegions.putIfAbsent(regionAddress, ON_UPGRADE) == null
    }
    def tryFinishShutdown(regionAddress: String): Boolean = downingRegions.replace(regionAddress, ON_UPGRADE, NOT_ON_UPGRADE)
    // rebalancing
    private val HAND_RUNNING: Int = 0
    private val HAND_END: Int = 1
    private val REBALANCE_TARGET: Int = 2
    //
    private val readiedTables: IMap[String, Integer] = HazelcastBroker.getMap[String, Integer]("READY-DOWN-TABLE-SHARDs")
    def tryFinishHandOff(tableId: String): Boolean = {
        if (readiedTables.containsKey(tableId)) readiedTables.replace(tableId, REBALANCE_TARGET, HAND_RUNNING)
        else readiedTables.putIfAbsent(tableId, HAND_RUNNING) == null
    }
    def tryStartHandOff(tableId: String): Boolean = readiedTables.replace(tableId, HAND_END, REBALANCE_TARGET)
    def tryUnpreparedToHandOff(tableId: String): Boolean = {
        if (readiedTables.containsKey(tableId)) readiedTables.replace(tableId, HAND_END, HAND_RUNNING)
        else readiedTables.putIfAbsent(tableId, HAND_RUNNING) == null
    }
    def tryPreparedToHandOff(tableId: String): Boolean = {
        if (readiedTables.containsKey(tableId)) readiedTables.replace(tableId, HAND_RUNNING, HAND_END)
        else readiedTables.putIfAbsent(tableId, HAND_END) == null
    }
    def isOnHandOff(tableId: String): Boolean = readiedTables.get(tableId) == REBALANCE_TARGET

}
