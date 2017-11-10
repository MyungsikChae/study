package com.nsuslab.test.akkaclustertest.common.database.jpa

import org.hibernate.boot.model.naming.{Identifier, PhysicalNamingStrategyStandardImpl}
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment

class PhysicalNamingStrategy extends PhysicalNamingStrategyStandardImpl with Serializable {
    import PhysicalNamingStrategy._
    override def toPhysicalColumnName(name: Identifier, context: JdbcEnvironment) = {
        new Identifier(addUnderscores(name.getText), name.isQuoted)
     }
    override def toPhysicalTableName(name: Identifier, context: JdbcEnvironment) = {
        new Identifier(addUnderscores(name.getText), name.isQuoted)
     }
}

object PhysicalNamingStrategy {
    private val INSTANCE = new PhysicalNamingStrategy
    private def addUnderscores(name: String): String =  {
        name.replaceAll("([a-z])([A-Z\\d])", "$1_$2")
                .replaceAll("([A-Z\\d])([A-Z][a-z])", "$1_$2")
                .toUpperCase
    }

    def getInstance = INSTANCE
}