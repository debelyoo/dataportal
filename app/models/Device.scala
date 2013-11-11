package models

import javax.persistence._
import org.hibernate.annotations.GenericGenerator
import controllers.util.json.JsonSerializable
import com.google.gson._
import java.lang.reflect.Type
import controllers.util.{DataImporter, JPAUtil}
import java.util
import scala.collection.JavaConversions._

@Entity
@Table(name = "device")
class Device(n: String, addr: String, dt: DeviceType) extends JsonSerializable {
  @Id
  @GeneratedValue(generator="increment")
  @GenericGenerator(name="increment", strategy = "increment")
  @Column(name = "id", unique = true, nullable = false)
  var id: Long = _

  var name: String = n

  var address: String = addr

  @ManyToOne
  @JoinColumn(name="devicetype_id")
  var deviceType: DeviceType = dt

  @ManyToMany(mappedBy = "devices",targetEntity = classOf[Mission])
  var missions: util.Collection[Mission] = new util.HashSet[Mission]()

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "device",cascade=Array(CascadeType.ALL))
  var sensorLogs: util.Collection[SensorLog] = new util.HashSet[SensorLog]()

  /**
   * Add a mission to the missions list related to the device
   * @param mission The mission to link to the device
   */
  def addMission(mission: Mission) {
    if (!this.missions.contains(mission)) {
      missions.add(mission)
    }
  }

  override def toString = "[Device] id: "+id + " -> name: "+ name +", address: "+ address +", type: " + deviceType.name

  def this() = this("", "", null) // default constructor - necessary to work with hibernate (otherwise not possible to do select queries)

  def toJson: String = {
    return new GsonBuilder().registerTypeAdapter(classOf[Device], new DeviceSerializer).create.toJson(this)
  }

  /**
   * Custom JSON Serializer for Device
   */
  class DeviceSerializer extends JsonSerializer[Device] {
    def serialize(device: Device, `type`: Type, context: JsonSerializationContext): JsonElement = {
      val missionJson: JsonElement = new JsonObject
      missionJson.getAsJsonObject.addProperty("id", device.id)
      missionJson.getAsJsonObject.addProperty("name", device.name)
      missionJson.getAsJsonObject.addProperty("address", device.address)
      val deviceTypeName = if(device.deviceType != null) device.deviceType.name else ""
      val plotType = if(device.deviceType != null) device.deviceType.plotType else ""
      missionJson.getAsJsonObject.addProperty("device_type", deviceTypeName)
      missionJson.getAsJsonObject.addProperty("plot_type", plotType)
      return missionJson
    }
  }

  /**
   * Save the Device in Postgres database
   */
  def save(withDetachedDeviceType: Boolean = false, emOpt: Option[EntityManager] = None): Boolean = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    var res: Boolean = false
    try {
      if (emOpt.isEmpty) em.getTransaction.begin
      if (withDetachedDeviceType) {
        em.merge(this) // need to merge instead of persist because deviceType is already in DB
      } else {
        em.persist(this)
      }
      res = true
      if (emOpt.isEmpty) em.getTransaction.commit
      res
    } catch {
      case ex: Exception => {
        System.out.println("[ERROR][Device.save] " + ex.getMessage)
        false
      }
    } finally {
      if (emOpt.isEmpty) em.close
    }
  }
}

object Device {
  /**
   * Get a sensor by Id
   * @param sIdList A list of sensor Ids
   * @return The sensor
   */
  def getById(sIdList: List[Long], emOpt: Option[EntityManager]): Map[Long, Device] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[Device].getName +" WHERE id IN ("+ sIdList.mkString(",") +")", classOf[Device])
      val devices = q.getResultList.map(s => (s.id.toLong, s)).toMap
      if (emOpt.isEmpty) em.getTransaction().commit()
      devices
    } catch {
      case nre: NoResultException => Map()
      case ex: Exception => ex.printStackTrace; Map()
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  /**
   * Get a device by name and address
   * @param name The device name
   * @param address The device address
   * @param emOpt An optional entity manager to avoid created many transactions for multiple consecutive queries
   * @return The device
   */
  def getByNameAndAddress(name: String, address: String, emOpt: Option[EntityManager] = None): Option[Device] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      val q = em.createQuery("from "+ classOf[Device].getName +" where name = '"+ name +"' and address = '"+ address +"'")
      val device = q.getSingleResult.asInstanceOf[Device]
      if (emOpt.isEmpty) em.getTransaction().commit()
      Some(device)
    } catch {
      case nre: NoResultException => None
      case ex: Exception => ex.printStackTrace; None
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }

  /**
   * Get the devices that are associated with a mission
   * @param missionId The id of the mission
   * @param datatype The type of data to get
   * @param address The address of the device
   * @param emOpt An optional entity manager to avoid created many transactions for multiple consecutive queries
   * @return A list of devices
   */
  def getForMission(missionId: Long, datatype: Option[String], address: Option[String], emOpt: Option[EntityManager] = None): List[Device] = {
    val em = emOpt.getOrElse(JPAUtil.createEntityManager())
    val typeMap = collection.mutable.Map[DeviceType, Int]()
    try {
      if (emOpt.isEmpty) em.getTransaction().begin()
      // many to many query
      val typeCondition = if (datatype.isDefined) " AND d.deviceType.name = '"+ datatype.get +"'" else ""
      val addressCondition = if (address.isDefined) " AND d.address = '"+ address.get +"'" else ""
      val query = "SELECT DISTINCT d FROM "+ classOf[Device].getName +" d JOIN d.missions m WHERE m.id = "+ missionId + typeCondition + addressCondition + " ORDER BY d.name"
      //println("[Q] "+query)
      val q = em.createQuery(query, classOf[Device])
      val devices = q.getResultList.toList.map(d => {
        if (!typeMap.contains(d.deviceType)) {
          typeMap += d.deviceType -> 1
        } else {
          typeMap(d.deviceType) = (typeMap(d.deviceType) + 1)
        }
        d
      })
      // add a virtual sensor that will appear as "All temperature" in map interface
      val virtualDeviceList = for {
        t <- typeMap
        if (t._2 > 1)
      } yield {
        new Device(t._1.name, "", DeviceType(t._1.name, t._1.unit, DeviceType.PlotTypes.LINE))
      }
      if (emOpt.isEmpty) em.getTransaction().commit()
      devices ++ virtualDeviceList
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    } finally {
      if (emOpt.isEmpty) em.close()
    }
  }
}
