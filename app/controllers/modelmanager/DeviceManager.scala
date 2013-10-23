package controllers.modelmanager

import javax.persistence.{TemporalType, NoResultException, EntityManager}
import models._
import controllers.util.{DataImporter, JPAUtil}
import scala.collection.JavaConversions._
import java.util.Date
import scala.reflect._
import java.lang.Boolean
import scala.Some
import models.Device

object DeviceManager {
  /**
   * Get a sensor by Id
   * @param sIdList A list of sensor Ids
   * @return The sensor
   */
  def getById(sIdList: List[Long], emOpt: Option[EntityManager]): Map[Long, Device] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[Device].getName +" WHERE id IN ("+ sIdList.mkString(",") +")")
      val devices = q.getResultList.map(_.asInstanceOf[Device]).map(s => (s.getId.toLong, s)).toMap
      if (emOpt.isEmpty) em.getTransaction().commit()
      devices
    } catch {
      case nre: NoResultException => Map()
      case ex: Exception => ex.printStackTrace; Map()
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  /**
   * Get a device by name and address
   * @param name The device name
   * @param address The device address
   * @param emOpt An optional entity manager to avoid created many transactions for multiple consecutive queries
   * @return The device
   */
  def getByNameAndAddress(name: String, address: String, emOpt: Option[EntityManager] = None): Option[Device] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[Device].getName +" where name = '"+ name +"' and address = '"+ address +"'")
      val device = q.getSingleResult.asInstanceOf[Device]
      if (emOpt.isEmpty) em.getTransaction().commit()
      Some(device)
    } catch {
      case nre: NoResultException => None
      case ex: Exception => ex.printStackTrace; None
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  /**
   * Get the devices that are associated with a mission
   * @param missionId The id of the mission
   * @param datatype The type of data to get
   * @param address The address of the device
   * @param emOpt An optional entity manager to avoid created many transactions for multiple consecutive queries
   * @return A list of devices
   */
  def getForMission(missionId: Long, datatype: Option[String], address: Option[String], emOpt: Option[EntityManager] = None): List[Device] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    val typeMap = collection.mutable.Map[String, Int]()
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      // TODO - improve many to many request
      val typeCondition = if (datatype.isDefined) " AND d.datatype = '"+ datatype.get +"'" else ""
      val addressCondition = if (address.isDefined) " AND d.address = '"+ address.get +"'" else ""
      val query = "SELECT d.id, d.name FROM equipment AS e, device AS d WHERE e.mission_id = "+ missionId +" AND e.device_id = d.id"+ typeCondition + addressCondition+ " ORDER BY name"
      val q = em.createNativeQuery(query)
      val devices = q.getResultList.map(_.asInstanceOf[Array[Object]]).toList.map(obj => {
        val dId = obj(0).asInstanceOf[Int]
        val d = getById(List(dId), Some(em)).get(dId).get
        if (!typeMap.contains(d.getDatatype)) {
          typeMap += d.getDatatype -> 1
        } else {
          typeMap(d.getDatatype) = (typeMap(d.getDatatype) + 1)
        }
        d
      })
      // add a virtual sensor that will appear as "All temperature" in map interface
      val virtualDeviceList = for {
        t <- typeMap
        if (t._2 > 1)
      } yield {
        new Device(0, t._1, "", t._1)
      }
      if (emOpt.isEmpty) em.getTransaction().commit()
      devices ++ virtualDeviceList
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  /**
   * Get active devices by time range
   * @param from start time of range
   * @param to end time of range
   * @param dataType type of the device
   * @return A list of devices (active during specified time range)
   */
  def getByDatetime(from: Date, to: Date, dataType: Option[String] = None): List[Device] = {
    /*SELECT DISTINCT s.id, s.name
    FROM temperaturelog tl, sensor s where timestamp BETWEEN '2013-06-13 16:20:00'::timestamp
      AND '2013-06-13 16:31:25'::timestamp AND sensor_id = s.id AND s.name like 'PT%';*/
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val temperatureSensors = getActiveSensorByTimeInterval[TemperatureLog](from, to, false, Some(em))
      val windSensors = getActiveSensorByTimeInterval[WindLog](from, to, false, Some(em))
      val radiometerSensors = getActiveSensorByTimeInterval[RadiometerLog](from, to, false, Some(em))
      val temperatureType = if (temperatureSensors.nonEmpty && dataType.isEmpty) {
        // add a virtual sensor that will appear as "All temperature" in map interface
        val typeSensor = new Device(0, "temperature", "", DataImporter.Types.TEMPERATURE)
        List(typeSensor)
      } else List()
      val radiometerType = if (radiometerSensors.nonEmpty && dataType.isEmpty) {
        // add a virtual sensor that will appear as "All radiometer" in map interface
        val typeSensor = new Device(0, "radiometer", "", DataImporter.Types.RADIOMETER)
        List(typeSensor)
      } else List()
      val devices = temperatureSensors ++ temperatureType ++ windSensors ++ radiometerSensors ++ radiometerType

      em.getTransaction().commit()
      val filteredDevices = if (dataType.isDefined) devices.filter(_.getDatatype == dataType.get) else devices
      filteredDevices.sortBy(_.getName)
    } catch {
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      em.close()
    }
  }

  /**
   * Get the data logs of a type between a time interval
   * @param startTime The start time of the interval
   * @param endTime The end time of the interval
   * @param geoOnly Indicates if we want only the logs mapped to a gps log
   * @tparam T The type of data log to get
   * @return A list of the logs in the specified time interval
   */
  private def getActiveSensorByTimeInterval[T: ClassTag](startTime: Date, endTime: Date, geoOnly: Boolean, emOpt: Option[EntityManager] = None): List[Device] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val clazz = classTag[T].runtimeClass
      val geoCondition = if (geoOnly) "AND log.gpsLog IS NOT NULL " else ""
      // where timestamp BETWEEN '2013-05-14 16:30:00'::timestamp AND '2013-05-14 16:33:25'::timestamp ;
      val queryStr = "SELECT DISTINCT log.sensor from "+ clazz.getName +" log " +
        "WHERE timestamp BETWEEN :start AND :end " + geoCondition
      //println(queryStr)

      val q = em.createQuery(queryStr)
      q.setParameter("start", startTime, TemporalType.TIMESTAMP)
      q.setParameter("end", endTime, TemporalType.TIMESTAMP)
      //println(q.getResultList)
      val sensors = q.getResultList.map(_.asInstanceOf[Device]).toList
      if (emOpt.isEmpty) em.getTransaction().commit()
      sensors
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

}
