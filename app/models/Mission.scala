package models

import javax.persistence._
import org.hibernate.annotations.{Type, GenericGenerator, Cascade}
import java.util.Date
import controllers.util.json.JsonSerializable
import com.vividsolutions.jts.geom.LineString
import com.google.gson._
import controllers.util.{JPAUtil, DateFormatHelper}
import java.util
import scala.collection.JavaConversions._
import scala.collection.mutable
import models.spatial.TrajectoryPoint

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

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "mission", cascade=Array(CascadeType.ALL))
  var sensorLogs: util.Collection[SensorLog] = new util.HashSet[SensorLog]()

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "mission", cascade=Array(CascadeType.ALL))
  var trajectoryPoints: util.Collection[TrajectoryPoint] = new util.HashSet[TrajectoryPoint]()

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
      missionJson.getAsJsonObject.addProperty("timezone", mission.timeZone)
      missionJson.getAsJsonObject.addProperty("vehicle", mission.vehicle.name)
      return missionJson
    }
  }

  /**
   * Save the Mission in Postgres database
   * @param emOpt An optional entity mnager
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
}
