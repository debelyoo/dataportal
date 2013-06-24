package models.mapping

import javax.persistence._
import org.hibernate.annotations.GenericGenerator
import controllers.util.JPAUtil
import scala.Boolean

@Entity
@Table(name = "map_gps_temperature", uniqueConstraints = Array(new UniqueConstraint(columnNames = Array("gpslog_id", "datalog_id"))))
case class MapGpsTemperature(gpslog_id: Long, datalog_id: Long) {
  @Id
  @GeneratedValue(generator="increment")
  @GenericGenerator(name="increment", strategy = "increment")
  var id: Long = _

  override def toString = gpslog_id +" -> "+ datalog_id
  /**
   * Persist mapping in DB (if it is not already in)
   * @return
   */
  def save(em: EntityManager): Boolean = {
    //val mappingInDb = MapGpsTemperature.getByIds(this.gpslog_id, this.temperaturelog_id, em) // remove check, to gain some speed
    //if (mappingInDb.isEmpty) {
      //println("[MapGpsTemperature] save() - "+ this.toString)
      //val em: EntityManager = JPAUtil.createEntityManager
      try {
        //em.getTransaction.begin
        em.persist(this)
        //em.getTransaction.commit
        true
      } catch {
        case ex: Exception => {
          println("[WARNING] " + ex.getMessage)
          false
        }
      } /*finally {
        em.close
      }*/
    /*} else {
      true
    }*/
  }
}

object MapGpsTemperature {
  def getByIds(gpsLogId: Long, temperatureLogId: Long, em: EntityManager): Option[MapGpsTemperature] = {
    //val em = JPAUtil.createEntityManager()
    try {
      //em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[MapGpsTemperature].getName +" WHERE gpslog_id = "+ gpsLogId + " " +
        "AND datalog_id = "+temperatureLogId)
      //q.setParameter("id",sId)
      val map = q.getSingleResult.asInstanceOf[MapGpsTemperature]
      //em.getTransaction().commit()
      Some(map)
    } catch {
      case nre: NoResultException => None
      case ex: Exception => ex.printStackTrace; None
    } /*finally {
      em.close
    }*/
  }
}