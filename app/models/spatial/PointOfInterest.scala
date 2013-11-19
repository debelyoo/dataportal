package models.spatial

import javax.persistence._
import controllers.util.json.GeoJsonSerializable
import java.util.Date
import com.vividsolutions.jts.geom.Point
import models.Mission
import org.hibernate.annotations.GenericGenerator
import java.lang.String
import com.google.gson._
import controllers.util.{JPAUtil, ApproxSwissProj, DateFormatHelper}
import java.lang.reflect.Type

/**
 * A class that represents a Point of Interest
 * @param ts The timestamp of the point of interest
 * @param coord The coordinate of the point of interest
 * @param m The mission the POI belongs to
 */
@Entity
@Table(name = "pointofinterest")
class PointOfInterest(ts: Date, coord: Point, m: Mission) extends GeoJsonSerializable {
  /**
   * The id of the point of interest in DB
   */
  @Id
  @GeneratedValue(generator="increment")
  @GenericGenerator(name="increment", strategy = "increment")
  @Column(name = "id", unique = true, nullable = false)
  var id: Long = _

  /**
   * The timestamp of the point of interest
   */
  var timestamp: Date = ts

  /**
   * The coordinate of the point of interest
   */
  var coordinate: Point = coord

  /**
   * The mission this point of interest belongs to (ManyToOne)
   */
  @ManyToOne // cascading constraint is set on a OneToMany relationship, not a ManyToOne
  @JoinColumn(name="mission_id")
  var mission: Mission = m

  override def toString = "[Point of interest] id: " + id + ", mission: "+ mission.id +", TS: " + timestamp

  def this() = this(null, null, null) // default constructor - necessary to work with hibernate (otherwise not possible to do select queries)

  override def toGeoJson: String = {
    return new GsonBuilder().registerTypeAdapter(classOf[PointOfInterest], new PointOfInterestGeoJsonSerializer).create.toJson(this)
  }

  /**
   * Custom Geo JSON Serializer for GPS log
   */
  class PointOfInterestGeoJsonSerializer extends JsonSerializer[PointOfInterest] {
    def serialize(point: PointOfInterest, `type`: Type, context: JsonSerializationContext): JsonElement = {
      val gson: Gson = new Gson
      val geometryObj: JsonObject = new JsonObject
      geometryObj.addProperty("type", "Point")
      val str: String = "[" + point.coordinate.getCoordinate.x + "," + point.coordinate.getCoordinate.y + "," + point.coordinate.getCoordinate.z + "]"
      val jArr: JsonArray = gson.fromJson(str, classOf[JsonArray])
      geometryObj.add("coordinates", jArr)
      val propertiesObj: JsonObject = new JsonObject
      propertiesObj.addProperty("id", point.id)
      propertiesObj.addProperty("timestamp", DateFormatHelper.postgresTimestampWithMilliFormatter.format(point.timestamp))
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
   * Persist point of interest in DB
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
