package models

import javax.persistence._
import org.hibernate.annotations.{Type, GenericGenerator, Cascade}
import java.util.{TimeZone, Calendar, Date}
import controllers.util.json.JsonSerializable
import com.vividsolutions.jts.geom.LineString
import com.google.gson._
import controllers.util.{JPAUtil, DateFormatHelper}
import java.util
import scala.collection.JavaConversions._
import scala.collection.mutable
import models.spatial.{PointOfInterest, TrajectoryPoint}
import controllers.modelmanager.DataLogManager
import play.api.libs.json.{Json, JsValue}
import models.internal.OneDoubleValueLog
import play.api.Logger

@Entity
@Table(name = "mission")
class Mission(depTime: Date, tz: String, v: Vehicle) extends JsonSerializable {
  @Id
  @GeneratedValue(generator="increment")
  @GenericGenerator(name="increment", strategy = "increment")
  @Column(name = "id", unique = true, nullable = false)
  var id: Long = _

  var departureTime: Date = depTime
  var timeZone: String = tz

  @ManyToOne
  @JoinColumn(name = "vehicle_id")
  var vehicle: Vehicle = v

  @Column(name = "trajectory")
  @Type(`type` = "org.hibernate.spatial.GeometryType")
  var trajectory: LineString = null

  @ManyToMany(fetch = FetchType.EAGER,targetEntity = classOf[Device])
  @JoinTable(name = "equipment",
    joinColumns = Array(new JoinColumn(name = "mission_id", referencedColumnName = "id")),
    inverseJoinColumns = Array(new JoinColumn(name = "device_id", referencedColumnName = "id")))
  var devices: util.Collection[Device] = new util.HashSet[Device]()

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "mission", cascade=Array(CascadeType.ALL)) // cascading constraint is set on a OneToMany relationship, not a ManyToOne
  var sensorLogs: util.Collection[SensorLog] = new util.HashSet[SensorLog]()

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "mission", cascade=Array(CascadeType.ALL)) // cascading constraint is set on a OneToMany relationship, not a ManyToOne
  var trajectoryPoints: util.Collection[TrajectoryPoint] = new util.HashSet[TrajectoryPoint]()

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "mission", cascade=Array(CascadeType.ALL)) // cascading constraint is set on a OneToMany relationship, not a ManyToOne
  var pointsOfInterest: util.Collection[PointOfInterest] = new util.HashSet[PointOfInterest]()

  def this() = this(null, "", null) // default constructor - necessary to work with hibernate (otherwise not possible to do select queries)

  def addDevice(dev: Device) {
    if (!this.devices.contains(dev)) {
      devices.add(dev)
    }
  }

  override def toString = "[Mission] id:" +id + ", depTime: "+ departureTime + ", timezone: "+timeZone+", vehicle: "+vehicle.name

  @Override
  def toJson: String = {
    return new GsonBuilder().registerTypeAdapter(classOf[Mission], new MissionSerializer).create.toJson(this)
  }

  /**
   * Custom JSON Serializer
   */
  class MissionSerializer extends JsonSerializer[Mission] {
    @Override
    def serialize(mission: Mission, `type`: java.lang.reflect.Type, context: JsonSerializationContext): JsonElement = {
      val missionJson: JsonElement = new JsonObject
      missionJson.getAsJsonObject.addProperty("id", mission.id)
      missionJson.getAsJsonObject.addProperty("date", DateFormatHelper.selectYearFormatter.format(mission.departureTime))
      missionJson.getAsJsonObject.addProperty("time", DateFormatHelper.selectHourMinFormatter.format(mission.departureTime))
      missionJson.getAsJsonObject.addProperty("timezone", mission.timeZone)
      missionJson.getAsJsonObject.addProperty("vehicle", mission.vehicle.name)
      return missionJson
    }
  }

  /**
   * Save the Mission in Postgres database
   * @param emOpt An optional entity manager
   * @return true if success
   */
  def save(emOpt: Option[EntityManager] = None): Boolean = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction.begin
      em.persist(this)
      if (emOpt.isEmpty) em.getTransaction.commit
      true
    }
    catch {
      case ex: Exception => {
        System.out.println("[ERROR][Mission.save()] " + ex.getMessage)
        false
      }
    }
    finally {
      if (emOpt.isEmpty) em.close
    }
  }

  /**
   * Delete the Mission from Postgres database
   * @param emOpt An optional entity mnager
   * @return true if success
   */
  def delete(emOpt: Option[EntityManager] = None): Boolean = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction.begin
      em.remove(this)
      if (emOpt.isEmpty) em.getTransaction.commit
      true
    }
    catch {
      case ex: Exception => {
        System.out.println("[ERROR][Mission.delete()] " + ex.getMessage)
        false
      }
    }
    finally {
      if (emOpt.isEmpty) em.close
    }
  }
}

