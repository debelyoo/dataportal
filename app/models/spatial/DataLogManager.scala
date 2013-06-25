package models.spatial

import controllers.util._
import javax.persistence.{Query, EntityManager, TemporalType, NoResultException}
import scala.reflect.{ClassTag, classTag}
import java.util.{UUID, Calendar, Date}
import scala.collection.JavaConversions._
import models.mapping.{MapGpsRadiometer, MapGpsWind, MapGpsCompass, MapGpsTemperature}
import models.Sensor
import scala.Some
import controllers.database.{SpatializationBatchManager, SpatializationBatchWorker, SpatializationWorker}
import play.libs.Akka
import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import scala.concurrent.{Promise, Future}
import scala.concurrent.ExecutionContext.Implicits.global

//import scala.Predef.String
import com.vividsolutions.jts.geom.Point

object DataLogManager {

  val spatializationWorker = Akka.system.actorOf(Props[SpatializationWorker].withDispatcher("akka.actor.prio-dispatcher"), name = "spatializationWorker")
  val spatializationBatchWorker = Akka.system.actorOf(Props[SpatializationBatchWorker].withDispatcher("akka.actor.prio-dispatcher"), name = "spatializationBatchWorker")
  val TIMEOUT = 5 seconds
  implicit val timeout = Timeout(TIMEOUT) // needed for `?` below

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
  private def getNotGeolocated[T:ClassTag](emOpt: Option[EntityManager] = None): List[T] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val clazz = classTag[T].runtimeClass
      val q = em.createQuery("from "+ clazz.getName +" where geo_pos IS NULL ORDER BY timestamp")
      val logs = q.getResultList.map(_.asInstanceOf[T]).toList
      if (emOpt.isEmpty) em.getTransaction().commit()
      logs
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  /**
   * Get the sensor logs by date
   * @tparam T The type of data to get
   * @return A list with the logs
   */
  def getByDate[T:ClassTag](date: Date, emOpt: Option[EntityManager] = None): List[T] = {
    val afterDate = Calendar.getInstance()
    afterDate.setTime(date)
    afterDate.add(Calendar.DAY_OF_YEAR, 1)
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val clazz = classTag[T].runtimeClass
      val q = em.createQuery("from "+ clazz.getName +" WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp")
      q.setParameter("start", date, TemporalType.DATE)
      q.setParameter("end", afterDate.getTime, TemporalType.DATE)
      val logs = q.getResultList.map(_.asInstanceOf[T]).toList
      if (emOpt.isEmpty) em.getTransaction().commit()
      logs
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  /**
   * Link each GPS log to one sensor log (of a specific datatype)
   * @param dataType The type of data to link
   * @param logs The sensor logs to spatialize
   */
  def linkGpsLogToSensorLog(dataType: String, logs: List[SensorLog], em: EntityManager): String = {
    //println("GPS mapping [Start]")
    val firstTime = logs.head.getTimestamp
    val lastTime = logs.last.getTimestamp
    val MARGIN_IN_SEC = 1
    val start = new Date
    val batchId = dataType match {
      /*case DataImporter.Types.COMPASS => {
        val gLogs = getByTimeInterval[GpsLog](firstTime, lastTime, Some(em))
        gLogs.foreach(gl => {
          val clOpt = getClosestLog[CompassLog](gl.getTimestamp, MARGIN_IN_SEC, em)
          clOpt.map(cl => {
            MapGpsCompass(gl.getId, cl.getId).save(em)
          })
        })
      }*/
      case DataImporter.Types.TEMPERATURE => {
        // get sensors
        val sensors = Sensor.getByDatetime(firstTime, lastTime).filter(s => s.datatype == DataImporter.Types.TEMPERATURE)
        // get GPS logs
        val gLogs = getByTimeInterval[GpsLog](firstTime, lastTime)
        val batchId = UUID.randomUUID().toString
        //val batchId = "1234"
        spatializationWorker ! Message.SetSpatializationBatch(batchId, gLogs, sensors, logs)
        batchId
      }
      case DataImporter.Types.WIND => {
        // get sensors
        val sensors = Sensor.getByDatetime(firstTime, lastTime).filter(s => s.datatype == DataImporter.Types.WIND)
        // get GPS logs
        val gLogs = getByTimeInterval[GpsLog](firstTime, lastTime)
        val batchId = UUID.randomUUID().toString
        spatializationWorker ! Message.SetSpatializationBatch(batchId, gLogs, sensors, logs)
        batchId
        /*for {
          gl <- gLogs
          sensor <- sensors
          wl <- getClosestLog(logs, gl.getTimestamp, MARGIN_IN_SEC, sensor.id)
        } {
          // add position in windlog table
          updateGeoPos[WindLog](wl.getId.longValue(), gl.getGeoPos, em)
          // create mapping gpslog <-> windlog
          MapGpsWind(gl.getId, wl.getId).save(em)
        }
        em.getTransaction.commit
        */
      }
      /*case DataImporter.Types.RADIOMETER => {
        val gLogs = getByTimeInterval[GpsLog](firstTime, lastTime, Some(em))
        gLogs.foreach(gl => {
          val clOpt = getClosestLog[RadiometerLog](gl.getTimestamp, MARGIN_IN_SEC, em)
          clOpt.map(cl => {
            MapGpsRadiometer(gl.getId, cl.getId).save(em)
          })
        })
      }*/
      case _ => ""
    }
    //val diff = (new Date).getTime - start.getTime
    //println("GPS mapping [Stop] - time: "+ diff +"ms")
    batchId
  }

  /**
   * Get the data logs of a type between a time interval
   * @param startTime The start time of the interval
   * @param endTime The end time of the interval
   * @tparam T The type of data log to get
   * @return A list of the logs in the specified time interval
   */
  def getByTimeInterval[T: ClassTag](startTime: Date, endTime: Date, emOpt: Option[EntityManager] = None): List[T] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val clazz = classTag[T].runtimeClass
      // where timestamp BETWEEN '2013-05-14 16:30:00'::timestamp AND '2013-05-14 16:33:25'::timestamp ;
      val queryStr = "from "+ clazz.getName +" " +
        "WHERE timestamp BETWEEN :start and :end " +
        "ORDER BY timestamp"
      //println(queryStr)
      val q = em.createQuery(queryStr)
      q.setParameter("start", startTime, TemporalType.TIMESTAMP)
      q.setParameter("end", endTime, TemporalType.TIMESTAMP)
      //println(q.getResultList)
      val logs = q.getResultList.map(_.asInstanceOf[T]).toList
      if (emOpt.isEmpty) em.getTransaction().commit()
      logs
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  /**
   * Get the data logs of a type between a time interval
   * @param startTime The start time of the interval
   * @param endTime The end time of the interval
   * @tparam T The type of data log to get
   * @tparam M The type of the mapping table
   * @return A list of the logs in the specified time interval
   */
  def getByTimeIntervalWithJoin[T: ClassTag, M: ClassTag](startTime: Date, endTime: Date, emOpt: Option[EntityManager] = None): List[T] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val clazz = classTag[T].runtimeClass
      val clazzMapping = classTag[M].runtimeClass
      // where timestamp BETWEEN '2013-05-14 16:30:00'::timestamp AND '2013-05-14 16:33:25'::timestamp ;
      val queryStr = "SELECT lt FROM "+ clazz.getName +" lt, " + clazzMapping.getName +" map "+
        "WHERE lt.timestamp BETWEEN :start and :end AND lt.id = map.datalog_id " +
        "ORDER BY lt.timestamp"
      //println(queryStr)
      val q = em.createQuery(queryStr)
      q.setParameter("start", startTime, TemporalType.TIMESTAMP)
      q.setParameter("end", endTime, TemporalType.TIMESTAMP)
      //println(q.getResultList)
      val logs = q.getResultList.map(_.asInstanceOf[T]).toList
      if (emOpt.isEmpty) em.getTransaction().commit()
      logs
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  /**
   * Get the data logs of a type between a time interval, filtered by sensor id
   * @param startTime The start time of the interval
   * @param endTime The end time of the interval
   * @tparam T The type of data log to get
   * @return A list of the logs in the specified time interval
   */
  def getByTimeIntervalAndSensor[T: ClassTag](startTime: Date, endTime: Date, sensorId: Option[Long], emOpt: Option[EntityManager] = None): List[T] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val clazz = classTag[T].runtimeClass
      // where timestamp BETWEEN '2013-05-14 16:30:00'::timestamp AND '2013-05-14 16:33:25'::timestamp ;
      val sensorCondition = if (sensorId.isDefined) "AND sensor_id = :sid " else ""
      val queryStr = "from "+ clazz.getName +" WHERE timestamp BETWEEN :start AND :end "+ sensorCondition +"ORDER BY timestamp"
      //println(queryStr)
      val q = em.createQuery(queryStr)
      q.setParameter("start", startTime, TemporalType.TIMESTAMP)
      q.setParameter("end", endTime, TemporalType.TIMESTAMP)
      if (sensorId.isDefined) q.setParameter("sid", sensorId.get)
      //println(q.getResultList)
      val start = new Date
      val logs = q.getResultList.map(_.asInstanceOf[T]).toList
      val diff = (new Date).getTime - start.getTime
      println("Nb of logs queried: "+logs.length + " ["+ diff +"ms]")
      if (emOpt.isEmpty) em.getTransaction().commit()
      logs
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  /**
   * Get the data logs of a type between a time interval, filtered by sensor id
   * @param startTime The start time of the interval
   * @param endTime The end time of the interval
   * @tparam T The type of data log to get
   * @return A list of the logs in the specified time interval
   */
  def getByTimeIntervalAndSensorWithJoin[T: ClassTag, M: ClassTag](startTime: Date, endTime: Date, sensorId: Option[Long], emOpt: Option[EntityManager] = None): List[T] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val clazz = classTag[T].runtimeClass
      val clazzMapping = classTag[M].runtimeClass
      // where timestamp BETWEEN '2013-05-14 16:30:00'::timestamp AND '2013-05-14 16:33:25'::timestamp ;
      //val queryStr = "from "+ clazz.getName +" WHERE timestamp BETWEEN :start AND :end AND sensor_id = :sid ORDER BY timestamp"
      val sensorCondition = if (sensorId.isDefined) "AND sensor_id = :sid " else ""
      val queryStr = "SELECT lt FROM "+ clazz.getName +" lt, " + clazzMapping.getName +" map "+
        "WHERE lt.timestamp BETWEEN :start and :end AND lt.id = map.datalog_id " + sensorCondition +
        "ORDER BY lt.timestamp"
      //println(queryStr)
      val q = em.createQuery(queryStr)
      q.setParameter("start", startTime, TemporalType.TIMESTAMP)
      q.setParameter("end", endTime, TemporalType.TIMESTAMP)
      if (sensorId.isDefined) q.setParameter("sid", sensorId.get)
      val start = new Date
      val logs = q.getResultList.map(_.asInstanceOf[T]).toList
      val diff = (new Date).getTime - start.getTime
      println("Nb of logs queried (Join): "+logs.length + " ["+ diff +"ms]")
      if (emOpt.isEmpty) em.getTransaction().commit()
      logs
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      if (emOpt.isEmpty) em.close()
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
  def spatialize(dataType: String, dateStr: String): String = {
    //println("Spatializing [Start]")
    val date = DateFormatHelper.selectYearFormatter.parse(dateStr)
    val start = new Date
    val MARGIN_IN_SEC = 1
    val res = dataType match {
      /*case DataImporter.Types.COMPASS => {
        val logs = getNotGeolocated[CompassLog](em)
        val successes = logs.map(log => {
          val geo_pos = getClosestLog[GpsLog](log.getTimestamp, MARGIN_IN_SEC)
          if (geo_pos.isDefined) {
            updateGeoPos[CompassLog](log.getId.longValue(), geo_pos.get.getGeoPos)
          } else {
            false
          }
        }).filter(b => b)
        (successes.length, logs.length - successes.length)
      }*/
      case DataImporter.Types.TEMPERATURE => {
        val logs = getByDate[TemperatureLog](date)
        val em: EntityManager = JPAUtil.createEntityManager
        try {
          // link each GPS log to one sensor log
          linkGpsLogToSensorLog(dataType, logs, em)
        } catch {
          case ex: Exception => ""
        } finally {
          em.close
        }
      }
      /*case DataImporter.Types.RADIOMETER => {
        val logs = getNotGeolocated[RadiometerLog](em)
        val successes = logs.map(log => {
          val geo_pos = getClosestLog[GpsLog](log.getTimestamp, MARGIN_IN_SEC, em)
          if (geo_pos.isDefined) {
            updateGeoPos[RadiometerLog](log.getId.longValue(), geo_pos.get.getGeoPos, em)
          } else {
            false
          }
        }).filter(b => b)
        (successes.length, logs.length - successes.length)
      }*/
      case DataImporter.Types.WIND => {
        val logs = getByDate[WindLog](date)
        val em: EntityManager = JPAUtil.createEntityManager
        try {
          // link each GPS log to one temperature log
          linkGpsLogToSensorLog(dataType, logs, em)
        } catch {
          case ex: Exception => ""
        } finally {
          em.close
        }
        /*val successes = logs.map(log => {
          val geo_pos = getClosestLog[GpsLog](log.getTimestamp, MARGIN_IN_SEC, em)
          if (geo_pos.isDefined) {
            updateGeoPos[WindLog](log.getId.longValue(), geo_pos.get.getGeoPos, em)
          } else {
            false
          }
        }).filter(b => b)
        (successes.length, logs.length - successes.length)*/
      }
      case _ => ""
    }
    res
  }

  def getDates: List[String] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val q = em.createQuery("SELECT DISTINCT cast(timestamp as date) FROM "+ classOf[GpsLog].getName)
      val dates = q.getResultList.map(_.asInstanceOf[Date]).toList.map(ts => DateFormatHelper.selectYearFormatter.format(ts))
      em.getTransaction().commit()
      em.close()
      dates
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    }
  }

  def getTimesForDate(date: Date): (String, String)= {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val q = em.createQuery("SELECT MIN(cast(timestamp as time)), MAX(cast(timestamp as time)) FROM "+ classOf[GpsLog].getName)
      val res = q.getSingleResult.asInstanceOf[Array[Object]]
      val firstTime = res(0).asInstanceOf[Date]
      val lastTime = res(1).asInstanceOf[Date]
      em.getTransaction().commit()
      em.close()
      (DateFormatHelper.selectTimeFormatter.format(firstTime), DateFormatHelper.selectTimeFormatter.format(lastTime))
    } catch {
      case nre: NoResultException => ("00:00:00", "00:00:00")
      case ex: Exception => ex.printStackTrace; ("00:00:00", "00:00:00")
    }
  }

  /**
   * Get the closest data log from a specific timestamp
   * @param ts The target timestamp
   * @param marginInSeconds The nb of seconds to search before and after the timestamp
   * @param sensorId
   * @param em
   * @return An option with the closest GPS point
   */
  /*private def getClosestLogFromDB[T: ClassTag](ts: Date, marginInSeconds: Int, sensorId: Long, em: EntityManager): Option[T] = {
    val beforeDate = Calendar.getInstance()
    beforeDate.setTime(ts)
    beforeDate.add(Calendar.SECOND, -marginInSeconds)
    val afterDate = Calendar.getInstance()
    afterDate.setTime(ts)
    afterDate.add(Calendar.SECOND, marginInSeconds)
    val closeLogs = getByTimeIntervalAndSensor[T](beforeDate.getTime, afterDate.getTime, sensorId, Some(em))
    if (closeLogs.nonEmpty) {
      val (closestPoint, diff) = closeLogs.map(cl => {
        val timeDiff = math.abs(cl.asInstanceOf[SensorLog].getTimestamp.getTime - ts.getTime)
        (cl, timeDiff)
      }).minBy(_._2)
      Some(closestPoint)
    } else {
      println("[WARNING] No close GPS point for TS: "+DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
      None
    }
  }*/

  def getClosestLog(logs: List[SensorLog], ts: Date, marginInSeconds: Int, sensorId: Long): Option[SensorLog] = {
    val beforeDate = Calendar.getInstance()
    beforeDate.setTime(ts)
    beforeDate.add(Calendar.SECOND, -marginInSeconds)
    val afterDate = Calendar.getInstance()
    afterDate.setTime(ts)
    afterDate.add(Calendar.SECOND, marginInSeconds)
    //val closeLogs = getByTimeIntervalAndSensor[T](beforeDate.getTime, afterDate.getTime, sensorId, Some(em))
    val closeLogs = logs.filter(log =>
      (log.getTimestamp.getTime > beforeDate.getTime.getTime &&
        log.getTimestamp.getTime < afterDate.getTime.getTime &&
        log.getSensorId == sensorId)
    )
    if (closeLogs.nonEmpty) {
      val (closestPoint, diff) = closeLogs.map(cl => {
        val timeDiff = math.abs(cl.getTimestamp.getTime - ts.getTime)
        (cl, timeDiff)
      }).minBy(_._2)
      Some(closestPoint)
    } else {
      println("[WARNING] No close log for TS: "+DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
      None
    }
  }

  /**
   * Update the geo position
   * @param dataLogId The id of the log to update
   * @param pos The new geo position
   * @return true if success
   */
  def updateGeoPos[T: ClassTag](dataLogId: Long, pos: Point, em: EntityManager): Boolean = {
    //val em: EntityManager = JPAUtil.createEntityManager
    try {
      //em.getTransaction.begin
      val queryStr: String = "UPDATE " + classTag[T].runtimeClass.getName + " " +
        "SET geo_pos = ST_GeomFromText('POINT(" + pos.getX + " " + pos.getY + ")', 4326) " +
        "WHERE id=" + dataLogId
      val q: Query = em.createQuery(queryStr)
      q.executeUpdate
      //println("updateGeoPos() - ["+ dataLogId +"]")
      //em.getTransaction.commit
      true
    }
    catch {
      case ex: Exception => false
    }
    /*finally {
      em.close
    }*/
  }

  //def spatializationProgress(batchId: String): Future[Either[Long, String]] = {
  def spatializationProgress(batchId: String): Option[Long] = {
    /*try {
      val f_prog = spatializationWorker ? Message.GetSpatializationProgress(batchId)
      f_prog.map(_.asInstanceOf[Option[Long]].map(Left(_)).getOrElse(Right("unknown batch id")))
    } catch {
      case ex: Exception => Future { Right("timeout") }
    }*/
    val percentage = SpatializationBatchManager.batchProgress.get(batchId).map {
      case (nbTot, nbDone) => math.round((nbDone.toDouble / nbTot.toDouble) * 100)
    }
    percentage
  }

}
