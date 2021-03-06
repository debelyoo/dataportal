package models

import javax.persistence._
import org.hibernate.annotations.GenericGenerator
import java.util.Date
import controllers.util.{DateFormatHelper, JPAUtil}
import controllers.util.json.JsonSerializable
import java.lang.String
import com.google.gson._
import java.lang.reflect.Type

/**
 * A class that represents a data log
 * @param m The mission this log belongs to
 * @param d The device this log comes from
 * @param ts The timestamp of the log
 * @param v The value of the log
 */
@Entity
@Table(name = "sensorlog", uniqueConstraints = Array(new UniqueConstraint(columnNames = Array("timestamp", "device_id", "mission_id")))) // add unique constraint to avoid adding twice the same log when exporting
class SensorLog(m: Mission, d: Device, ts: Date, v: Double) extends JsonSerializable {
  /**
   * The id of the log in DB
   */
  @Id
  @GeneratedValue(generator="increment")
  @GenericGenerator(name="increment", strategy = "increment")
  @Column(name = "id", unique = true, nullable = false)
  var id: Long = _

  /**
   * The mission this log belongs to (ManyToOne)
   */
  @ManyToOne // cascading constraint is set on a OneToMany relationship, not a ManyToOne
  @JoinColumn(name="mission_id")
  var mission: Mission = m

  /**
   * The device this log comes from (ManyToOne)
   */
  @ManyToOne
  @JoinColumn(name="device_id")
  var device: Device = d

  /**
   * The timestamp of the log
   */
  var timestamp: Date = ts

  /**
   * The value of the log (Double)
   */
  var value: Double = v

  override def toString = "[SensorLog] id: " + id + ", mission: "+ mission.id +", device: "+ device.name +", TS: " + timestamp + " value: "+value

  def this() = this(null, null, null, 0.0) // default constructor - necessary to work with hibernate (otherwise not possible to do select queries)

  /**
   * Format the data log as JSON
   * @return A JSON representation of the data log
   */
  override def toJson: String = {
    return new GsonBuilder().registerTypeAdapter(classOf[SensorLog], new SensorLogSerializer).create.toJson(this)
  }

  /**
   * Custom Serializer for Sensor log
   */
  class SensorLogSerializer extends JsonSerializer[SensorLog] {
    def serialize(sensorLog: SensorLog, `type`: Type, context: JsonSerializationContext): JsonElement = {
      val logJson: JsonElement = new JsonObject
      logJson.getAsJsonObject.addProperty("id", sensorLog.id)
      logJson.getAsJsonObject.addProperty("device_id", sensorLog.device.id)
      logJson.getAsJsonObject.addProperty("mission_id", sensorLog.mission.id)
      logJson.getAsJsonObject.addProperty("timestamp", DateFormatHelper.postgresTimestampWithMilliFormatter.format(sensorLog.timestamp))
      logJson.getAsJsonObject.addProperty("value", sensorLog.value)
      return logJson
    }
  }

  /**
   * Persist device type in DB (if it is not already in)
   * @param emOpt An optional EntityManger
   * @return true if success
   */
  def save(emOpt: Option[EntityManager] = None): Boolean = {
    //println("[SensorLog] save() - "+ this.toString)
    val em = emOpt.getOrElse(JPAUtil.createEntityManager)
    try {
      if(emOpt.isEmpty) em.getTransaction.begin
      em.persist(this)
      if(emOpt.isEmpty) em.getTransaction.commit
      true
    } catch {
      case ex: Exception => {
        ex.printStackTrace()
        false
      }
    } finally {
      if (emOpt.isEmpty) em.close
    }
  }
}

object SensorLog {

}
