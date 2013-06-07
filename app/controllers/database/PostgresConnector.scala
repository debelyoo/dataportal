package controllers.database

import play.api.mvc.{Action, Controller}
import play.api.Play.current
import models._
import java.util.Date
import com.vividsolutions.jts.geom.{Geometry, Point, GeometryFactory, Coordinate}
import com.vividsolutions.jts.geom.impl.CoordinateArraySequence
import com.vividsolutions.jts.io.{ParseException, WKTReader}
import controllers.util.JPAUtil
import models.spatial.GpsLog

object PostgresConnector extends Controller {

  def insert = Action {

    /*val sensor = new Sensor("sensor-xx-20", "31", "compass")
    sensor.save*/

    val pt = new Point(new CoordinateArraySequence(Array(new Coordinate(2,4))), new GeometryFactory())
    //val geom = wktToGeometry("POINT(2 4)")
    //val pt = new Point(2.0, 4.0)
    //val gpsLog = new GpsLog(1, pt, new Date())
    //gpsLog.save

    val jGpsLog = new GpsLog()
    jGpsLog.setSensorId(1)
    jGpsLog.setTimestamp(new Date())
    jGpsLog.setGeoPos(pt)
    jGpsLog.save()

    /*val ev = new Event()
    ev.setTitle("event1")
    ev.setDate(new Date())
    ev.setLocation(pt)
    val em = JPAUtil.createEntityManager()
    em.getTransaction().begin()
    em.persist(ev)
    em.getTransaction().commit()
    em.close()*/

    Ok("good")
  }


}
