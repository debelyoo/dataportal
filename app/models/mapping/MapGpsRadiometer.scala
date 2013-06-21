package models.mapping

import javax.persistence._
import org.hibernate.annotations.GenericGenerator
import controllers.util.JPAUtil
import scala.Some

@Entity
@Table(name = "map_gps_radiometer", uniqueConstraints = Array(new UniqueConstraint(columnNames = Array("gpslog_id", "datalog_id"))))
case class MapGpsRadiometer(gpslog_id: Long, datalog_id: Long) {
  @Id
  @GeneratedValue(generator="increment")
  @GenericGenerator(name="increment", strategy = "increment")
  var id: Long = _

  /**
   * Persist mapping in DB (if it is not already in)
   * @return
   */
  def save(em: EntityManager): Boolean = {
    val mappingInDb = MapGpsRadiometer.getByIds(this.gpslog_id, this.datalog_id, em)
    if (mappingInDb.isEmpty) {
      //println("[MapGpsWind] save() - "+ this.toString)
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

object MapGpsRadiometer {
  def getByIds(gpsLogId: Long, radiometerLogId: Long, em: EntityManager): Option[MapGpsRadiometer] = {
    //val em = JPAUtil.createEntityManager()
    try {
      //em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[MapGpsRadiometer].getName +" WHERE gpslog_id = "+ gpsLogId + " " +
        "AND datalog_id = "+radiometerLogId)
      //q.setParameter("id",sId)
      val map = q.getSingleResult.asInstanceOf[MapGpsRadiometer]
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

