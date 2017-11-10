package com.nsuslab.test.akkaclustertest.common.hazelcast.mapper

import java.{lang, util}
import java.util.Properties
import javax.persistence.EntityManager

import com.hazelcast.core.{HazelcastInstance, MapLoaderLifecycleSupport, MapStore}
import com.nsuslab.test.akkaclustertest.common.database.entity.{GameDataInfo, QGameDataInfo}
import com.nsuslab.test.akkaclustertest.common.database.jpa.JpaHelper
import com.querydsl.jpa.impl.JPAQueryFactory

import scala.collection.JavaConverters._

class GameDataMapStore extends MapStore[Long, GameDataInfo] with MapLoaderLifecycleSupport {
    private val gameInfo = QGameDataInfo.gameDataInfo

    override def deleteAll(keys: util.Collection[Long]) = trx() { em =>
        val queryFactory = new JPAQueryFactory(em)
        queryFactory.delete(gameInfo)
                .where(gameInfo.gameId in keys.asScala.map(key => new lang.Long(key)).asJavaCollection)
                .execute()
    }

    override def store(key: Long, value: GameDataInfo) = trx() { em =>
        _merge(em, value)
    }

    override def delete(key: Long) = trx() { em =>
        val queryFactory = new JPAQueryFactory(em)
        queryFactory.delete(gameInfo)
                .where(gameInfo.gameId eq key)
                .execute()
    }

    override def storeAll(map: util.Map[Long, GameDataInfo]) = trx() { em =>
        map.values().forEach(g => _merge(em, g))
    }

    override def init(hazelcastInstance: HazelcastInstance, properties: Properties, mapName: String) = {}

    override def destroy() = {}

    override def loadAllKeys(): java.lang.Iterable[Long] = query[java.lang.Iterable[Long]](){
        _.select(gameInfo.gameId).from(gameInfo).fetchAll().fetch().asScala.map(key => key.toLong).asJava
    }

    override def loadAll(keys: util.Collection[Long]) = query[util.Map[Long, GameDataInfo]]() {
        _
                .selectFrom(gameInfo)
                .where(gameInfo.gameId in keys.asScala.map(key => new lang.Long(key)).asJavaCollection)
                .fetchAll().fetch().asScala.map(info => info.getGameId -> info).toMap.asJava
    }

    override def load(key: Long) = query[GameDataInfo]() {
        _.selectFrom(gameInfo).where(gameInfo.gameId eq key).fetchOne()
    }

    private def trx()(f: (EntityManager) => Unit): Unit = {
        val em = JpaHelper.createSession
        val tx = em.getTransaction
        try {tx.begin(); f(em); tx.commit()}
        catch {case e: Throwable => tx.rollback(); e.printStackTrace()}
        finally {em.close() }
    }
    private def query[T >: Null]()(f: (JPAQueryFactory) => T): T = {
        val em = JpaHelper.createSession
        var ret: T = null
        try {ret = f(new JPAQueryFactory(em))} finally {em.close() }
        ret
    }
    private def _merge(em: EntityManager, g: GameDataInfo) = em.merge(g)
}
