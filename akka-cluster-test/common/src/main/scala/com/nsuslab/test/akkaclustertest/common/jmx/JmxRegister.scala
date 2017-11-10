package com.nsuslab.test.akkaclustertest.common.jmx

import java.lang.management.ManagementFactory
import javax.management._

import akka.actor.{ActorContext, PoisonPill}
import akka.cluster.sharding.ClusterSharding
import com.nsuslab.test.akkaclustertest.common.message.{CreateGameMessage, GameEnvelopeMessage, TerminateGameMessage, UpgradeMessage}
import com.nsuslab.test.akkaclustertest.common.shard.GameSharding

case class JMXMBeanDataObject(var objName: String, var objType: String, var stateName: String, var stateData: String)(implicit obj: ActorContext) extends DynamicMBean {
    import javax.management.MBeanAttributeInfo
    import javax.management.MBeanConstructorInfo
    import javax.management.MBeanInfo
    import javax.management.MBeanOperationInfo

    private val dClassName = this.getClass.getName
    private val dDescription = "JMXMBeanDataObject implementation of a dynamic MBean."

    private def buildAttributeList: AttributeList = {
        val attributeList = new AttributeList()
        attributeList.add(new Attribute("Name", objName))
        attributeList.add(new Attribute("Type", objType))
        attributeList.add(new Attribute("StateName", stateName))
        attributeList.add(new Attribute("StateData", stateData))
        attributeList
    }

    override def setAttributes(attributes: AttributeList) = {
        attributes.asList.forEach(
            attribute => attribute.getName match {
                case "Name" => objName = attribute.getValue.asInstanceOf[String]
                case "Type" => objType = attribute.getValue.asInstanceOf[String]
                case "StateName" => stateName = attribute.getValue.asInstanceOf[String]
                case "StateData" => stateData = attribute.getValue.asInstanceOf[String]
            }
        )
        buildAttributeList
    }

    override def setAttribute(attribute: Attribute) = {
        attribute.getName match {
            case "Name" => objName = attribute.getValue.asInstanceOf[String]
            case "Type" => objType = attribute.getValue.asInstanceOf[String]
            case "StateName" => stateName = attribute.getValue.asInstanceOf[String]
            case "StateData" => stateData = attribute.getValue.asInstanceOf[String]
        }
    }

    override def getAttribute(attribute: String) = {
        attribute match {
            case "Name" => objName
            case "Type" => objType
            case "StateName" => stateName
            case "StateData" => stateData
        }
    }

    override def getAttributes(attributes: Array[String]) = {
        val attributeList = new AttributeList()
        attributes.foldLeft(attributeList) {
            (list, attribute) =>
                list.add(attribute match {
                    case "Name" => new Attribute(attribute, objName)
                    case "Type"    => new Attribute(attribute, objType)
                    case "StateName" => new Attribute(attribute, stateName)
                    case "StateData" => new Attribute(attribute, stateData)
                })
                list
        }
    }

    override def getMBeanInfo = {
        val dAttributes: Array[MBeanAttributeInfo] = Array(
            new MBeanAttributeInfo("Name", "java.lang.String", "Name: value string", true, true, false),
            new MBeanAttributeInfo("Type", "java.lang.String", "Type: name string", true, true, false),
            new MBeanAttributeInfo("StateName", "java.lang.String", "StateName: value string", true, true, false),
            new MBeanAttributeInfo("StateData", "java.lang.String", "StateData: value string", true, true, false),
        )
        val dConstructors: Array[MBeanConstructorInfo] = Array()
        val dParams: Array[MBeanParameterInfo] = Array(new MBeanParameterInfo("param01", "java.lang.String", "test params"))
        val dOperations: Array[MBeanOperationInfo] = Array(
            new MBeanOperationInfo("GetActorPath","get actor path",null,"java.lang.String",MBeanOperationInfo.ACTION),
            new MBeanOperationInfo("Stop","stop instance", dParams,null,MBeanOperationInfo.ACTION),
            new MBeanOperationInfo("StopParent","stop parent instance", dParams,null,MBeanOperationInfo.ACTION),
            new MBeanOperationInfo("StopSiblings","stop all sibling instances", dParams,null,MBeanOperationInfo.ACTION),

            new MBeanOperationInfo("Upgrade","upgrade machine", dParams,null,MBeanOperationInfo.ACTION)
        )
        val dNotifications: Array[MBeanNotificationInfo] = Array(
            new MBeanNotificationInfo(Array[String](AttributeChangeNotification.ATTRIBUTE_CHANGE),
                                      "AttributeChangeNotification",
                                      "This notification is emitted when the reset() method is called.")
        )

        new MBeanInfo(dClassName, dDescription, dAttributes, dConstructors, dOperations, dNotifications)
    }

    override def invoke(actionName: String, params: Array[AnyRef], signature: Array[String]) = {
        actionName match {
            case "GetActorPath" =>
                obj.self.path.toString
            case "Stop" =>
                if (params.nonEmpty) {
                    params(0) match {
                        case param: String if param.length > 0 && param.contains('/') =>
                            obj.actorSelection(param) ! PoisonPill

                        case _ =>
                            obj.self ! PoisonPill
                    }
                } else {
                    obj.self ! PoisonPill
                }
                null
            case "StopParent" =>
                if (params.nonEmpty) {
                    params(0) match {
                        case param: String if param.length > 0 && param.contains('/') =>
                            obj.actorSelection(param.substring(0, param.lastIndexOf('/'))) ! PoisonPill
                        case _ =>
                            obj.parent ! PoisonPill
                    }
                } else {
                    obj.parent ! PoisonPill
                }
                null
            case "StopSiblings" =>
                if (params.nonEmpty) {
                    params(0) match {
                        case param: String if param.length > 0 && param.contains('/') =>
                            obj.actorSelection(param.substring(0, param.lastIndexOf('/'))+"/*") ! PoisonPill
                        case _ =>
                            obj.actorSelection(obj.parent.path.toString+"/*") ! PoisonPill
                    }
                } else {
                    obj.actorSelection(obj.parent.path.toString+"/*") ! PoisonPill
                }
                null
            case "Upgrade" =>
                if (params.nonEmpty) {
                    params(0) match {
                        case param: String if param.length > 0 && param.contains('/') =>
                            obj.actorSelection(param.substring(0, param.lastIndexOf('/'))+"/*") ! UpgradeMessage
                        case _ =>
                            obj.actorSelection(obj.parent.path.toString+"/*") ! UpgradeMessage
                    }
                } else {
                    obj.actorSelection(obj.parent.path.toString+"/*") ! UpgradeMessage
                }
                null
        }
    }
}

