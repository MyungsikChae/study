package com.nsuslab.test.akkaclustertest.common.database.jpa

import javax.persistence.{EntityManagerFactory, Persistence}

object JpaHelper {
    private val PERSISTENCE_UNIT_NAME = "gamedata"
    private var _factory: EntityManagerFactory = _
    def createSession = {
        if (_factory == null || !_factory.isOpen) _factory = createFactory()
        _factory.createEntityManager
    }
    def createFactory(): EntityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME)
    def destroy(): Unit = {
        if (_factory != null && _factory.isOpen) _factory.close()
        _factory = null
    }
}
