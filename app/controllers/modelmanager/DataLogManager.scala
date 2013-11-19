package controllers.modelmanager

import controllers.util._
import javax.persistence.{Query, EntityManager, TemporalType, NoResultException}
import scala.reflect.{ClassTag, classTag}
import java.util.{TimeZone, Calendar, Date}
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
import models.spatial.TrajectoryPoint
import com.google.gson.{Gson, JsonArray, JsonElement, JsonObject}
import scala.collection.mutable

object DataLogManager {

  /**
   * The actor which handles the messages to insert the different data
   */
  val insertionWorker = Akka.system.actorOf(Props[InsertionWorker], name = "insertionWorker")
  /**
   * The actor which handles the messages to process the insertion batches
   */
  val insertionBatchWorker = Akka.system.actorOf(Props[InsertionBatchWorker], name = "insertionBatchWorker")
  //val TIMEOUT = 5 seconds
  //implicit val timeout = Timeout(TIMEOUT) // needed for `?` below

  /**
   * Get a data log by Id (give log type in parameter using scala 2.10 ClassTag - https://wiki.scala-lang.org/display/SW/2.10+Reflection+and+Manifest+Migration+Notes)
   * @param sId The log id
   * @tparam T The type of data log to get
   * @return The log
   */
  def getById[T: ClassTag](sId: Long, emOpt: Option[EntityManager] = None): Option[T] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val clazz = classTag[T].runtimeClass
      val q = em.createQuery("from "+ clazz.getName +" where id = "+sId)
      val log = q.getSingleResult.asInstanceOf[T]
      if (emOpt.isEmpty) em.getTransaction().commit()
      Some(log)
    } catch {
      case nre: NoResultException => None
      case ex: Exception => ex.printStackTrace; None
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

  /**
   * Get the device data by mission. It is called from getData() (in GetApi.scala)
   * @param datatype The type of data to get
   * @param missionId The id of the mission
   * @param deviceIdList The list of deivec to get
   * @param startDate The start date (optional)
   * @param endDate The end date (optional)
   * @param maxNb The maximum number of logs (optional)
   * @param syncWithTrajectory true if data is get in a graph shown as overlay to a map (sync with trajectory is necessary) - default is true
   * @return A map with the logs by device
   */
  def getDataByMission(
                     datatype: String,
                     missionId: Long,
                     deviceIdList: List[Long],
                     startDate: Option[Date],
                     endDate: Option[Date],
                     maxNb: Option[Int],
                     syncWithTrajectory: Boolean): Map[Device, List[JsonSerializable]] = {
    datatype match {
      case DataImporter.Types.ALTITUDE => {
        Mission.getAltitude(missionId, startDate, endDate, maxNb)
      }
      case DataImporter.Types.SPEED => {
        Mission.getSpeed(missionId, startDate, endDate, maxNb)
      }
      case DataImporter.Types.HEADING => {
        Mission.getHeading(missionId, startDate, endDate, maxNb)
      }
      case _ => {
        // all other device type
        getByMissionAndDevice[SensorLog](missionId, deviceIdList, startDate, endDate, maxNb, syncWithTrajectory)
      }
    }
  }

  /**
   * Get data log by mission and device
   * @param missionId The id of the mission
   * @param deviceIdList The list of devices we are interested in
   * @param startTime
   * @param endTime
   * @param maxNb The maximum number of logs to return
   * @param syncWithTrajectory true if data is get in a graph shown as overlay to a map (sync with trajectory is necessary)
   * @param emOpt An optional entity manager
   * @tparam T The type of log to get
   * @return A map of logs by device
   */
  private def getByMissionAndDevice[T: ClassTag](
                                               missionId: Long,
                                               deviceIdList: List[Long],
                                               startTime: Option[Date],
                                               endTime: Option[Date],
                                               maxNb: Option[Int],
                                               syncWithTrajectory: Boolean,
                                               emOpt: Option[EntityManager] = None): Map[Device, List[T]] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val devices = Device.getById(deviceIdList, emOpt)
      val clazz = classTag[T].runtimeClass
      val deviceCondition = if (deviceIdList.nonEmpty) " AND log.device.id IN ("+ deviceIdList.mkString(",") +") " else ""
      val timeCondition = if (startTime.isDefined && endTime.isDefined) "AND timestamp BETWEEN :start AND :end " else ""
      val queryStr = "FROM "+ clazz.getName +" log WHERE log.mission.id = " + missionId + deviceCondition + timeCondition +
        "ORDER BY timestamp"
      //println(queryStr)
      val q = em.createQuery(queryStr)
      if (startTime.isDefined && endTime.isDefined) {
        //println("Time condition: "+ startTime.get + " - "+ endTime.get)
        q.setParameter("start", startTime.get, TemporalType.TIMESTAMP)
        q.setParameter("end", endTime.get, TemporalType.TIMESTAMP)
      }
      //println(q.getResultList)
      val start = new Date
      val logList = q.getResultList.map(_.asInstanceOf[T]).toList
      val diff = (new Date).getTime - start.getTime
      println("Nb of logs queried: "+logList.length + " ["+ diff +"ms]")
      if (emOpt.isEmpty) em.getTransaction().commit()
      val logsMapBySensorId = logList.map(_.asInstanceOf[SensorLog]).groupBy(_.device.id)
      logsMapBySensorId.map { case (sId, rawLogs) => {
        // adapt to trajectory points time range - pad with zero values (if requested)
        val logs = if (syncWithTrajectory) mapTimeRangeToTrajectory(missionId, rawLogs, emOpt) else rawLogs
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
        (devices.get(sId).get, reducedLogList.map(_.asInstanceOf[T]))
      }}
    } catch {
      case nre: NoResultException => println("No result !!"); Map()
      case ex: Exception => ex.printStackTrace; Map()
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  /**
   * Adapt time range of data logs to time range of trajectory.
   * Many times data logging begins after gps logging, this introduces a time range difference
   * between data logs and trajectory points and causes problems when plotting both graphs.
   * Need to add fake values to sensor logs list.
   * @param missionId The id of the mission
   * @param logs The data logs
   * @param emOpt An optional entity manager
   * @return The list of sensor logs extended by "virtual" values if needed
   */
  private def mapTimeRangeToTrajectory(missionId: Long, logs: List[SensorLog], emOpt: Option[EntityManager]): List[SensorLog] = {
    val firstTimeData = logs.head.timestamp
    val lastTimeData = logs.last.timestamp
    val timeDiffBetweenDataLogs = logs(1).timestamp.getTime - logs(0).timestamp.getTime
    val (firstTimeTraj, lastTimeTraj) = getFirstAndLastTimeOfTrajectory(missionId, emOpt).get
    val timeDiffBetweenFirstDataLogAndFirstTrajectoryPoint = firstTimeData.getTime - firstTimeTraj.getTime
    if (timeDiffBetweenFirstDataLogAndFirstTrajectoryPoint > timeDiffBetweenDataLogs) {
      //Logger.info("Need padding !")
      // need padding
      val virtualSensorLogs = new mutable.MutableList[SensorLog]()
      var virtualLogTS = firstTimeTraj.getTime
      while(virtualLogTS < firstTimeData.getTime) {
        // create virtual sensor log with same value as the first 'real' sensor log
        val log = new SensorLog(logs.head.mission, logs.head.device, new Date(virtualLogTS), logs.head.value)
        virtualSensorLogs += log
        // keep same time diff between logs
        virtualLogTS += timeDiffBetweenDataLogs
      }
      virtualSensorLogs.toList ::: logs
    } else {
      // does not need padding, so simply return 'logs'
      logs
    }
  }

  /**
   * Get the first and last time for a trajectory
   * @param missionId The id of the mission
   * @param emOpt An optional entity manager
   * @return An option with the first time and last time of the trajectory logs
   */
  private def getFirstAndLastTimeOfTrajectory(missionId: Long, emOpt: Option[EntityManager]): Option[(Date, Date)] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val query = "SELECT MIN(timestamp) AS firsttime, MAX(timestamp) AS lasttime FROM "+ classOf[TrajectoryPoint].getName +" WHERE mission_id = :missionId";
      val q = em.createQuery(query)
      q.setParameter("missionId", missionId)
      val res = q.getSingleResult.asInstanceOf[Array[Object]]
      if (emOpt.isEmpty) em.getTransaction().commit()
      Some((res(0).asInstanceOf[Date], res(1).asInstanceOf[Date]))
    } catch {
      case ex: Exception => ex.printStackTrace(); None
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }


  /**
   * Get the closest GPS log to a specified timestamp
   * @param trajectoryPoints The trajectory points to search in
   * @param ts The timestamp to match
   * @param marginInSeconds The margin +/- around the timestamp
   * @return An option with the closest trajectory point found
   */
  def getClosestGpsLog(trajectoryPoints: List[TrajectoryPoint], ts: Date, marginInSeconds: Int): Option[TrajectoryPoint] = {
    //println("[DataLogManager] getClosestLog() - "+logs.head)
    val beforeDate = Calendar.getInstance()
    beforeDate.setTime(ts)
    beforeDate.add(Calendar.SECOND, -marginInSeconds)
    val afterDate = Calendar.getInstance()
    afterDate.setTime(ts)
    afterDate.add(Calendar.SECOND, marginInSeconds)
    //val closeLogs = getByTimeIntervalAndSensor[T](beforeDate.getTime, afterDate.getTime, sensorId, Some(em))
    val closePoints = trajectoryPoints.filter(pt =>
      (pt.getTimestamp.getTime > beforeDate.getTime.getTime &&
        pt.getTimestamp.getTime < afterDate.getTime.getTime)
    )
    if (closePoints.nonEmpty) {
      val (closestPoint, diff) = closePoints.map(cl => {
        val timeDiff = math.abs(cl.getTimestamp.getTime - ts.getTime)
        (cl, timeDiff)
      }).minBy(_._2)
      Some(closestPoint)
    } else {
      println("[WARNING] No close trajectory point for TS: "+DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
      None
    }
  }



}