case class JMXMBeanManagerObject(var objName: String, var objType: String)(implicit obj: ActorContext) extends DynamicMBean {
    import javax.management.MBeanAttributeInfo
    import javax.management.MBeanConstructorInfo
    import javax.management.MBeanInfo
    import javax.management.MBeanOperationInfo

    private val dClassName = this.getClass.getName
    private val dDescription = "JMXMBeanManagerObject implementation of a dynamic MBean."

    private def buildAttributeList: AttributeList = {
        val attributeList = new AttributeList()
        attributeList.add(new Attribute("Name", objName))
        attributeList.add(new Attribute("Type", objType))
        attributeList
    }

    override def setAttributes(attributes: AttributeList) = {
        attributes.asList.forEach(
            attribute => attribute.getName match {
                case "Name" => objName = attribute.getValue.asInstanceOf[String]
                case "Type" => objType = attribute.getValue.asInstanceOf[String]
            }
        )
        buildAttributeList
    }

    override def setAttribute(attribute: Attribute) = {
        attribute.getName match {
            case "Name" => objName = attribute.getValue.asInstanceOf[String]
            case "Type" => objType = attribute.getValue.asInstanceOf[String]
        }
    }

    override def getAttribute(attribute: String) = {
        attribute match {
            case "Name" => objName
            case "Type" => objType
        }
    }

    override def getAttributes(attributes: Array[String]) = {
        val attributeList = new AttributeList()
        attributes.foldLeft(attributeList) {
            (list, attribute) =>
                list.add(attribute match {
                    case "Name" => new Attribute(attribute, objName)
                    case "Type" => new Attribute(attribute, objType)
                })
                list
        }
    }

    override def getMBeanInfo = {
        val dAttributes: Array[MBeanAttributeInfo] = Array(
            new MBeanAttributeInfo("Name", "java.lang.String", "Name: value string", true, true, false),
            new MBeanAttributeInfo("Type", "java.lang.String", "Type: name string", true, true, false),
        )

        val dConstructors: Array[MBeanConstructorInfo] = Array()
        val dStringParams: Array[MBeanParameterInfo] = Array(new MBeanParameterInfo("paramString", "java.lang.String", "string params"))
        val dLongParams: Array[MBeanParameterInfo] = Array(new MBeanParameterInfo("paramLong", "java.lang.Long", "long params"))
        val dOperations: Array[MBeanOperationInfo] = Array(
            new MBeanOperationInfo("GetActorPath","get actor path",null,"java.lang.String",MBeanOperationInfo.ACTION),
            new MBeanOperationInfo("StartGameByOptionID","start a game by option id",dLongParams,null,MBeanOperationInfo.ACTION),
            new MBeanOperationInfo("StopGameByOptionID","stop a game by option id",dLongParams,null,MBeanOperationInfo.ACTION)
        )
        val dNotifications: Array[MBeanNotificationInfo] = Array(
            new MBeanNotificationInfo(Array[String](AttributeChangeNotification.ATTRIBUTE_CHANGE),
                "AttributeChangeNotification",
                "This notification is emitted when the reset() method is called.")
        )

        new MBeanInfo(dClassName, dDescription, dAttributes, dConstructors, dOperations, dNotifications)
    }

    override def invoke(actionName: String, params: Array[AnyRef], signature: Array[String]) = {
        actionName match {
            case "GetActorPath" =>
                obj.self.path.toString
            case "StartGameByOptionID" =>
                if (params.nonEmpty) {
                    params(0) match {
                        case param: java.lang.Long =>
                            ClusterSharding(obj.system).shardRegion(GameSharding.shardName) !
                                    GameEnvelopeMessage(param, param,CreateGameMessage(param,"",0,0))
                        case _ =>
                    }
                }
                null
            case "StopGameByOptionID" =>
                if (params.nonEmpty) {
                    params(0) match {
                        case param: java.lang.Long =>
                            ClusterSharding(obj.system).shardRegion(GameSharding.shardName) !
                                    GameEnvelopeMessage(param, param,TerminateGameMessage(param))
                        case _ =>
                    }
                }
                null
        }
    }
}

object AkkaJmxRegister {
    private lazy val mbs: MBeanServer = ManagementFactory.getPlatformMBeanServer

    @throws[InstanceAlreadyExistsException]
    @throws[MBeanRegistrationException]
    @throws[RuntimeMBeanException]
    @throws[RuntimeErrorException]
    @throws[NotCompliantMBeanException]
    @throws[RuntimeOperationsException]
    def registerToMBeanServer(data: DynamicMBean, objName: ObjectName): ObjectInstance = {
        mbs.registerMBean(data, objName)
    }

    @throws[RuntimeOperationsException]
    @throws[RuntimeMBeanException]
    @throws[RuntimeErrorException]
    @throws[InstanceNotFoundException]
    @throws[MBeanRegistrationException]
    def unregisterFromMBeanServer(objName: ObjectName): Unit = {
        mbs.unregisterMBean(objName)
    }
}