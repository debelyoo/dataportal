package models

import javax.persistence._
import controllers.util.JPAUtil
import org.hibernate.annotations.GenericGenerator
import java.lang.Boolean
import models.spatial.{TemperatureLog, CompassLog}
import com.google.gson.Gson
import java.util.Date
import scala.collection.JavaConversions._

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
          em.getTransaction.rollback
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
   * @param sId The sensor id
   * @return The sensor
   */
  def getById(sId: Long): Option[Sensor] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[Sensor].getName +" where id = "+sId)
      //q.setParameter("id",sId)
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
      em.getTransaction().begin()
      val q = em.createQuery("SELECT DISTINCT s.id, s.name, s.address, s.datatype FROM "+ classOf[Sensor].getName +" s,"+ classOf[TemperatureLog].getName +" tl " +
        "WHERE timestamp BETWEEN :start AND :end AND tl.sensor_id = s.id")
      val sensors = q.getResultList.map(_.asInstanceOf[Sensor]).toList
      em.getTransaction().commit()
      em.close()
      sensors
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    }
  }
}
