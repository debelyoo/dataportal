package models.spatial

import javax.persistence._
import controllers.util.json.GeoJsonSerializable
import java.lang.{String, Long}
import org.hibernate.annotations.{Type, GenericGenerator}
import java.util.{TimeZone, Date}
import models.Mission
import com.vividsolutions.jts.geom.Point
import com.google.gson._
import controllers.util.{JPAUtil, ApproxSwissProj, DateFormatHelper}
import java.text.SimpleDateFormat

/**
 * A class that represents a trajectory point in DB
 * @param ts The timestamp fo the trajectory point
 * @param coord The coordinate of the trajectory point
 * @param m The mission the point is linked to
 * @param sp The speed of the vehicle at this point
 * @param h The heading of the vehicle at this point (as Option)
 */
@Entity
@Table (name = "trajectorypoint", uniqueConstraints = Array(new UniqueConstraint(columnNames = Array("timestamp", "mission_id")))) // add unique constraint to avoid adding twice the same point when exporting
class TrajectoryPoint(ts: Date, coord: Point, m: Mission, sp: Double, h: Option[Double]) extends GeoJsonSerializable {

  /**
   * The id of the point of interest in DB
   */
  @Id
  @GeneratedValue(generator = "increment")
  @GenericGenerator(name = "increment", strategy = "increment")
  var id: Long = null

  /**
   * The timestamp of the point of interest
   */
  var timestamp: Date = ts

  /**
   * The coordinate of the point of interest
   */
  @Column(name = "coordinate")
  @Type(`type` = "org.hibernate.spatial.GeometryType")
  var coordinate: Point = coord

  /**
   *
   */
  @ManyToOne // cascading constraint is set on a OneToMany relationship, not a ManyToOne
  @JoinColumn(name = "mission_id")
  var mission: Mission = m

  var speed: Double = sp

  var heading: Double = h.getOrElse(0.0)

  override def toString = "[TrajectoryPoint] id: " + id + ", mission: "+ mission.id +", TS: " + timestamp

  def this() = this(null, null, null, 0.0, None) // default constructor - necessary to work with hibernate (otherwise not possible to do select queries)

  def this(ts: Date, pt: Point, sp: Double) = this(ts, pt, null, sp, None) // constructor for speed computation

  override def toGeoJson: String = {
    return new GsonBuilder().registerTypeAdapter(classOf[TrajectoryPoint], new TrajectoryPointGeoJsonSerializer).create.toJson(this)
  }

  /**
   * Custom Geo JSON Serializer for GPS log
   */
  class TrajectoryPointGeoJsonSerializer extends JsonSerializer[TrajectoryPoint] {
    def serialize(point: TrajectoryPoint, `type`: java.lang.reflect.Type, context: JsonSerializationContext): JsonElement = {
      val gson: Gson = new Gson
      val geometryObj: JsonObject = new JsonObject
      geometryObj.addProperty("type", "Point")
      val str: String = "[" + point.coordinate.getCoordinate.x + "," + point.coordinate.getCoordinate.y + "," + point.coordinate.getCoordinate.z + "]"
      val jArr: JsonArray = gson.fromJson(str, classOf[JsonArray])
      geometryObj.add("coordinates", jArr)
      val propertiesObj: JsonObject = new JsonObject
      propertiesObj.addProperty("id", point.id)
      val formatter: SimpleDateFormat = DateFormatHelper.postgresTimestampWithMilliFormatter
      formatter.setTimeZone(TimeZone.getTimeZone(point.mission.timeZone))
      propertiesObj.addProperty("timestamp", formatter.format(point.timestamp))
      propertiesObj.addProperty("speed", point.speed)
      propertiesObj.addProperty("heading", point.heading)
      if (point.coordinate != null) {
        val arr: Array[Double] = ApproxSwissProj.WGS84toLV03(point.coordinate.getCoordinate.y, point.coordinate.getCoordinate.x, point.coordinate.getCoordinate.z)
        propertiesObj.addProperty("coordinate_swiss", arr(0) + "," + arr(1) + "," + arr(2))
      }
      val featureObj: JsonObject = new JsonObject
      featureObj.addProperty("type", "Feature")
      featureObj.add("geometry", geometryObj)
      featureObj.add("properties", propertiesObj)
      return featureObj
    }
  }

  /**
   * Persist trajectory point in DB
   * @param emOpt An optional EntityManger
   * @return true if success
   */
  def save(emOpt: Option[EntityManager] = None): Boolean = {
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
