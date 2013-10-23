package controllers

import models.{Mission}
import play.api.mvc.{Action, Controller}
import controllers.modelmanager.DataLogManager
import controllers.util.JPAUtil

trait DeleteApi {
  this: Controller =>

  /**
   * Delete a mission from DB (with cascading)
   * @param mId The id of the mission
   * @return
   */
  def deleteMission(mId: String) = Action {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction.begin()
      val mission = DataLogManager.getById[Mission](mId.toLong, Some(em))
      val res = if (mission.isDefined) {
        mission.get.delete(Some(em))
        Ok
      } else {
        NoContent
      }
      em.getTransaction.commit()
      res
    } catch {
      case nfe: NumberFormatException => InternalServerError
      case ex: Exception => ex.printStackTrace(); InternalServerError
    }
  }
}
