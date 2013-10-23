package models

import javax.persistence._
import org.hibernate.annotations.GenericGenerator
import controllers.util.JPAUtil

@Entity
@Table(name = "vehicle")
class Vehicle(n: String) {
  @Id
  @GeneratedValue(generator="increment")
  @GenericGenerator(name="increment", strategy = "increment")
  @Column(name = "id", unique = true, nullable = false)
  var id: Long = _

  var name: String = n

  def this() = this("") // default constructor - necessary to work with hibernate (otherwise not possible to do select queries)

  override def toString = "[Vehicle] id:" +id + ", name: "+ name

  /**
   * Save the Vehicle in Postgres database
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

object Vehicle {
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
}
