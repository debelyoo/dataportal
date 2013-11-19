package models

import javax.persistence._
import org.hibernate.annotations.GenericGenerator
import scala.collection.JavaConversions._
import controllers.util.JPAUtil

/**
 * A class that represents a vehicle
 * @param n The name of the vehicle
 */
@Entity
@Table(name = "vehicle")
class Vehicle(n: String) {
  /**
   * The id of the vehicle in DB
   */
  @Id
  @GeneratedValue(generator="increment")
  @GenericGenerator(name="increment", strategy = "increment")
  @Column(name = "id", unique = true, nullable = false)
  var id: Long = _

  /**
   * The name of the vehicle
   */
  var name: String = n

  def this() = this("") // default constructor - necessary to work with hibernate (otherwise not possible to do select queries)

  override def toString = "[Vehicle] id:" +id + ", name: "+ name

  /**
   * Save the Vehicle in Postgres database
   * @param emOpt An optional Entity Manager
   * @return true if success
   */
  def save(emOpt: Option[EntityManager]): Boolean = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction.begin
      em.persist(this)
      if (emOpt.isEmpty) em.getTransaction.commit
      true
    }
    catch {
      case ex: Exception => {
        System.out.println("[ERROR][Vehicle.save()] " + ex.getMessage)
        false
      }
    }
    finally {
      if (emOpt.isEmpty) em.close
    }
  }
}

/**
 * The companion object of the Vehicle class
 */
object Vehicle {
  /**
   * Get a vehicle by name
   * @param name The name of the vehicle
   * @param emOpt An optional Entity manager
   * @return A vehicle as an Option
   */
  def getByName(name: String, emOpt: Option[EntityManager] = None): Option[Vehicle] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[Vehicle].getName +" where name = '"+ name +"'")
      val vehicle = q.getSingleResult.asInstanceOf[Vehicle]
      if (emOpt.isEmpty) em.getTransaction().commit()
      Some(vehicle)
    } catch {
      case nre: NoResultException => None
      case ex: Exception => ex.printStackTrace; None
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  /**
   * Get all vehicles in database
   * @return A list of vehicles
   */
  def getAll(): List[Vehicle] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[Vehicle].getName, classOf[Vehicle])
      val vehicles = q.getResultList.toList
      em.getTransaction().commit()
      vehicles
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      em.close()
    }
  }
}
