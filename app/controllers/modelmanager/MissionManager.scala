package controllers.modelmanager

import java.util.Date
import javax.persistence.{TemporalType, NoResultException, EntityManager}
import controllers.util.JPAUtil
import models.Mission

object MissionManager {

  def getByDatetime(date: Date, emOpt: Option[EntityManager] = None): Option[Mission] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[Mission].getName +" where departuretime = :depTime")
      q.setParameter("depTime", date, TemporalType.TIMESTAMP)
      val mission = q.getSingleResult.asInstanceOf[Mission]
      if (emOpt.isEmpty) em.getTransaction().commit()
      Some(mission)
    } catch {
      case nre: NoResultException => None
      case ex: Exception => ex.printStackTrace; None
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }
}
