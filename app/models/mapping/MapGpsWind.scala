package models.mapping

import javax.persistence._
import org.hibernate.annotations.GenericGenerator
import scala.Some
import controllers.util.JPAUtil

@Entity
@Table(name = "map_gps_wind", uniqueConstraints = Array(new UniqueConstraint(columnNames = Array("gpslog_id", "datalog_id"))))
case class MapGpsWind(gpslog_id: Long, datalog_id: Long) {
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
    //val mappingInDb = MapGpsWind.getByIds(this.gpslog_id, this.windlog_id, em)
    //if (mappingInDb.isEmpty) {
      println("[MapGpsWind] save() - "+ this.toString)
      //val em: EntityManager = JPAUtil.createEntityManager
      try {
        //em.getTransaction.begin
        em.persist(this)
        //em.getTransaction.commit
        true
      } catch {
        case ex: Exception => false
      } /*finally {
        em.close
      }*/
    /*} else {
      true
    }*/
  }
}

object MapGpsWind {
  def getByIds(gpsLogId: Long, windLogId: Long, em: EntityManager): Option[MapGpsWind] = {
    //val em = JPAUtil.createEntityManager()
    try {
      //em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[MapGpsWind].getName +" WHERE gpslog_id = "+ gpsLogId + " " +
        "AND datalog_id = "+windLogId)
      //q.setParameter("id",sId)
      val map = q.getSingleResult.asInstanceOf[MapGpsWind]
      //em.getTransaction().commit()
      Some(map)
    } catch {
      case nre: NoResultException => None
      case ex: Exception => ex.printStackTrace; None
    } /*finally {
      em.close()
    }*/
  }
}

