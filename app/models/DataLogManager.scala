package models

import controllers.util._
import javax.persistence.{Query, EntityManager, TemporalType, NoResultException}
import scala.reflect.{ClassTag, classTag}
import java.util.{UUID, Calendar, Date}
import scala.collection.JavaConversions._
import models._
import controllers.database._
import play.libs.Akka
import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.Some
import play.api.Logger
import play.api.libs.json.{Json, JsValue}
import com.vividsolutions.jts.geom.Point
import controllers.util.json.JsonSerializable
import scala.Some
import models.spatial.{TrajectoryPoint, GpsLog}
import com.google.gson.{JsonArray, JsonElement, JsonObject}

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
   * @param date
   * @param geoLocated Filter logs according to the relation sensor log <-> gps log (Some(false) -> get only not geolocated logs, Some(true) -> get only geolocated logs)
   * @param emOpt The entity manager to use (optional)
   * @tparam T The type of data to get
   * @return A list with the logs
   */
  def getByDate[T:ClassTag](date: Date, geoLocated: Option[Boolean] = None, emOpt: Option[EntityManager] = None): List[T] = {
    val afterDate = Calendar.getInstance()
    afterDate.setTime(date)
    afterDate.add(Calendar.DAY_OF_YEAR, 1)
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val clazz = classTag[T].runtimeClass
      val geoCondition = geoLocated.map(b => {
        if(b) " AND sl.gpsLog IS NOT NULL" else " AND sl.gpsLog IS NULL"
      }).getOrElse("")
      val q = em.createQuery("from "+ clazz.getName +" sl WHERE timestamp BETWEEN :start AND :end" + geoCondition + " ORDER BY timestamp")
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
  def getByTimeInterval[T: ClassTag](
                                      startTime: Date,
                                      endTime: Date,
                                      geoOnly: Boolean,
                                      maxNb: Option[Int] = None,
                                      emOpt: Option[EntityManager] = None): List[T] = {
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
      val reducedLogList = if (maxNb.isDefined && logs.length > maxNb.get) {
        val moduloFactor = math.ceil(logs.length.toDouble / maxNb.get.toDouble).toInt
        //println("logs list reduced by factor: "+ moduloFactor)
        for {
          (sl, ind) <- logs.zipWithIndex
          if (ind % moduloFactor == 0)
        } yield {
          sl
        }
      } else logs
      reducedLogList
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  def getDataByMission(
                     datatype: String,
                     missionId: Long,
                     deviceIdList: List[Long],
                     maxNb: Option[Int]): Map[String, List[JsonSerializable]] = {
    datatype match {
      case DataImporter.Types.TEMPERATURE => {
        getByMissionAndDevice[TemperatureLog](missionId, deviceIdList, maxNb)
      }
      case DataImporter.Types.WIND => {
        getByMissionAndDevice[WindLog](missionId, deviceIdList, maxNb)
      }
      case DataImporter.Types.COMPASS => {
        getByMissionAndDevice[CompassLog](missionId, deviceIdList, maxNb)
      }
      case DataImporter.Types.RADIOMETER => {
        getByMissionAndDevice[RadiometerLog](missionId, deviceIdList, maxNb)
      }
      /*case DataImporter.Types.GPS => {
        val gpsLogsNative = DataLogManager.getByTimeInterval[GpsLog](startDate, endDate, false, maxNb)
        val gpsLogs = if (coordinateFormat == "swiss") {
          gpsLogsNative.map(gl => {
            val arr = ApproxSwissProj.WGS84toLV03(gl.getGeoPos.getY, gl.getGeoPos.getX, 0L).toList // get east, north, height
            val geom = CoordinateHelper.wktToGeometry("POINT("+ arr(0) +" "+ arr(1) +")")
            gl.setGeoPos(geom.asInstanceOf[Point])
            gl
          })
        } else gpsLogsNative
        Map("gps sensor" -> gpsLogs)
      }*/
      case _ => {
        println("GET data - Unknown data type")
        Map[String, List[JsonSerializable]]()
      }
    }
  }

  /**
   * Get the data logs of a type between a time interval, filtered by sensor id
   * @param startTime The start time of the interval
   * @param endTime The end time of the interval
   * @param geoOnly Indicates if we want only the logs mapped to a gps log
   * @param deviceIdList The list of devices we are interested in
   * @param emOpt The optional entityManager
   * @tparam T The type of data log to get
   * @return A list of the logs in the specified time interval
   */
  private def getByTimeIntervalAndDevice[T: ClassTag](
                                               startTime: Date,
                                               endTime: Date,
                                               geoOnly: Boolean,
                                               deviceIdList: List[Long],
                                               maxNb: Option[Int],
                                               emOpt: Option[EntityManager] = None): Map[String, List[T]] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val devices = Device.getById(deviceIdList, emOpt)
      val clazz = classTag[T].runtimeClass
      // where timestamp BETWEEN '2013-05-14 16:30:00'::timestamp AND '2013-05-14 16:33:25'::timestamp ;
      val geoCondition = if (geoOnly) "AND log.gpsLog IS NOT NULL " else ""
      val deviceCondition = if (deviceIdList.nonEmpty) "AND log.device.id IN ("+ deviceIdList.mkString(",") +") " else ""
      val queryStr = "FROM "+ clazz.getName +" log WHERE timestamp BETWEEN :start AND :end "+ geoCondition + deviceCondition +
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
      val logsMapBySensorId = logs.map(_.asInstanceOf[SensorLog]).groupBy(_.getDevice.id)
      logsMapBySensorId.map { case (sId, logs) => {
          val reducedLogList = if (maxNb.isDefined && logs.length > maxNb.get) {
            val moduloFactor = math.ceil(logs.length.toDouble / maxNb.get.toDouble).toInt
            //println("logs list reduced by factor: "+ moduloFactor)
            for {
              (sl, ind) <- logs.zipWithIndex
              if (ind % moduloFactor == 0)
            } yield {
              sl
            }
          } else logs
          println("Nb of logs returned: "+reducedLogList.length)
          (devices.get(sId).get.name, reducedLogList.map(_.asInstanceOf[T]))
        }
      }
    } catch {
      case nre: NoResultException => println("No result !!"); Map()
      case ex: Exception => ex.printStackTrace; Map()
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  private def getByMissionAndDevice[T: ClassTag](
                                               missionId: Long,
                                               deviceIdList: List[Long],
                                               maxNb: Option[Int],
                                               emOpt: Option[EntityManager] = None): Map[String, List[T]] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val devices = Device.getById(deviceIdList, emOpt)
      val clazz = classTag[T].runtimeClass
      val deviceCondition = if (deviceIdList.nonEmpty) "AND log.device.id IN ("+ deviceIdList.mkString(",") +") " else ""
      val queryStr = "FROM "+ clazz.getName +" log WHERE log.mission.id = " + missionId + deviceCondition +
        "ORDER BY timestamp"
      //println(queryStr)
      val q = em.createQuery(queryStr)
      //println(q.getResultList)
      val start = new Date
      val logs = q.getResultList.map(_.asInstanceOf[T]).toList
      val diff = (new Date).getTime - start.getTime
      println("Nb of logs queried: "+logs.length + " ["+ diff +"ms]")
      if (emOpt.isEmpty) em.getTransaction().commit()
      val logsMapBySensorId = logs.map(_.asInstanceOf[SensorLog]).groupBy(_.getDevice.id)
      logsMapBySensorId.map { case (sId, logs) => {
        val reducedLogList = if (maxNb.isDefined && logs.length > maxNb.get) {
          val moduloFactor = math.ceil(logs.length.toDouble / maxNb.get.toDouble).toInt
          //println("logs list reduced by factor: "+ moduloFactor)
          for {
            (sl, ind) <- logs.zipWithIndex
            if (ind % moduloFactor == 0)
          } yield {
            sl
          }
        } else logs
        println("Nb of logs returned: "+reducedLogList.length)
        (devices.get(sId).get.name, reducedLogList.map(_.asInstanceOf[T]))
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
    val geoLocated = Some(false)
    val batchId = dataType match {
      case DataImporter.Types.COMPASS => {
        val logs = getByDate[CompassLog](date, geoLocated)
        try {
          // link each GPS log to one compass log
          //linkGpsLogToSensorLog(dataType, logs)
          val firstTime = logs.head.getTimestamp
          val lastTime = logs.last.getTimestamp
          // get sensors
          val devices = Device.getByDatetime(firstTime, lastTime).filter(s => s.datatype == DataImporter.Types.COMPASS)
          // get GPS logs
          val gLogs = getByTimeInterval[GpsLog](firstTime, lastTime, false)
          val batchId = UUID.randomUUID().toString
          spatializationWorker ! Message.SetSpatializationBatch(batchId, gLogs, devices, logs)
          batchId
        } catch {
          case ex: Exception => ex.printStackTrace(); ""
        }
      }
      case DataImporter.Types.TEMPERATURE => {
        val logs = getByDate[TemperatureLog](date, geoLocated)
        //println("XX - "+logs.head)
        try {
          // link each GPS log to one sensor log
          //linkGpsLogToSensorLog(dataType, logs)
          val firstTime = logs.head.getTimestamp
          val lastTime = logs.last.getTimestamp
          // get sensors
          val devices = Device.getByDatetime(firstTime, lastTime).filter(s => s.datatype == DataImporter.Types.TEMPERATURE)
          // get GPS logs
          val gLogs = getByTimeInterval[GpsLog](firstTime, lastTime, false)
          val batchId = UUID.randomUUID().toString
          spatializationWorker ! Message.SetSpatializationBatch(batchId, gLogs, devices, logs)
          batchId
        } catch {
          case ex: Exception => ex.printStackTrace(); ""
        }
      }
      case DataImporter.Types.RADIOMETER => {
        val logs = getByDate[RadiometerLog](date, geoLocated)
        try {
          // link each GPS log to one radiometer log
          //linkGpsLogToSensorLog(dataType, logs)
          val firstTime = logs.head.getTimestamp
          val lastTime = logs.last.getTimestamp
          // get sensors
          val devices = Device.getByDatetime(firstTime, lastTime).filter(s => s.datatype == DataImporter.Types.RADIOMETER)
          // get GPS logs
          val gLogs = getByTimeInterval[GpsLog](firstTime, lastTime, false)
          val batchId = UUID.randomUUID().toString
          //val batchId = "1234"
          spatializationWorker ! Message.SetSpatializationBatch(batchId, gLogs, devices, logs)
          batchId
        } catch {
          case ex: Exception => ex.printStackTrace(); ""
        }
      }
      case DataImporter.Types.WIND => {
        val logs = getByDate[WindLog](date, geoLocated)
        try {
          // link each GPS log to one wind log
          //linkGpsLogToSensorLog(dataType, logs)
          val firstTime = logs.head.getTimestamp
          val lastTime = logs.last.getTimestamp
          // get sensors
          val devices = Device.getByDatetime(firstTime, lastTime).filter(s => s.datatype == DataImporter.Types.WIND)
          // get GPS logs
          val gLogs = getByTimeInterval[GpsLog](firstTime, lastTime, false)
          val batchId = UUID.randomUUID().toString
          //val batchId = "1234"
          spatializationWorker ! Message.SetSpatializationBatch(batchId, gLogs, devices, logs)
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
      dates
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      em.close()
    }
  }

  /**
   * Get the distinct dates for which there is logs
   * @return A list of dates (as String) and vehicles
   */
  def getMissionDates: List[(String, String)] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val q = em.createQuery("SELECT DISTINCT cast(m.departureTime as date), m.vehicle.name FROM "+ classOf[Mission].getName +" m")
      //val dates = q.getResultList.map(_.asInstanceOf[Date]).toList.map(ts => DateFormatHelper.selectYearFormatter.format(ts))
      val dates = q.getResultList.map(_.asInstanceOf[Array[Object]]).toList.map(obj =>
        (DateFormatHelper.selectYearFormatter.format(obj(0).asInstanceOf[Date]), obj(1).asInstanceOf[String])
      )
      em.getTransaction().commit()
      dates
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      em.close()
    }
  }

  /**
   * Get the missions for a specific date
   * @param date The date of the missions to get
   * @return A list of missions
   */
  def getMissionsForDate(date: Date): List[Mission] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val afterDate = Calendar.getInstance()
      afterDate.setTime(date)
      afterDate.add(Calendar.DAY_OF_YEAR, 1)
      val q = em.createQuery("FROM "+ classOf[Mission].getName+" m  WHERE departuretime BETWEEN :start AND :end ORDER BY departuretime DESC")
      q.setParameter("start", date, TemporalType.DATE)
      q.setParameter("end", afterDate.getTime, TemporalType.DATE)
      val missions = q.getResultList.map(_.asInstanceOf[Mission]).toList
      em.getTransaction().commit()
      missions
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      em.close()
    }
  }

  /**
   * Get the trajectory (as linestring) of a mission
   * @param missionId The mission id
   * @return The trajectory in GeoJSON object
   */
  def getTrajectoryLinestring(missionId: Int): JsValue = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val nativeQuery = "SELECT ST_AsGeoJSON(trajectory) FROM mission WHERE id = "+missionId
      val q = em.createNativeQuery(nativeQuery)
      val res = q.getSingleResult.asInstanceOf[String]
      em.getTransaction().commit()
      Json.parse(res)
    } catch {
      case nre: NoResultException => Json.parse("{\"error\": \"no result\"}")
      case ex: Exception => ex.printStackTrace; Json.parse("{\"error\": \"exception\"}")
    } finally {
      em.close()
    }
  }

  def getTrajectoryPoints(missionId: Int,
                          maxNb: Option[Int],
                          emOpt: Option[EntityManager] = None): List[TrajectoryPoint] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val queryStr = "FROM "+ classOf[TrajectoryPoint].getName +" tp WHERE tp.mission.id = "+ missionId +" ORDER BY timestamp"
      //println(queryStr)
      val q = em.createQuery(queryStr, classOf[TrajectoryPoint])
      //println(q.getResultList)
      val start = new Date
      val pointList = q.getResultList.toList
      val diff = (new Date).getTime - start.getTime
      println("Nb of points queried: "+ pointList.length + " ["+ diff +"ms]")
      if (emOpt.isEmpty) em.getTransaction().commit()

      //pointList.map(pt => {
        val reducedPointList = if (maxNb.isDefined && pointList.length > maxNb.get) {
          val moduloFactor = math.ceil(pointList.length.toDouble / maxNb.get.toDouble).toInt
          //println("logs list reduced by factor: "+ moduloFactor)
          for {
            (sl, ind) <- pointList.zipWithIndex
            if (ind % moduloFactor == 0)
          } yield {
            sl
          }
        } else pointList
        println("Nb of points returned: "+reducedPointList.length)
        reducedPointList
      //})
    } catch {
      case nre: NoResultException => println("No result !!"); List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  /**
   * Get the footage for a specific date
   * @param date The date of the footage to get
   * @return the footage coordinate (JSON)
   */
  def getFootageForDate(date: Date): JsValue = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val afterDate = Calendar.getInstance()
      afterDate.setTime(date)
      afterDate.add(Calendar.DAY_OF_YEAR, 1)
      val nativeQuery = "SELECT imagename, ST_X(leftuppercorner) AS xlu, ST_Y(leftuppercorner) AS ylu, ST_X(leftlowercorner) AS xll, ST_Y(leftlowercorner) AS yll, " +
        "ST_X(rightuppercorner) AS xru, ST_Y(rightuppercorner) AS yru, ST_X(rightlowercorner) AS xrl, ST_Y(rightlowercorner) AS yrl FROM device" +
        " INNER JOIN measurement ON device.id=measurement.deviceid" +
        " INNER JOIN footage ON footage.id=measurement.footageid" +
        " WHERE timestamp BETWEEN :start AND :end";
      val q = em.createNativeQuery(nativeQuery)
      q.setParameter("start", date, TemporalType.DATE)
      q.setParameter("end", afterDate.getTime, TemporalType.DATE)
      val res = q.getResultList.map(_.asInstanceOf[Array[Object]])
      val jsArr = new JsonArray
      res.foreach(resultArray => {
        val dataJson = (new JsonObject).asInstanceOf[JsonElement]
        dataJson.getAsJsonObject.addProperty("imagename", resultArray(0).asInstanceOf[String])
        dataJson.getAsJsonObject.addProperty("xlu", resultArray(1).asInstanceOf[Double])
        dataJson.getAsJsonObject.addProperty("ylu", resultArray(2).asInstanceOf[Double])
        dataJson.getAsJsonObject.addProperty("xll", resultArray(3).asInstanceOf[Double])
        dataJson.getAsJsonObject.addProperty("yll", resultArray(4).asInstanceOf[Double])
        dataJson.getAsJsonObject.addProperty("xru", resultArray(5).asInstanceOf[Double])
        dataJson.getAsJsonObject.addProperty("yru", resultArray(6).asInstanceOf[Double])
        dataJson.getAsJsonObject.addProperty("xrl", resultArray(7).asInstanceOf[Double])
        dataJson.getAsJsonObject.addProperty("yrl", resultArray(8).asInstanceOf[Double])
        jsArr.add(dataJson)
      })

      em.getTransaction().commit()
      Json.toJson(Json.parse(jsArr.toString))
      //Json.parse(jsStr)
    } catch {
      case nre: NoResultException => Json.parse("{\"error\": \"no result\"}")
      case ex: Exception => ex.printStackTrace; Json.parse("{\"error\": \"exception\"}")
    } finally {
      em.close()
    }
  }

  /**
   * Get the first and last log time for a specific date
   * @param date The date
   * @return The first and last log time
   */
  def getTimesForDateAndSet(date: Date, setNumber: Option[Int]): (String, String) = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val afterDate = Calendar.getInstance()
      afterDate.setTime(date)
      afterDate.add(Calendar.DAY_OF_YEAR, 1)
      val setCondition = setNumber.map(sn => " AND set_number = "+sn).getOrElse("")
      val q = em.createQuery("SELECT MIN(cast(timestamp as time)), MAX(cast(timestamp as time)) " +
        "FROM "+ classOf[GpsLog].getName + " WHERE timestamp BETWEEN :start AND :end" + setCondition)
      q.setParameter("start", date, TemporalType.DATE)
      q.setParameter("end", afterDate.getTime, TemporalType.DATE)
      val res = q.getSingleResult.asInstanceOf[Array[Object]]
      val firstTime = res(0).asInstanceOf[Date]
      val lastTime = res(1).asInstanceOf[Date]
      //println(firstTime +" - "+ lastTime)
      em.getTransaction().commit()
      (DateFormatHelper.selectTimeFormatter.format(firstTime), DateFormatHelper.selectTimeFormatter.format(lastTime))
    } catch {
      case nre: NoResultException => ("00:00:00", "00:00:00")
      case ex: Exception => ex.printStackTrace; ("00:00:00", "00:00:00")
    } finally {
      em.close()
    }
  }

  /**
   * Get the list of set numbers for a specific date
   * @param date The date we want
   * @return A list of set numbers
   */
  def getLogSetsForDate(date: Date): List[Int] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val afterDate = Calendar.getInstance()
      afterDate.setTime(date)
      afterDate.add(Calendar.DAY_OF_YEAR, 1)
      val q = em.createQuery("SELECT DISTINCT log.setNumber FROM "+ classOf[GpsLog].getName+" log  WHERE timestamp BETWEEN :start AND :end")
      q.setParameter("start", date, TemporalType.DATE)
      q.setParameter("end", afterDate.getTime, TemporalType.DATE)
      val setNumbers = q.getResultList.map(_.asInstanceOf[Int]).toList.sorted
      em.getTransaction().commit()
      setNumbers
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      em.close()
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
        log.getDevice.id == sensorId)
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
   * Get the closest GPS log to a specified timestamp
   * @param gpsLogs The GPS logs to search in
   * @param ts The timestamp to match
   * @param marginInSeconds The margin +/- around the timestamp
   * @return An option with the closest GPS log we found
   */
  def getClosestGpsLog(gpsLogs: List[GpsLog], ts: Date, marginInSeconds: Int): Option[GpsLog] = {
    //println("[DataLogManager] getClosestLog() - "+logs.head)
    val beforeDate = Calendar.getInstance()
    beforeDate.setTime(ts)
    beforeDate.add(Calendar.SECOND, -marginInSeconds)
    val afterDate = Calendar.getInstance()
    afterDate.setTime(ts)
    afterDate.add(Calendar.SECOND, marginInSeconds)
    //val closeLogs = getByTimeIntervalAndSensor[T](beforeDate.getTime, afterDate.getTime, sensorId, Some(em))
    val closeLogs = gpsLogs.filter(log =>
      (log.getTimestamp.getTime > beforeDate.getTime.getTime &&
        log.getTimestamp.getTime < afterDate.getTime.getTime)
    )
    if (closeLogs.nonEmpty) {
      val (closestPoint, diff) = closeLogs.map(cl => {
        val timeDiff = math.abs(cl.getTimestamp.getTime - ts.getTime)
        (cl, timeDiff)
      }).minBy(_._2)
      Some(closestPoint)
    } else {
      println("[WARNING] No close GPS log for TS: "+DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
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
  def spatializationProgress(batchId: String): Option[(String, Long)] = {
    val percentage = BatchManager.batchProgress.get(batchId).map {
      case (hint, nbTot, nbDone) => (hint, math.floor((nbDone.toDouble / nbTot.toDouble) * 100).toLong)
    }
    if(percentage.isDefined && percentage.get._2 == 100L) {
      BatchManager.cleanCompletedBatch(batchId)
    }
    percentage
  }

  /**
   * Get the next set number for data (multiple data sets can be collected on one day)
   * @param date The date of the set
   * @tparam T The type of data want
   * @return An option with the next set number to use
   */
  def getNextSetNumber[T:ClassTag](date: Date): Option[Int] = {
    val MAX_TIME_DIFF_BETWEEN_SETS = 1000 * 60 * 0.5 // 30 seconds
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val afterDate = Calendar.getInstance()
      afterDate.setTime(date)
      afterDate.add(Calendar.DAY_OF_YEAR, 1)
      val q = em.createQuery("FROM "+ classTag[GpsLog].runtimeClass.getName + " WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC", classOf[GpsLog])
      q.setParameter("start", date, TemporalType.DATE)
      q.setParameter("end", afterDate.getTime, TemporalType.DATE)
      q.setMaxResults(1)
      val lastLog = q.getSingleResult
      em.getTransaction().commit()
      val diff = date.getTime - lastLog.asInstanceOf[SensorLog].getTimestamp.getTime
      val newSetNumber = if (math.abs(diff) < MAX_TIME_DIFF_BETWEEN_SETS) {
        // if diff is smaller than threshold, keep same set number
        lastLog.getSetNumber
      } else lastLog.getSetNumber + 1
      Logger.warn("Previous logs exist for this date -> set number = "+ newSetNumber +" [diff: "+ diff +"ms]")
      Some(newSetNumber)
    } catch {
      case nre: NoResultException => {
        Logger.warn("No previous log for this date -> set number = 0")
        Some(0)
      }
      case ex: Exception => ex.printStackTrace; None
    } finally {
      em.close()
    }
  }

}
