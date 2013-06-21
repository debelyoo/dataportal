package models.mapping

import javax.persistence._
import org.hibernate.annotations.GenericGenerator
import controllers.util.JPAUtil

@Entity
@Table(name = "map_gps_compass", uniqueConstraints = Array(new UniqueConstraint(columnNames = Array("gpslog_id", "datalog_id"))))
case class MapGpsCompass(gpslog_id: Long, datalog_id: Long) {
  @Id
  @GeneratedValue(generator="increment")
  @GenericGenerator(name="increment", strategy = "increment")
  var id: Long = _

  /**
   * Persist mapping in DB (if it is not already in)
   * @return
   */
  def save(em: EntityManager): Boolean = {
    val mappingInDb = MapGpsCompass.getByIds(this.gpslog_id, this.datalog_id, em)
    if (mappingInDb.isEmpty) {
      //println("[MapGpsTemperature] save() - "+ this.toString)
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
    } else {
      true
    }
  }
}

object MapGpsCompass {
  def getByIds(gpsLogId: Long, compassLogId: Long, em: EntityManager): Option[MapGpsCompass] = {
    //val em = JPAUtil.createEntityManager()
    try {
      //em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[MapGpsCompass].getName +" WHERE gpslog_id = "+ gpsLogId + " " +
        "AND datalog_id = "+compassLogId)
      //q.setParameter("id",sId)
      val map = q.getSingleResult.asInstanceOf[MapGpsCompass]
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
