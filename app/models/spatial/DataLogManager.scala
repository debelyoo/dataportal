package models.spatial

import controllers.util.{DataImporter, DateFormatHelper, JPAUtil}
import javax.persistence.{Query, EntityManager, TemporalType, NoResultException}
import scala.reflect.{ClassTag, classTag}
import java.util.{Calendar, Date}
import scala.collection.JavaConversions._
//import scala.Predef.String
import com.vividsolutions.jts.geom.Point

object DataLogManager {
  /**
   * Get a data log by Id (give log type in parameter using scala 2.10 ClassTag - https://wiki.scala-lang.org/display/SW/2.10+Reflection+and+Manifest+Migration+Notes)
   * @param sId The log id
   * @tparam T The type of data log to get
   * @return The log
   */
  def getById[T: ClassTag](sId: Long): Option[T] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val clazz = classTag[T].runtimeClass
      val q = em.createQuery("from "+ clazz.getName +" where id = "+sId)
      val log = q.getSingleResult.asInstanceOf[T]
      em.getTransaction().commit()
      em.close()
      Some(log)
    } catch {
      case nre: NoResultException => None
      case ex: Exception => ex.printStackTrace; None
    }
  }

  /**
   * Get the sensor logs that do not have geo pos yet
   * @tparam T The type of data to get
   * @return A list with the non-geolocated logs
   */
  def getNotGeolocated[T:ClassTag]: List[T] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val clazz = classTag[T].runtimeClass
      val q = em.createQuery("from "+ clazz.getName +" where geo_pos IS NULL")
      val logs = q.getResultList.map(_.asInstanceOf[T]).toList
      em.getTransaction().commit()
      em.close()
      logs
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    }
  }

  /**
   * Get the data logs of a type between a time interval
   * @param startTime The start time of the interval
   * @param endTime The end time of the interval
   * @tparam T The type of data log to get
   * @return A list of the logs in the specified time interval
   */
  def getByTimeInterval[T: ClassTag](startTime: Date, endTime: Date): List[T] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val clazz = classTag[T].runtimeClass
      // where timestamp BETWEEN '2013-05-14 16:30:00'::timestamp AND '2013-05-14 16:33:25'::timestamp ;
      val queryStr = "from "+ clazz.getName +" " +
        "where timestamp between :start and :end"
      //println(queryStr)
      val q = em.createQuery(queryStr)
      q.setParameter("start", startTime, TemporalType.TIMESTAMP)
      q.setParameter("end", endTime, TemporalType.TIMESTAMP)
      //println(q.getResultList)
      val logs = q.getResultList.map(_.asInstanceOf[T]).toList
      em.getTransaction().commit()
      em.close()
      logs
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    }
  }

  /**
   * Converts a Double value to Int
   * @param valueToTest The value to convert
   * @return an option with the Int value
   */
  def doubleToInt(valueToTest: Double): Option[Int] = {
    try {
      val intValue = valueToTest.asInstanceOf[Int]
      Some(intValue)
    } catch {
      case ex: Exception => None
    }
  }

  /**
   * Add the location to data logs that don't have this info
   * @param dataType The type of data to handle
   * @return The number of successes, the number of failures
   */
  def spatialize(dataType: String): (Int, Int) = {
    val MARGIN_IN_SEC = 1
    dataType match {
      case DataImporter.Types.COMPASS => {
        val logs = getNotGeolocated[CompassLog]
        val successes = logs.map(log => {
          val geo_pos = getClosestGPSPoint(log.getTimestamp, MARGIN_IN_SEC)
          if (geo_pos.isDefined) {
            updateGeoPos[CompassLog](log.getId.longValue(), geo_pos.get.getGeoPos)
          } else {
            false
          }
        }).filter(b => b)
        (successes.length, logs.length - successes.length)
      }
      case DataImporter.Types.TEMPERATURE => {
        val logs = getNotGeolocated[TemperatureLog]
        val successes = logs.map(log => {
          val geo_pos = getClosestGPSPoint(log.getTimestamp, MARGIN_IN_SEC)
          if (geo_pos.isDefined) {
            updateGeoPos[TemperatureLog](log.getId.longValue(), geo_pos.get.getGeoPos)
          } else {
            false
          }
        }).filter(b => b)
        (successes.length, logs.length - successes.length)
      }
      case DataImporter.Types.RADIOMETER => {
        val logs = getNotGeolocated[RadiometerLog]
        val successes = logs.map(log => {
          val geo_pos = getClosestGPSPoint(log.getTimestamp, MARGIN_IN_SEC)
          if (geo_pos.isDefined) {
            updateGeoPos[RadiometerLog](log.getId.longValue(), geo_pos.get.getGeoPos)
          } else {
            false
          }
        }).filter(b => b)
        (successes.length, logs.length - successes.length)
      }
      case DataImporter.Types.WIND => {
        val logs = getNotGeolocated[WindLog]
        val successes = logs.map(log => {
          val geo_pos = getClosestGPSPoint(log.getTimestamp, MARGIN_IN_SEC)
          if (geo_pos.isDefined) {
            updateGeoPos[WindLog](log.getId.longValue(), geo_pos.get.getGeoPos)
          } else {
            false
          }
        }).filter(b => b)
        (successes.length, logs.length - successes.length)
      }
      case _ => (0, 0)
    }
  }

  /**
   * Get the closest GPS log from a specific timestamp
   * @param ts The target timestamp
   * @param marginInSeconds The nb of seconds to search before and after the timestamp
   * @return An option with the closest GPS point
   */
  private def getClosestGPSPoint(ts: Date, marginInSeconds: Int): Option[GpsLog] = {
    val beforeDate = Calendar.getInstance()
    beforeDate.setTime(ts)
    beforeDate.add(Calendar.SECOND, -marginInSeconds)
    val afterDate = Calendar.getInstance()
    afterDate.setTime(ts)
    afterDate.add(Calendar.SECOND, marginInSeconds)
    val closeGpsLogs = getByTimeInterval[GpsLog](beforeDate.getTime, afterDate.getTime)
    if (closeGpsLogs.nonEmpty) {
      val (closestPoint, diff) = closeGpsLogs.map(gl => {
        val timeDiff = math.abs(gl.getTimestamp.getTime - ts.getTime)
        (gl, timeDiff)
      }).minBy(_._2)
      Some(closestPoint)
    } else {
      println("[WARNING] No close GPS point for TS: "+DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
      None
    }
  }

  /**
   * Update the geo position
   * @param pos The new geo position
   * @return true if success
   */
  private def updateGeoPos[T: ClassTag](dataLogId: Long, pos: Point): Boolean = {
    val em: EntityManager = JPAUtil.createEntityManager
    try {
      em.getTransaction.begin
      val queryStr: String = "UPDATE " + classTag[T].runtimeClass.getName + " SET geo_pos = ST_GeomFromText('POINT(" + pos.getX + " " + pos.getY + ")', 4326) WHERE id=" + dataLogId
      val q: Query = em.createQuery(queryStr)
      q.executeUpdate
      em.getTransaction.commit
      true
    }
    catch {
      case ex: Exception => {
        false
      }
    }
    finally {
      em.close
    }
  }

}