object Mission {
  /**
   * Get all missions in DB
   * @return A list of missions
   */
  def getAll(): List[Mission] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val q = em.createQuery("FROM "+ classOf[Mission].getName + " ORDER BY departuretime DESC", classOf[Mission])
      val missions = q.getResultList.toList
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
   * Get the missions for a specific date
   * @param date The date of the missions to get
   * @return A list of missions
   */
  def getByDate(date: Date): List[Mission] = {
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
   * Get a mission by datetime
   * @param date The date of the mission
   * @param emOpt An optional Entity Manager
   * @return The mission as an Option
   */
  def getByDatetime(date: Date, emOpt: Option[EntityManager] = None): Option[Mission] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[Mission].getName +" where departuretime = :depTime")
      q.setParameter("depTime", date, TemporalType.TIMESTAMP)
      val mission = q.getSingleResult.asInstanceOf[Mission]
      if (emOpt.isEmpty) em.getTransaction().commit()
      Some(mission)
    } catch {
      case nre: NoResultException => None
      case ex: Exception => ex.printStackTrace; None
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  /**
   * Get the trajectory (as linestring) of a mission
   * @param missionId The mission id
   * @return The trajectory in GeoJSON object
   */
  def getTrajectoryLinestring(missionId: Long): JsValue = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val nativeQuery = "SELECT ST_AsGeoJSON(trajectory) FROM mission WHERE id = "+missionId
      val q = em.createNativeQuery(nativeQuery)
      val res = q.getSingleResult.asInstanceOf[String]
      em.getTransaction().commit()
      if (res != null) {
        Json.parse(res)
      } else {
        Json.parse("{\"error\": \"no trajectory for mission: "+ missionId +"\"}")
      }
    } catch {
      case nre: NoResultException => Json.parse("{\"error\": \"no result\"}")
      case ex: Exception => ex.printStackTrace; Json.parse("{\"error\": \""+ ex.getMessage +"\"}")
    } finally {
      em.close()
    }
  }

  /**
   * Set the linestring geometry object for a mission
   * @param missionId The id of a mission
   * @return true if success
   */
  def setTrajectoryLinestring(missionId: Long): Boolean = {
    val em: EntityManager = JPAUtil.createEntityManager
    try {
      em.getTransaction.begin()
      val points = getTrajectoryPoints(missionId, None, None, None, Some(em))
      val pointsAsString = points.map(tp => tp.getCoordinate.getX + " " + tp.getCoordinate.getY) // coordinates are separated by space
      val linestring = "LINESTRING("+ pointsAsString.mkString(",") +")"
      val q = em.createNativeQuery("UPDATE mission SET trajectory = '"+ linestring +"' WHERE id = "+missionId)
      val nbUpdated = q.executeUpdate()
      em.getTransaction.commit()
      nbUpdated == 1
    } catch {
      case ex: Exception => ex.printStackTrace(); false
    } finally {
      em.close()
    }
  }

  /**
   * Get the trajectory points of a mission
   * @param missionId The id of the mission
   * @param maxNb The maximum n b of points to get
   * @param emOpt An optional entity manager
   * @return A list of trajectory points
   */
  def getTrajectoryPoints(missionId: Long,
                          startTime: Option[Date],
                          endTime: Option[Date],
                          maxNb: Option[Int],
                          emOpt: Option[EntityManager] = None): List[TrajectoryPoint] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val timeCondition = if (startTime.isDefined && endTime.isDefined) " AND timestamp BETWEEN :start AND :end " else ""
      val queryStr = "FROM "+ classOf[TrajectoryPoint].getName +" tp WHERE tp.mission.id = "+ missionId + timeCondition +"ORDER BY timestamp"
      //println(queryStr)
      val q = em.createQuery(queryStr, classOf[TrajectoryPoint])
      if (startTime.isDefined && endTime.isDefined) {
        //println("Time condition: "+ startTime.get + " - "+ endTime.get)
        q.setParameter("start", startTime.get, TemporalType.TIMESTAMP)
        q.setParameter("end", endTime.get, TemporalType.TIMESTAMP)
      }
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
   * Get the altitude data for a mission
   * @param missionId The is of the mission
   * @param maxNb The maximum number of points to get
   * @param startTime
   * @param endTime
   * @return A map with the altitude logs
   */
  def getAltitude(missionId: Long, startTime: Option[Date], endTime: Option[Date], maxNb: Option[Int]): Map[Device, List[JsonSerializable]] = {
    val tz = DataLogManager.getById[Mission](missionId).map(m => TimeZone.getTimeZone(m.timeZone)).getOrElse(TimeZone.getDefault)
    val dateFormatter = DateFormatHelper.postgresTimestampWithMilliFormatter
    dateFormatter.setTimeZone(tz) // format TS with mission timezone
    val pointList = getTrajectoryPoints(missionId, startTime, endTime, maxNb)
    val altitudeLogList = pointList.map(trajPt =>
      OneDoubleValueLog(trajPt.getId,
        dateFormatter.format(trajPt.getTimestamp),
        trajPt.getCoordinate.getCoordinate.z
      )
    )
    val virtualDev = new Device("altitude", "", DeviceType("", "m", DeviceType.PlotTypes.LINE))
    Map(virtualDev -> altitudeLogList)
  }

  /**
   * Get the speed data for a mission
   * @param missionId The id of the mission
   * @param startTime
   * @param endTime
   * @param maxNb The maximum number of points to get
   * @return A map with the speed logs
   */
  def getSpeed(missionId: Long, startTime: Option[Date], endTime: Option[Date], maxNb: Option[Int]): Map[Device, List[JsonSerializable]] = {
    val pointList = getTrajectoryPoints(missionId, startTime, endTime, maxNb)
    val speedLogList = pointList.map(trajPt =>
      OneDoubleValueLog(trajPt.getId,
        DateFormatHelper.postgresTimestampWithMilliFormatter.format(trajPt.getTimestamp),
        trajPt.getSpeed)
    )
    val virtualDev = new Device("speed", "", DeviceType("", "m/s", DeviceType.PlotTypes.LINE))
    Map(virtualDev -> speedLogList)
  }

  /**
   * Get the heading data for a mission
   * @param missionId The id of the mission
   * @param startTime
   * @param endTime
   * @param maxNb The maximum number of points to get
   * @return A map with the heading logs
   */
  def getHeading(missionId: Long, startTime: Option[Date], endTime: Option[Date], maxNb: Option[Int]): Map[Device, List[JsonSerializable]] = {
    val pointList = getTrajectoryPoints(missionId, startTime, endTime, maxNb)
    val headingLogList = pointList.map(trajPt =>
      OneDoubleValueLog(trajPt.getId,
        DateFormatHelper.postgresTimestampWithMilliFormatter.format(trajPt.getTimestamp),
        trajPt.getHeading)
    )
    val virtualDev = new Device("heading", "", DeviceType("", "degree", DeviceType.PlotTypes.LINE))
    Map(virtualDev -> headingLogList)
  }

  /**
   * Get the points of interest of a mission
   * @param missionId The id of the mission
   * @param emOpt An optional entity manager
   * @return A list of points of interest
   */
  def getPointOfInterest(missionId: Long, emOpt: Option[EntityManager] = None): List[PointOfInterest] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val queryStr = "FROM "+ classOf[PointOfInterest].getName +" poi WHERE poi.mission.id = "+ missionId +" ORDER BY timestamp"
      //println(queryStr)
      val q = em.createQuery(queryStr, classOf[PointOfInterest])
      //println(q.getResultList)
      val pointList = q.getResultList.toList
      if (emOpt.isEmpty) em.getTransaction().commit()
      pointList
    } catch {
      case nre: NoResultException => println("No result !!"); List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  /**
   * Get the raster data for a specific mission
   * @param missionId The id of the mission
   * @return the raster data (JSON)
   */
  def getRasterData(missionId: Long): JsValue = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val nativeQuery = "SELECT imagename, ST_X(leftuppercorner) AS xlu, ST_Y(leftuppercorner) AS ylu, ST_X(leftlowercorner) AS xll, ST_Y(leftlowercorner) AS yll," +
        " ST_X(rightuppercorner) AS xru, ST_Y(rightuppercorner) AS yru, ST_X(rightlowercorner) AS xrl, ST_Y(rightlowercorner) AS yrl," +
        " device.id, device.name, device.address FROM rasterdata, device" +
        " WHERE mission_id = "+missionId+" AND rasterdata.device_id = device.id"
      val q = em.createNativeQuery(nativeQuery)
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
        //val dev = new Device(resultArray(9).asInstanceOf[Int],resultArray(10).asInstanceOf[String],resultArray(11).asInstanceOf[String],resultArray(12).asInstanceOf[String])
        //Logger.info("device id: "+resultArray(9).asInstanceOf[Int])
        val dev = Device.getById(List(resultArray(9).asInstanceOf[Int]), Some(em)).head._2
        val gson: Gson = new Gson
        dataJson.getAsJsonObject.add("device", gson.fromJson(dev.toJson, classOf[JsonObject]))
        jsArr.add(dataJson)
      })

      em.getTransaction().commit()
      Json.toJson(Json.parse(jsArr.toString))
    } catch {
      case nre: NoResultException => Json.parse("{\"error\": \"no result\"}")
      case ex: Exception => ex.printStackTrace; Json.parse("{\"error\": \"exception\"}")
    } finally {
      em.close()
    }
  }

  /**
   * Get the maximum speed for a specific mission
   * @param missionId The id of the mission
   * @return the max speed (JSON)
   */
  def getMaxSpeedAndHeading(missionId: Long): JsValue = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val query = "SELECT MAX(speed) AS speed, MAX(heading) AS heading FROM "+ classOf[TrajectoryPoint].getName +" WHERE mission_id = :missionId";
      val q = em.createQuery(query)
      q.setParameter("missionId", missionId)
      val res = q.getSingleResult.asInstanceOf[Array[Object]]
      em.getTransaction().commit()
      val headingAvailable = if (res(1).asInstanceOf[Double] > 0) true else false
      val dataJson = (new JsonObject).asInstanceOf[JsonElement]
      dataJson.getAsJsonObject.addProperty("max_speed", res(0).asInstanceOf[Double])
      dataJson.getAsJsonObject.addProperty("heading_available", headingAvailable)
      Json.toJson(Json.parse(dataJson.toString))
    } catch {
      case nre: NoResultException => Json.parse("{\"error\": \"no result\"}")
      case ex: Exception => ex.printStackTrace; Json.parse("{\"error\": \"exception\"}")
    } finally {
      em.close()
    }
  }

  /**
   * Create a mission (add it in DB)
   * @param depTime The datetime of the mission
   * @param timeZone The timezone of the mission
   * @param vehicleId The id of the vehicle of the mission
   * @return true if success
   */
  def create(depTime: Date, timeZone: String, vehicleId: Long): Boolean = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction.begin()
      val vehicle = DataLogManager.getById[Vehicle](vehicleId).get
      val mission = new Mission(depTime, timeZone, vehicle)
      val b = mission.save(Some(em))
      em.getTransaction.commit()
      b
    } catch {
      case ex: Exception => ex.printStackTrace(); false
    } finally {
      em.close()
    }
  }

  /**
   * Delete a mission from DB
   * @param missionId The id of the mission
   * @return true if success
   */
  def delete(missionId: Long): Boolean = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction.begin()
      val mission = DataLogManager.getById[Mission](missionId, Some(em)).get
      val b = mission.delete(Some(em))
      em.getTransaction.commit()
      b
    } catch {
      case ex: Exception => ex.printStackTrace(); false
    } finally {
      em.close()
    }
  }
}
