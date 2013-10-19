package controllers.modelmanager

import models.Vehicle
import javax.persistence.{NoResultException, EntityManager}
import controllers.util.JPAUtil

object VehicleManager {
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
