package models

import javax.persistence._
import org.hibernate.annotations.GenericGenerator
import java.lang.Boolean
import controllers.util.JPAUtil
import scala.collection.JavaConversions._

@Entity
@Table(name = "devicetype")
case class DeviceType(name: String) {

  @Id
  @GeneratedValue(generator="increment")
  @GenericGenerator(name="increment", strategy = "increment")
  @Column(name = "id", unique = true, nullable = false)
  var id: Long = _

  override def toString = id + " -> name: " + name

  def this() = this("foo") // default constructor - necessary to work with hibernate (otherwise not possible to do select queries)

  /**
   * Persist device type in DB (if it is not already in)
   * @return
   */
  def save: Boolean = {
    val deviceTypeInDb = DeviceType.getByName(this.name)
    if (deviceTypeInDb.isEmpty) {
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
}

object DeviceType {

  /**
   * Get DeviceType by name
   * @param name The name of the device type
   * @return An option with the device type if it is in DB
   */
  def getByName(name: String): Option[DeviceType] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[DeviceType].getName +" where name = '"+ name +"'")
      val deviceType = q.getSingleResult.asInstanceOf[DeviceType]
      em.getTransaction().commit()
      Some(deviceType)
    } catch {
      case nre: NoResultException => None
      case ex: Exception => ex.printStackTrace; None
    } finally {
      em.close()
    }
  }

  def getAll(): List[DeviceType] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[DeviceType].getName, classOf[DeviceType])
      val deviceTypes = q.getResultList.toList
      em.getTransaction().commit()
      deviceTypes
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      em.close()
    }
  }
}
