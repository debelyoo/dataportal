package models.old

import javax.persistence._
import controllers.util.{DataImporter, JPAUtil}
import org.hibernate.annotations.GenericGenerator
import java.lang.Boolean
import com.google.gson.Gson
import java.util.Date
import scala.collection.JavaConversions._
import scala.Some
import scala.reflect.{ClassTag, classTag}
import java.util.HashSet
import models.{CompassLog, RadiometerLog, WindLog, TemperatureLog}

@Entity
@Table(name = "devicescala")
case class DeviceScala(name: String, address: String, datatype: String) {

  @Id
  @GeneratedValue(generator="increment")
  @GenericGenerator(name="increment", strategy = "increment")
  @Column(name = "device_id", unique = true, nullable = false)
  var id: Long = _

  override def toString = id + " -> name: " + name + ", address: " + address + ", type: "+ datatype

  def toJson: String = {
    val gson = new Gson()
    gson.toJson(this)
  }

  def this() = this("foo", "bar", "xx") // default constructor - necessary to work with hibernate (otherwise not possible to do select queries)

  /**
   * Persist device in DB (if it is not already in)
   * @return
   */
  def save: Boolean = {
    val sensorInDb = DeviceScala.getByNameAndAddress(this.name, this.address)
    if (sensorInDb.isEmpty) {
      //println("[Sensor] save() - "+ this.toString)
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        em.getTransaction.begin
        em.persist(this)
        em.getTransaction.commit
        true
      } catch {
        case ex: Exception => {
          false
        }
      } finally {
        em.close
      }
    } else {
      true
    }
  }

  /**
   * Update the data type of the sensor
   * @param newType The new type to set
   * @return true if success
   */
  /*def updateType(newType: String): Boolean = {
    println("[Device] update() - "+ this.toString)
    val em: EntityManager = JPAUtil.createEntityManager
    // UPDATE sensor SET datatype='temperature' WHERE id=16;
    try {
      em.getTransaction.begin
      val queryStr = "UPDATE "+ classOf[Device].getName +" SET datatype='"+ newType +"' WHERE id="+this.id
      val q = em.createQuery(queryStr)
      q.executeUpdate()
      em.getTransaction.commit
      true
    } catch {
      case ex: Exception => false
    } finally {
      em.close
    }
  }*/
}

object DeviceScala {
  /**
   * Get a sensor by Id
   * @param sIdList A list of sensor Ids
   * @return The sensor
   */
  def getById(sIdList: List[Long], emOpt: Option[EntityManager]): Map[Long, DeviceScala] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[DeviceScala].getName +" WHERE id IN ("+ sIdList.mkString(",") +")")
      //q.setParameter("id",sId)
      val sensors = q.getResultList.map(_.asInstanceOf[DeviceScala]).map(s => (s.id, s)).toMap
      if (emOpt.isEmpty) em.getTransaction().commit()
      sensors
      //Some(sensor)
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
   * @return The device
   */
  def getByNameAndAddress(name: String, address: String): Option[DeviceScala] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[DeviceScala].getName +" where name = '"+ name +"' and address = '"+ address +"'")
      val device = q.getSingleResult.asInstanceOf[DeviceScala]
      em.getTransaction().commit()
      em.close()
      Some(device)
    } catch {
      case nre: NoResultException => None
      case ex: Exception => ex.printStackTrace; None
    }
  }

  def getForMission(missionId: Long, datatype: Option[String]): List[DeviceScala] = {
    val em = JPAUtil.createEntityManager()
    val typeMap = collection.mutable.Map[String, Int]()
    try {
      em.getTransaction().begin()
      // TODO - improve many to many request
      val typeCondition = if (datatype.isDefined) " AND d.datatype = '"+ datatype.get +"'" else ""
      val query = "SELECT d.id, d.name FROM equipment AS e, device AS d WHERE e.mission_id = "+ missionId +" AND e.device_id = d.id"+ typeCondition +" ORDER BY name"
      val q = em.createNativeQuery(query)
      val devices = q.getResultList.map(_.asInstanceOf[Array[Object]]).toList.map(obj => {
        val dId = obj(0).asInstanceOf[Int]
        val d = DeviceScala.getById(List(dId), Some(em)).get(dId).get
        if (!typeMap.contains(d.datatype)) {
          typeMap += d.datatype -> 1
        } else {
          typeMap(d.datatype) = (typeMap(d.datatype) + 1)
        }
        d
      })
      // add a virtual sensor that will appear as "All temperature" in map interface
      val virtualDeviceList = for {
        t <- typeMap
        if (t._2 > 1)
      } yield {
        DeviceScala(t._1, "", t._1)
      }
      em.getTransaction().commit()
      devices ++ virtualDeviceList
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      em.close()
    }
  }

  /**
   * Get active devices by time range
   * @param from start time of range
   * @param to end time of range
   * @param dataType type of the device
   * @return A list of devices (active during specified time range)
   */
  def getByDatetime(from: Date, to: Date, dataType: Option[String] = None): List[DeviceScala] = {
    /*SELECT DISTINCT s.id, s.name
    FROM temperaturelog tl, sensor s where timestamp BETWEEN '2013-06-13 16:20:00'::timestamp
      AND '2013-06-13 16:31:25'::timestamp AND sensor_id = s.id AND s.name like 'PT%';*/
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val temperatureSensors = getActiveSensorByTimeInterval[TemperatureLog](from, to, false, Some(em))
      val windSensors = getActiveSensorByTimeInterval[WindLog](from, to, false, Some(em))
      val radiometerSensors = getActiveSensorByTimeInterval[RadiometerLog](from, to, false, Some(em))
      val compassSensors = getActiveSensorByTimeInterval[CompassLog](from, to, false, Some(em))
      val temperatureType = if (temperatureSensors.nonEmpty && dataType.isEmpty) {
        // add a virtual sensor that will appear as "All temperature" in map interface
        val typeSensor = DeviceScala("temperature", "", DataImporter.Types.TEMPERATURE)
        List(typeSensor)
      } else List()
      val radiometerType = if (radiometerSensors.nonEmpty && dataType.isEmpty) {
        // add a virtual sensor that will appear as "All radiometer" in map interface
        val typeSensor = DeviceScala("radiometer", "", DataImporter.Types.RADIOMETER)
        List(typeSensor)
      } else List()
      val devices = temperatureSensors ++ temperatureType ++ windSensors ++ radiometerSensors ++ radiometerType ++ compassSensors

      em.getTransaction().commit()
      val filteredDevices = if (dataType.isDefined) devices.filter(_.datatype == dataType.get) else devices
      filteredDevices.sortBy(_.name)
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
  private def getActiveSensorByTimeInterval[T: ClassTag](startTime: Date, endTime: Date, geoOnly: Boolean, emOpt: Option[EntityManager] = None): List[DeviceScala] = {
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
      val sensors = q.getResultList.map(_.asInstanceOf[DeviceScala]).toList
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
