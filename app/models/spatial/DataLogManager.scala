package models.spatial

import controllers.util._
import javax.persistence.{Query, EntityManager, TemporalType, NoResultException}
import scala.reflect.{ClassTag, classTag}
import java.util.{UUID, Calendar, Date}
import scala.collection.JavaConversions._
import models.Sensor
import controllers.database._
import play.libs.Akka
import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.Some

object DataLogManager {

  val insertionWorker = Akka.system.actorOf(Props[InsertionWorker], name = "insertionWorker")
  val insertionBatchWorker = Akka.system.actorOf(Props[InsertionBatchWorker], name = "insertionBatchWorker")
  //val spatializationWorker = Akka.system.actorOf(Props[SpatializationWorker].withDispatcher("akka.actor.prio-dispatcher"), name = "spatializationWorker")
  //val spatializationBatchWorker = Akka.system.actorOf(Props[SpatializationBatchWorker].withDispatcher("akka.actor.prio-dispatcher"), name = "spatializationBatchWorker")
  val spatializationWorker = Akka.system.actorOf(Props[SpatializationWorker], name = "spatializationWorker")
  val spatializationBatchWorker = Akka.system.actorOf(Props[SpatializationBatchWorker], name = "spatializationBatchWorker")
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
   * Get the data logs of a type between a time interval
   * @param startTime The start time of the interval
   * @param endTime The end time of the interval
   * @param geoOnly Indicates if we want only the logs mapped to a gps log
   * @tparam T The type of data log to get
   * @return A list of the logs in the specified time interval
   */
  def getByTimeInterval[T: ClassTag](startTime: Date, endTime: Date, geoOnly: Boolean, emOpt: Option[EntityManager] = None): List[T] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val clazz = classTag[T].runtimeClass
      val geoCondition = if (geoOnly) "AND log.gpsLog IS NOT NULL " else ""
      // where timestamp BETWEEN '2013-05-14 16:30:00'::timestamp AND '2013-05-14 16:33:25'::timestamp ;
      val queryStr = "from "+ clazz.getName +" log " +
        "WHERE timestamp BETWEEN :start AND :end " + geoCondition +
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
   * Get the data logs of a type between a time interval, filtered by sensor id
   * @param startTime The start time of the interval
   * @param endTime The end time of the interval
   * @param geoOnly Indicates if we want only the logs mapped to a gps log
   * @param sensorIdList The list of sensor we are interested in
   * @param emOpt The optional entityManager
   * @tparam T The type of data log to get
   * @return A list of the logs in the specified time interval
   */
  def getByTimeIntervalAndSensor[T: ClassTag](
                                               startTime: Date,
                                               endTime: Date,
                                               geoOnly: Boolean,
                                               sensorIdList: List[Long],
                                               maxNb: Option[Int],
                                               emOpt: Option[EntityManager] = None): Map[String, List[T]] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val sensors = Sensor.getById(sensorIdList, emOpt)
      val clazz = classTag[T].runtimeClass
      // where timestamp BETWEEN '2013-05-14 16:30:00'::timestamp AND '2013-05-14 16:33:25'::timestamp ;
      val geoCondition = if (geoOnly) "AND log.gpsLog IS NOT NULL " else ""
      val sensorCondition = if (sensorIdList.nonEmpty) "AND log.sensor.id IN ("+ sensorIdList.mkString(",") +") " else ""
      val queryStr = "from "+ clazz.getName +" log WHERE timestamp BETWEEN :start AND :end "+ geoCondition + sensorCondition +
        "ORDER BY timestamp"
      //println(queryStr)
      val q = em.createQuery(queryStr)
      q.setParameter("start", startTime, TemporalType.TIMESTAMP)
      q.setParameter("end", endTime, TemporalType.TIMESTAMP)
      //println(q.getResultList)
      val start = new Date
      val logs = q.getResultList.map(_.asInstanceOf[T]).toList
      val diff = (new Date).getTime - start.getTime
      println("Nb of logs queried: "+logs.length + " ["+ diff +"ms]")
      if (emOpt.isEmpty) em.getTransaction().commit()
      val logsMapBySensorId = logs.map(_.asInstanceOf[SensorLog]).groupBy(_.getSensor.id)
      logsMapBySensorId.map { case (sId, logs) => {
          val reducedLogList = if (maxNb.isDefined && logs.length > maxNb.get) {
            val moduloFactor = math.ceil(logs.length.toDouble / maxNb.get.toDouble).toInt
            println("logs list reduced by factor: "+ moduloFactor)
            for {
              (sl, ind) <- logs.zipWithIndex
              if (ind % moduloFactor == 0)
            } yield {
              sl
            }
          } else logs
          println("Nb of logs returned: "+reducedLogList.length)
          (sensors.get(sId).get.name, reducedLogList.map(_.asInstanceOf[T]))
        }
      }
    } catch {
      case nre: NoResultException => println("No result !!"); Map()
      case ex: Exception => ex.printStackTrace; Map()
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
    val batchId = dataType match {
      case DataImporter.Types.COMPASS => {
        val logs = getByDate[CompassLog](date)
        try {
          // link each GPS log to one compass log
          //linkGpsLogToSensorLog(dataType, logs)
          val firstTime = logs.head.getTimestamp
          val lastTime = logs.last.getTimestamp
          // get sensors
          val sensors = Sensor.getByDatetime(firstTime, lastTime).filter(s => s.datatype == DataImporter.Types.COMPASS)
          // get GPS logs
          val gLogs = getByTimeInterval[GpsLog](firstTime, lastTime, false)
          val batchId = UUID.randomUUID().toString
          spatializationWorker ! Message.SetSpatializationBatch(batchId, gLogs, sensors, logs)
          batchId
        } catch {
          case ex: Exception => ex.printStackTrace(); ""
        }
      }
      case DataImporter.Types.TEMPERATURE => {
        val logs = getByDate[TemperatureLog](date)
        //println("XX - "+logs.head)
        try {
          // link each GPS log to one sensor log
          //linkGpsLogToSensorLog(dataType, logs)
          val firstTime = logs.head.getTimestamp
          val lastTime = logs.last.getTimestamp
          // get sensors
          val sensors = Sensor.getByDatetime(firstTime, lastTime).filter(s => s.datatype == DataImporter.Types.TEMPERATURE)
          // get GPS logs
          val gLogs = getByTimeInterval[GpsLog](firstTime, lastTime, false)
          val batchId = UUID.randomUUID().toString
          spatializationWorker ! Message.SetSpatializationBatch(batchId, gLogs, sensors, logs)
          batchId
        } catch {
          case ex: Exception => ex.printStackTrace(); ""
        }
      }
      case DataImporter.Types.RADIOMETER => {
        val logs = getByDate[RadiometerLog](date)
        try {
          // link each GPS log to one radiometer log
          //linkGpsLogToSensorLog(dataType, logs)
          val firstTime = logs.head.getTimestamp
          val lastTime = logs.last.getTimestamp
          // get sensors
          val sensors = Sensor.getByDatetime(firstTime, lastTime).filter(s => s.datatype == DataImporter.Types.RADIOMETER)
          // get GPS logs
          val gLogs = getByTimeInterval[GpsLog](firstTime, lastTime, false)
          val batchId = UUID.randomUUID().toString
          //val batchId = "1234"
          spatializationWorker ! Message.SetSpatializationBatch(batchId, gLogs, sensors, logs)
          batchId
        } catch {
          case ex: Exception => ex.printStackTrace(); ""
        }
      }
      case DataImporter.Types.WIND => {
        val logs = getByDate[WindLog](date)
        try {
          // link each GPS log to one wind log
          //linkGpsLogToSensorLog(dataType, logs)
          val firstTime = logs.head.getTimestamp
          val lastTime = logs.last.getTimestamp
          // get sensors
          val sensors = Sensor.getByDatetime(firstTime, lastTime).filter(s => s.datatype == DataImporter.Types.WIND)
          // get GPS logs
          val gLogs = getByTimeInterval[GpsLog](firstTime, lastTime, false)
          val batchId = UUID.randomUUID().toString
          //val batchId = "1234"
          spatializationWorker ! Message.SetSpatializationBatch(batchId, gLogs, sensors, logs)
          batchId
        } catch {
          case ex: Exception => ex.printStackTrace(); ""
        }
      }
      case _ => ""
    }
    batchId
  }

  /**
   * Get the distinct dates for which there is logs
   * @return A list of dates (as String)
   */
  def getDates: List[String] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val q = em.createQuery("SELECT DISTINCT cast(timestamp as date) FROM "+ classOf[GpsLog].getName)
      val dates = q.getResultList.map(_.asInstanceOf[Date]).toList.reverse.map(ts => DateFormatHelper.selectYearFormatter.format(ts)) // use reverse to get the most recent date on top
      em.getTransaction().commit()
      em.close()
      dates
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    }
  }

  /**
   * Get the first and last log time for a specific date
   * @param date The date
   * @return The first and last log time
   */
  def getTimesForDate(date: Date): (String, String)= {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val afterDate = Calendar.getInstance()
      afterDate.setTime(date)
      afterDate.add(Calendar.DAY_OF_YEAR, 1)
      val q = em.createQuery("SELECT MIN(cast(timestamp as time)), MAX(cast(timestamp as time)) " +
        "FROM "+ classOf[GpsLog].getName + " WHERE timestamp BETWEEN :start AND :end")
      q.setParameter("start", date, TemporalType.DATE)
      q.setParameter("end", afterDate.getTime, TemporalType.DATE)
      val res = q.getSingleResult.asInstanceOf[Array[Object]]
      val firstTime = res(0).asInstanceOf[Date]
      val lastTime = res(1).asInstanceOf[Date]
      //println(firstTime +" - "+ lastTime)
      em.getTransaction().commit()
      em.close()
      (DateFormatHelper.selectTimeFormatter.format(firstTime), DateFormatHelper.selectTimeFormatter.format(lastTime))
    } catch {
      case nre: NoResultException => ("00:00:00", "00:00:00")
      case ex: Exception => ex.printStackTrace; ("00:00:00", "00:00:00")
    }
  }

  /**
    * Get the closest log (of a sensor) to a specified timestamp
    * @param logs The sensor logs to search in
    * @param ts The timestamp to match
    * @param marginInSeconds The margin +/- around the timestamp
    * @param sensorId The id of the sensor of interest
    * @return An option with the closest log we found
    */
  def getClosestLog(logs: List[SensorLog], ts: Date, marginInSeconds: Int, sensorId: Long): Option[SensorLog] = {
    //println("[DataLogManager] getClosestLog() - "+logs.head)
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
        log.getSensor.id == sensorId)
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
  /*def updateGeoPos[T: ClassTag](dataLogId: Long, pos: Point, em: EntityManager): Boolean = {
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
      case ex: Exception => {
        ex.printStackTrace()
        false
      }
    }
    /*finally {
      em.close
    }*/
  }*/

  /**
   * Link a sensor log to the corresponding gps log
   * @param dataLogId The id of the sensor log
   * @param gpsLogId The id of the GPS log
   * @param em The entity manager
   * @tparam T The type of sensor log to update
   * @return true if success
   */
  def linkSensorLogToGpsLog[T: ClassTag](dataLogId: Long, gpsLogId: Long, em: EntityManager): Boolean = {
    try {
      //em.getTransaction.begin
      val queryStr: String = "UPDATE " + classTag[T].runtimeClass.getName + " " +
        "SET gps_log_id=" + gpsLogId + " " +
        "WHERE id=" + dataLogId
      val q: Query = em.createQuery(queryStr)
      q.executeUpdate
      //println("linkSensorLogToGpsLog() - ["+ dataLogId +"]")
      //em.getTransaction.commit
      true
    }
    catch {
      case ex: Exception => {
        ex.printStackTrace()
        false
      }
    }
  }

  /**
   * Get the spatialization progress of a specific batch
   * @param batchId The batch id
   * @return The progress as a percentage
   */
  def spatializationProgress(batchId: String): Option[Long] = {
    val percentage = BatchManager.batchProgress.get(batchId).map {
      case (nbTot, nbDone) => math.floor((nbDone.toDouble / nbTot.toDouble) * 100).toLong
    }
    if(percentage.isDefined && percentage.get == 100L) {
      BatchManager.cleanCompletedBatch(batchId)
    }
    percentage
  }

}
