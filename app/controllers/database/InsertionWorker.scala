package controllers.database

import akka.actor.Actor
import controllers.util._
import models.spatial._
import models._

import com.vividsolutions.jts.geom.{Geometry, Point}
import scala.collection.immutable.Queue
import controllers.database.BatchManager._
import java.util.{TimeZone, Date}
import controllers.modelmanager.DataLogManager
import scala.Some
import javax.persistence.EntityManager
import play.api.libs.json.{Json, JsNumber, JsObject}

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
    case Message.SetInsertionBatchJson(batchId, dataType, nbOfItems) => {
      batchProgress(batchId) = (dataType, nbOfItems, 0)
    }
    case Message.InsertCompassLog(batchId, trajectoryPoints, ts, compassValue) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        em.getTransaction().begin()
        val ptOpt = DataLogManager.getClosestGpsLog(trajectoryPoints, ts, 1)
        for (pt <- ptOpt) {
          val p = DataLogManager.getById[TrajectoryPoint](pt.getId, Some(em)).get // need to reload the point in this entity manager to be persisted below
          p.setHeading(compassValue)
          p.save(Some(em))
        }
        BatchManager.updateBatchProgress(batchId, "Insertion")
        em.getTransaction().commit()
      } catch {
        case ex: Exception => ex.printStackTrace();None
      } finally {
        em.close()
      }
    }
    case Message.InsertGpsLog(batchId, missionId, ts, latitude, longitude, altitude, headingOpt) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        em.getTransaction.begin()
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
        val speed = computeSpeed(ts, pt)
        //Logger.info("Insert point: "+pt.getCoordinate.x+", "+pt.getCoordinate.y+", "+pt.getCoordinate.z)
        val gl = new TrajectoryPoint()
        gl.setTimestamp(ts)
        gl.setCoordinate(pt)
        gl.setSpeed(speed)
        headingOpt.foreach(heading => gl.setHeading(heading))
        val m = DataLogManager.getById[Mission](missionId, Some(em))
        gl.setMission(m.get)
        gl.save(Some(em)) // persist in DB
        BatchManager.updateBatchProgress(batchId, "Insertion")
        em.getTransaction.commit()
      } catch {
        case ae: AssertionError => None
      } finally {
        em.close()
      }
    }
    case Message.InsertPointOfInterest(batchId, missionId, ts, latitude, longitude, altitude) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        em.getTransaction.begin()
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
        val missionOpt = DataLogManager.getById[Mission](missionId, Some(em))
        poi.setMission(missionOpt.get)
        val persisted = poi.save(Some(em)) // persist in DB
        BatchManager.updateBatchProgress(batchId, "Insertion")
        em.getTransaction.commit()
      } catch {
        case ae: AssertionError => None
      } finally {
        em.close()
      }
    }
    case Message.InsertUlmTrajectory(batchId, missionId, ts, latitude, longitude, altitude) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        em.getTransaction.begin()
        //println("[RCV message] - insert gps log: "+ longitude +":"+ latitude +", "+ sensorInDb.get)
        // create unique string with second precision so that only one point is inserted per second
        val uniqueString = createUniqueString("ulmTs", DateFormatHelper.postgresTimestampFormatter.format(ts))
        if (!logCache.contains(uniqueString)) {
          val geom = CoordinateHelper.wktToGeometry("POINT("+ longitude +" "+ latitude +" "+ altitude +")")
          val pt = new TrajectoryPoint()
          pt.setTimestamp(ts)
          pt.setCoordinate(geom.asInstanceOf[Point])
          val m = DataLogManager.getById[Mission](missionId, Some(em))
          pt.setMission(m.get)
          val persisted = pt.save(Some(em)) // persist in DB
          if (persisted) {
            logCache = logCache.enqueueFinite(uniqueString, LOG_CACHE_MAX_SIZE)
          }
        }
        BatchManager.updateBatchProgress(batchId, "Insertion")
        em.getTransaction.commit()
      } catch {
        case ae: AssertionError => None
      } finally {
        em.close()
      }
    }
    case Message.InsertDevice(device, missionId) => {
      //println("[RCV message] - insert device: "+device)
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        em.getTransaction.begin()
        val deviceOpt = Device.getByNameAndAddress(device.name, device.address, Some(em))
        val dev = if (deviceOpt.isEmpty) {
          device.save(true, Some(em))
          Device.getByNameAndAddress(device.name, device.address, Some(em)).get
        } else {
          // device already in DB
          deviceOpt.get
        }
        // link mission with device
        val missionOpt = DataLogManager.getById[Mission](missionId, Some(em))
        for (mission <- missionOpt) {
          mission.addDevice(dev)
          mission.save(Some(em))
        }
        em.getTransaction.commit()
      } catch {
        case ex: Exception => ex.printStackTrace()
      } finally {
        em.close()
      }
    }
    case Message.InsertMission(departureTime, timezone, vehicleName, devices) => {
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        em.getTransaction.begin()
        val vehicle = Vehicle.getByName(vehicleName, Some(em)).getOrElse(new Vehicle(vehicleName))
        val missionOpt = Mission.getByDatetime(departureTime, Some(em))
        val mission = if (missionOpt.isEmpty) {
          (new Mission(departureTime, timezone, vehicle)).save(Some(em))
          Mission.getByDatetime(departureTime, Some(em)).get
        } else {
          // mission already in DB
          missionOpt.get
        }
        //println(devices)
        devices.value.foreach(jsDev => {
          val address = (jsDev \ "address").as[String]
          val name = (jsDev \ "name").as[String]
          val datatype = (jsDev \ "datatype").as[String]
          val deviceTypeOpt = DeviceType.getByName(datatype, Some(em))
          val deviceType = deviceTypeOpt.getOrElse(new DeviceType(datatype))
          val deviceOpt = Device.getByNameAndAddress(name, address, Some(em))
          val device = if (deviceOpt.isEmpty) {
            val dev = new Device(name, address, deviceType)
            dev.save(false, Some(em))
            dev
          } else {
            // device already in DB
            deviceOpt.get
          }
          mission.addDevice(device)
        })
        mission.save(Some(em))
        em.getTransaction.commit()
        sender ! Json.obj("mission_id" -> BigDecimal(mission.id))
      } catch {
        case ex: Exception => ex.printStackTrace()
      } finally {
        em.close()
      }
    }
    case Message.InsertSensorLog(batchId, missionId, timestamp, value, deviceAddress) => {
      //println("[InsertSensorLog]")
      val em: EntityManager = JPAUtil.createEntityManager
      try {
        em.getTransaction.begin()
        val mission = DataLogManager.getById[Mission](missionId, Some(em))
        assert(mission.isDefined, {println("[InsertSensorLog] Mission does not exist")})
        val devices = Device.getForMission(missionId, None, Some(deviceAddress), Some(em))
        assert(devices.length == 1, {println("[InsertSensorLog] Device does not exist")})
        // create sensor log record
        val log = new SensorLog(mission.get, devices.head, timestamp, value)
        log.save(Some(em))
        BatchManager.updateBatchProgress(batchId, "Insertion")
        em.getTransaction.commit()
      } catch {
        case ae: AssertionError => ae.printStackTrace()
        case ex: Exception => ex.printStackTrace()
      } finally {
        em.close()
      }
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
   * @return The speed in m/s
   */
  private def computeSpeed(ts: Date, point: Point): Double = {
    val INTERVAL_BETWEEN_SPEED_POINTS = 1000L // milliseconds (it means that we compute speed every 1000 ms)
    val timeDiff = lastSpeedPoint.map(gpsLog => ts.getTime - gpsLog.getTimestamp.getTime) // time difference (ms) between gps points
    //val point = geom.asInstanceOf[Point]
    if (lastSpeedPoint.isEmpty) {
      val speed = 0.0
      val gl = new TrajectoryPoint()
      gl.setTimestamp(ts)
      gl.setCoordinate(point)
      gl.setSpeed(speed)
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