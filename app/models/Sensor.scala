package models

import javax.persistence._
import controllers.util.JPAUtil
import org.hibernate.annotations.GenericGenerator
import java.lang.Boolean
import models.spatial._
import com.google.gson.Gson
import java.util.Date
import scala.collection.JavaConversions._
import scala.Some

@Entity
@Table(name = "sensor")
case class Sensor(name: String, address: String, datatype: String) {

  @Id
  @GeneratedValue(generator="increment")
  @GenericGenerator(name="increment", strategy = "increment")
  var id: Long = _

  override def toString = id + " -> name: " + name + ", address: " + address + ", type: "+ datatype

  def toJson: String = {
    val gson = new Gson()
    gson.toJson(this)
  }

  def this() = this("foo", "bar", "xx") // default constructor - necessary to work with hibernate (otherwise not possible to do select queries)

  /**
   * Persist sensor in DB (if it is not already in)
   * @return
   */
  def save: Boolean = {
    val sensorInDb = Sensor.getByNameAndAddress(this.name, this.address)
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
  def updateType(newType: String): Boolean = {
    println("[Sensor] update() - "+ this.toString)
    val em: EntityManager = JPAUtil.createEntityManager
    // UPDATE sensor SET datatype='temperature' WHERE id=16;
    try {
      em.getTransaction.begin
      val queryStr = "UPDATE "+ classOf[Sensor].getName +" SET datatype='"+ newType +"' WHERE id="+this.id
      val q = em.createQuery(queryStr)
      q.executeUpdate()
      em.getTransaction.commit
      true
    } catch {
      case ex: Exception => false
    } finally {
      em.close
    }
  }
}

object Sensor {
  /**
   * Get a sensor by Id
   * @param sIdList A list of sensor Ids
   * @return The sensor
   */
  def getById(sIdList: List[Long], emOpt: Option[EntityManager]): Map[Long, Sensor] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[Sensor].getName +" WHERE id IN ("+ sIdList.mkString(",") +")")
      //q.setParameter("id",sId)
      val sensors = q.getResultList.map(_.asInstanceOf[Sensor]).map(s => (s.id, s)).toMap
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
   * Get a sensor by name and address
   * @param name The sensor name
   * @param address The sensor address
   * @return The sensor
   */
  def getByNameAndAddress(name: String, address: String): Option[Sensor] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[Sensor].getName +" where name = '"+ name +"' and address = '"+ address +"'")
      val sensor = q.getSingleResult.asInstanceOf[Sensor]
      em.getTransaction().commit()
      em.close()
      Some(sensor)
    } catch {
      case nre: NoResultException => None
      case ex: Exception => ex.printStackTrace; None
    }
  }

  /**
   * Get active sensors by time range
   * @param from start time of range
   * @param to end time of range
   * @return A list of sensors (active during specified time range)
   */
  def getByDatetime(from: Date, to: Date): List[Sensor] = {
    /*SELECT DISTINCT s.id, s.name
    FROM temperaturelog tl, sensor s where timestamp BETWEEN '2013-06-13 16:20:00'::timestamp
      AND '2013-06-13 16:31:25'::timestamp AND sensor_id = s.id AND s.name like 'PT%';*/
    val em = JPAUtil.createEntityManager()
    try {
      //em.getTransaction().begin()
      /*val q = em.createQuery("SELECT DISTINCT s FROM "+ classOf[Sensor].getName +" s, " +
        classOf[TemperatureLog].getName +" tl WHERE tl.sensor = s " +
        "AND timestamp BETWEEN :start AND :end", classOf[Sensor])
      q.setParameter("start", from, TemporalType.TIMESTAMP)
      q.setParameter("end", to, TemporalType.TIMESTAMP)
      val q2 = em.createQuery("SELECT DISTINCT s FROM "+ classOf[Sensor].getName +" s, " +
        classOf[WindLog].getName +" wl WHERE wl.sensor = s " +
        "AND timestamp BETWEEN :start AND :end", classOf[Sensor])
      q2.setParameter("start", from, TemporalType.TIMESTAMP)
      q2.setParameter("end", to, TemporalType.TIMESTAMP)
      val sensors = q.getResultList.toList ++ q2.getResultList.toList
      */
      val temperatureSensors = DataLogManager.getByTimeInterval[TemperatureLog](from, to, false, Some(em)).map(_.getSensor).distinct
      val windSensors = DataLogManager.getByTimeInterval[WindLog](from, to, false, Some(em)).map(_.getSensor).distinct
      val radiometerSensors = DataLogManager.getByTimeInterval[RadiometerLog](from, to, false, Some(em)).map(_.getSensor).distinct
      val compassSensors = DataLogManager.getByTimeInterval[CompassLog](from, to, false, Some(em)).map(_.getSensor).distinct
      val sensors = temperatureSensors ++ windSensors ++ radiometerSensors ++ compassSensors

      //em.getTransaction().commit()
      sensors.sortBy(_.name)
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      em.close()
    }
  }
}
