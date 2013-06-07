package test

import org.hibernate.annotations.{GenericGenerator, Type}
import javax.persistence._
import java.util.Date
import controllers.util.{HibernateUtil, JPAUtil}
import com.vividsolutions.jts.geom.{Geometry, Point}

@Entity
class GpsLogX(
              @Column(name="sensor_id") pSensorId: Long,
              @Column(name="geo_pos") @Type(`type`="org.hibernate.spatial.GeometryType") pGeoPos: Point, // BUG - column is not persisted in PostGIS, seems to be a bug with Scala class
              @Column(name="timestamp") pTimestamp: Date) {


  @Id
  @GeneratedValue(generator="increment")
  @GenericGenerator(name="increment", strategy = "increment")
  var id: Long = _

  override def toString = id + " -> sensor: " + pSensorId + ", ts: " + pTimestamp //+ ", point: ["+ pGeoPos.getX + "," + pGeoPos.getY + "]"

  /**
   * Save the GPS log in DB
   */
  def save {
    println("[GpsLog] save() - "+this.toString)
    val em = JPAUtil.createEntityManager()
    em.getTransaction().begin()
    em.persist(this)
    em.getTransaction().commit()
    em.close()
  }
}