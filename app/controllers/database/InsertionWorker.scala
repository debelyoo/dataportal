package controllers.database

import akka.actor.Actor
import controllers.util._
import models.spatial._
import models._

import com.vividsolutions.jts.geom.{Geometry, Point}
import scala.collection.immutable.Queue
import controllers.database.BatchManager._
import scala.Some
import java.util.{TimeZone, Date}
import play.api.Logger
import scala.Some
import org.hibernate.Hibernate
import scala.Some
import controllers.modelmanager.{DeviceManager, DataLogManager}
import scala.Some
import scala.Some
import scala.Some
import scala.Some
import scala.Some
import javax.persistence.EntityManager

class InsertionWorker extends Actor {
  implicit def queue2finitequeue[A](q: Queue[A]) = new FiniteQueue[A](q)
  var logCache = Queue[String]()
  var lastSpeedPoint: Option[TrajectoryPoint] = None // most recent point for which the instantaneous speed has been calculated
  val LOG_CACHE_MAX_SIZE = 20

  def createUniqueString(str1: String, str2: String) = str1+":"+str2

  def receive = {
    case Message.SetInsertionBatch(batchId, filename, dataType, lines, sensors, missionId) => {
      insertionBatches(batchId) = (lines, sensors)
      batchProgress(batchId) = (filename, lines.length, 0)
      //Logger.info("insertionBatches: "+insertionBatches.size)
      DataLogManager.insertionBatchWorker ! Message.Work(batchId, dataType, missionId)
    }
    case Message.InsertTemperatureLog(batchId, missionId, ts, sensor, temperatureValue) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        em.getTransaction().begin()
        // fetch the sensor in DB, to get its id
        val sensorInDb = DeviceManager.getByNameAndAddress(sensor.getName, sensor.getAddress, Some(em))
        assert(sensorInDb.isDefined, {println("[Message.InsertTemperatureLog] Sensor is not in Database !")})
        //println("[RCV message] - insert temperature log: "+temperatureValue+", "+ sensorInDb.get)
        val uniqueString = createUniqueString(sensor.getAddress, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        if (!logCache.contains(uniqueString)) {
          // insert log in cache only if cache does not contain unique string (address-timestamp)
          val missionOpt = DataLogManager.getById[Mission](missionId, Some(em))
          assert(missionOpt.isDefined, {println("[Message.InsertTemperatureLog] Mission is not in Database !")})
          missionOpt.get.addDevice(sensorInDb.get)
          missionOpt.get.save(Some(em))
          val tl = new TemperatureLog(sensorInDb.get, ts, temperatureValue, missionOpt.get)
          val persisted = tl.save(Some(em)) // persist in DB
          if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
            Some(true)
          } else None
        } else Some(false)
        BatchManager.updateBatchProgress(batchId, "Insertion")
        em.getTransaction().commit()
      } catch {
        case ae: AssertionError => None
      } finally {
        em.close()
      }
    }
    case Message.InsertCompassLog(batchId, trajectoryPoints, ts, compassValue) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        em.getTransaction().begin()
        //val sensorInDb = DeviceManager.getByNameAndAddress(sensor.getName, sensor.getAddress, Some(em))
        //assert(sensorInDb.isDefined, {println("[Message.InsertCompassLog] Sensor is not in Database !")})
        //println("[RCV message] - insert compass log: "+compassValue+", "+ sensorInDb.get)
        //val uniqueString = createUniqueString(sensor.getAddress, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        //if (!logCache.contains(uniqueString)) {
          val ptOpt = DataLogManager.getClosestGpsLog(trajectoryPoints, ts, 1)
          for (pt <- ptOpt) {
            val p = DataLogManager.getById[TrajectoryPoint](pt.getId, Some(em)).get // need to reload the point in this entity manager to be persisted below
            p.setHeading(compassValue)
            p.save(Some(em))
          }

          //val cl = new CompassLog(sensorInDb.get, ts, compassValue, missionOpt.get)
          //val persisted = cl.save(Some(em)) // persist in DB
          /*if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
            Some(true)
          } else None*/
        //} else Some(false)
        BatchManager.updateBatchProgress(batchId, "Insertion")
        em.getTransaction().commit()
      } catch {
        case ex: Exception => ex.printStackTrace();None
      } finally {
        em.close()
      }
    }
    case Message.InsertWindLog(batchId, missionId, ts, sensor, windValue) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        em.getTransaction().begin()
        val sensorInDb = DeviceManager.getByNameAndAddress(sensor.getName, sensor.getAddress, Some(em))
        assert(sensorInDb.isDefined, {println("[Message.InsertWindLog] Sensor is not in Database !")})
        //println("[RCV message] - insert wind log: "+ windValue +", "+ sensorInDb.get)
        val uniqueString = createUniqueString(sensor.getAddress, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        val res = if (!logCache.contains(uniqueString)) {
          val missionOpt = DataLogManager.getById[Mission](missionId, Some(em))
          missionOpt.get.addDevice(sensorInDb.get)
          assert(missionOpt.isDefined, {println("[Message.InsertWindLog] Mission is not in Database !")})
          missionOpt.get.save(Some(em))
          val wl = new WindLog(sensorInDb.get, ts, windValue, missionOpt.get)
          val persisted = wl.save(Some(em)) // persist in DB
          if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
            Some(true)
          } else None
        } else Some(false)
        BatchManager.updateBatchProgress(batchId, "Insertion")
        em.getTransaction().commit()
      } catch {
        case ae: AssertionError => None
      } finally {
        em.close()
      }
    }
    /*case Message.InsertGpsLog(batchId, ts, setNumber, sensor, latitude, longitude) => {
      try {
        //println("[Message.InsertGpsLog] "+sensor.address)
        val sensorInDb = Device.getByNameAndAddress(sensor.name, sensor.address)
        assert(sensorInDb.isDefined, {println("[Message.InsertGpsLog] Sensor is not in Database !")})
        // ALTERNATIVE - create unique string, use timestamp format with seconds (and not milli) --> import only one GPS point per second
        val uniqueString = createUniqueString(sensor.address, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        val res = if (!logCache.contains(uniqueString)) {
          val (lat, lon) = if (math.abs(latitude) > 90 || math.abs(longitude) > 180) {
            // east coordinate comes as longitude var, north coordinate comes as latitude var
            val arr = ApproxSwissProj.LV03toWGS84(longitude, latitude, 0L).toList
            //val latitude = arr(0)
            //val longitude = arr(1)
            (arr(0), arr(1))
          } else {
            (latitude, longitude)
          }
          //println("[RCV message] - insert gps log: "+ longitude +":"+ latitude +", "+ sensorInDb.get)
          val geom = CoordinateHelper.wktToGeometry("POINT("+ lon +" "+ lat +")")
          val speed = computeSpeed(ts, geom, setNumber)
          val gl = new GpsLog()
          gl.setSensor(sensorInDb.get)
          gl.setTimestamp(ts)
          gl.setGeoPos(geom.asInstanceOf[Point])
          gl.setSpeed(speed)
          gl.setSetNumber(setNumber)
          val persisted = gl.save() // persist in DB
          if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
            Some(true)
          } else None
        } else Some(false)
        BatchManager.updateBatchProgress(batchId, "Insertion")
        //sender ! res
      } catch {
        case ae: AssertionError => None
      }
    }*/
    case Message.InsertRadiometerLog(batchId, missionId, ts, sensor, radiometerValue) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        em.getTransaction().begin()
        val sensorInDb = DeviceManager.getByNameAndAddress(sensor.getName, sensor.getAddress, Some(em))
        assert(sensorInDb.isDefined, {println("[Message.InsertRadiometerLog] Sensor is not in Database !")})
        //println("QUEUE -> "+logCache)
        val uniqueString = createUniqueString(sensor.getAddress, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        if (!logCache.contains(uniqueString)) {
          //if (sensorInDb.get.name.contains("Pyrgeometer") || sensorInDb.get.name.contains("Pyranometer")) {
            //println("[RCV message] - insert radiometer log: "+ radiometerValue)
          val missionOpt = DataLogManager.getById[Mission](missionId, Some(em))
          missionOpt.get.addDevice(sensorInDb.get)
          assert(missionOpt.isDefined, {println("[Message.InsertRadiometerLog] Mission is not in Database !")})
          missionOpt.get.save(Some(em))
          val rl = new RadiometerLog(sensorInDb.get, ts, radiometerValue, missionOpt.get)
          val persisted = rl.save(Some(em)) // persist in DB
          if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
            Some(true)
          } else None
          /*} else {
            //println("[RCV message] - insert temperature log: "+ radiometerValue)
            if (sensorInDb.get.datatype != "temperature") {
              sensorInDb.get.updateType("temperature") // update the type of the sensor (the PT100 of the radiometer)
            }
            val tl = new TemperatureLog()
            tl.setDevice(sensorInDb.get)
            tl.setTimestamp(ts)
            tl.setValue(radiometerValue)
            val persisted = tl.save() // persist in DB
            if (persisted) {
              logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
              Some(true)
            } else None
          }*/
        }
        BatchManager.updateBatchProgress(batchId, "Insertion")
        em.getTransaction().commit()
      } catch {
        case ae: AssertionError => sender ! None
      } finally {
        em.close()
      }
    }
    case Message.InsertGpsLog(batchId, missionId, ts, setNumber, latitude, longitude, altitude) => {
      try {
        //println("[Message.InsertGpsLog] "+sensor.address)
        //val sensorInDb = DeviceManager.getByNameAndAddress(sensor.getName, sensor.getAddress)
        //assert(sensorInDb.isDefined, {println("[Message.InsertGpsLog] Sensor is not in Database !")})
        //val uniqueString = createUniqueString(sensor.getAddress, DateFormatHelper.postgresTimestampWithMilliFormatter.format(ts))
        //if (!logCache.contains(uniqueString)) {
        val (lat, lon, alt) = if (math.abs(latitude) > 90 || math.abs(longitude) > 180) {
          // east coordinate comes as longitude var, north coordinate comes as latitude var
          val arr = ApproxSwissProj.LV03toWGS84(longitude, latitude, altitude).toList
          //val latitude = arr(0)
          //val longitude = arr(1)
          (arr(0), arr(1), arr(2))
        } else {
          (latitude, longitude, altitude)
        }
        //println("[RCV message] - insert gps log: "+ longitude +":"+ latitude +", "+ sensorInDb.get)
        val geom = CoordinateHelper.wktToGeometry("POINT("+ lon +" "+ lat +" "+ alt +")")
        val pt = geom.asInstanceOf[Point]
        val speed = computeSpeed(ts, pt, setNumber)
        //Logger.info("Insert point: "+pt.getCoordinate.x+", "+pt.getCoordinate.y+", "+pt.getCoordinate.z)
        val gl = new TrajectoryPoint()
        gl.setTimestamp(ts)
        gl.setCoordinate(pt)
        gl.setSpeed(speed)
        val m = DataLogManager.getById[Mission](missionId)
        gl.setMission(m.get)
        gl.save(None) // persist in DB
        BatchManager.updateBatchProgress(batchId, "Insertion")
      } catch {
        case ae: AssertionError => None
      }
    }
    case Message.InsertPointOfInterest(batchId, missionId, ts, latitude, longitude, altitude) => {
      try {
        //println("[Message.InsertPointOfInterest] ")
        val (lat, lon, alt) = if (math.abs(latitude) > 90 || math.abs(longitude) > 180) {
          // east coordinate comes as longitude var, north coordinate comes as latitude var
          val arr = ApproxSwissProj.LV03toWGS84(longitude, latitude, altitude).toList
          //val latitude = arr(0)
          //val longitude = arr(1)
          (arr(0), arr(1), arr(2))
        } else {
          (latitude, longitude, altitude)
        }
        //println("[RCV message] - insert gps log: "+ longitude +":"+ latitude +", "+ sensorInDb.get)
        val geom = CoordinateHelper.wktToGeometry("POINT("+ lon +" "+ lat +" "+ alt +")")
        val poi = new PointOfInterest()
        poi.setTimestamp(ts)
        poi.setCoordinate(geom.asInstanceOf[Point])
        //poi.setAltitude(altitude)
        val m = DataLogManager.getById[Mission](missionId)
        poi.setMission(m.get)
        val persisted = poi.save() // persist in DB
        BatchManager.updateBatchProgress(batchId, "Insertion")
      } catch {
        case ae: AssertionError => None
      }
    }
    case Message.InsertUlmTrajectory(batchId, missionId, ts, timeZone, latitude, longitude, altitude) => {
      try {
        //println("[Message.InsertPointOfInterest] ")
        //println("[RCV message] - insert gps log: "+ longitude +":"+ latitude +", "+ sensorInDb.get)
        // create unique string with second precision so that only one point is inserted per second
        //val defaultTz = TimeZone.getDefault
        //TimeZone.setDefault(timeZone)
        val uniqueString = createUniqueString("ulmTs", DateFormatHelper.postgresTimestampFormatter.format(ts))
        if (!logCache.contains(uniqueString)) {
          val geom = CoordinateHelper.wktToGeometry("POINT("+ longitude +" "+ latitude +" "+ altitude +")")
          val pt = new TrajectoryPoint()
          pt.setTimestamp(ts)
          pt.setCoordinate(geom.asInstanceOf[Point])
          val m = DataLogManager.getById[Mission](missionId)
          pt.setMission(m.get)
          val persisted = pt.save(None) // persist in DB
          if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
          }
        }
        BatchManager.updateBatchProgress(batchId, "Insertion")
        //TimeZone.setDefault(defaultTz)

      } catch {
        case ae: AssertionError => None
      }
    }
    case Message.InsertDevice(device) => {
      //println("[RCV message] - insert sensor: "+sensor)
      sender ! device.save
    }
    case Message.SkipLog(batchId) => {
      val batchNumbers = batchProgress.get(batchId)
      if (batchNumbers.isDefined) {
        batchProgress(batchId) = (batchNumbers.get._1, batchNumbers.get._2, batchNumbers.get._3 + 1)
        if (batchNumbers.get._2 == batchNumbers.get._3 + 1) println("Import batch ["+ batchId +"]: 100%")
      }
    }
  }

  /**
   * Compute the instantaneous speed in one GPS point
   * @param ts The timestamp of the GPS point
   * @param point The GPS point
   * @param setNumber The set number of the GPS point
   * @return The speed in m/s
   */
  private def computeSpeed(ts: Date, point: Point, setNumber: Int): Double = {
    val INTERVAL_BETWEEN_SPEED_POINTS = 1000L // milliseconds (it means that we compute speed every 1000 ms)
    val timeDiff = lastSpeedPoint.map(gpsLog => ts.getTime - gpsLog.getTimestamp.getTime) // time difference (ms) between gps points
    //val lastSetNumber = lastSpeedPoint.map(gpsLog => gpsLog.getSetNumber)
    //if (lastSetNumber.isDefined && lastSetNumber.get != setNumber) lastSpeedPoint = None // reset lastSpeedPoint if set number has changed
    //val point = geom.asInstanceOf[Point]
    if (lastSpeedPoint.isEmpty) {
      val speed = 0.0
      val gl = new TrajectoryPoint()
      gl.setTimestamp(ts)
      gl.setCoordinate(point)
      gl.setSpeed(speed)
      //gl.setSetNumber(setNumber)
      lastSpeedPoint = Some(gl)
      speed
    } else if (lastSpeedPoint.isDefined && timeDiff.get >= INTERVAL_BETWEEN_SPEED_POINTS) {
      // speed needs to be recomputed for this point
      val distance = computeDistanceBetween2GpsPoints(lastSpeedPoint.get.getCoordinate.getX, lastSpeedPoint.get.getCoordinate.getY, point.getX, point.getY)
      val speed = distance / (timeDiff.get.toDouble / 1000.0) // return m/s
      //Logger.info("TimeDiff: "+ timeDiff +" ms, Distance: "+distance+" m, speed: "+speed+" m/s")
      val gl = new TrajectoryPoint()
      gl.setTimestamp(ts)
      gl.setCoordinate(point)
      gl.setSpeed(speed)
      //gl.setSetNumber(setNumber)
      lastSpeedPoint = Some(gl)
      speed
    } else {
      // return the speed that was computed in the last speed point
      lastSpeedPoint.map(_.getSpeed).get
    }
  }

  /**
   * Compute the distance between 2 GPS points (in meters)
   * @param lon1 Longitude of 1st point
   * @param lat1 Latitude of 1st point
   * @param lon2 Longitude of 2nd point
   * @param lat2 Latitude of 2nd point
   * @return The distance in meters
   */
  private def computeDistanceBetween2GpsPoints(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double = {
    val R = 6371 // km
    val dLat = math.toRadians(lat2 - lat1)
    val dLon = math.toRadians(lon2 - lon1)
    var lat1InRad = math.toRadians(lat1)
    var lat2InRad = math.toRadians(lat2)

    val a = math.sin(dLat/2) * math.sin(dLat/2) + math.sin(dLon/2) * math.sin(dLon/2) * math.cos(lat1InRad) * math.cos(lat2InRad)
    val c = 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))
    val distKm = R * c
    distKm * 1000 // return meters
  }
}