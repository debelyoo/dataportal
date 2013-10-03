package models.spatial;

import com.google.gson.*;
import com.vividsolutions.jts.geom.Point;
import controllers.util.ApproxSwissProj;
import controllers.util.DateFormatHelper;
import controllers.util.JPAUtil;
import controllers.util.json.GeoJsonSerializable;
import models.Mission;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "pointofinterest")
public class PointOfInterest implements GeoJsonSerializable {

    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    private Long id;

    private Date timestamp;

    @Column(name="coordinate")
    @Type(type="org.hibernate.spatial.GeometryType")
    private Point coordinate;

    @ManyToOne
    @JoinColumn(name="mission_id")
    private Mission mission;

    public PointOfInterest() {
    }

    public Long getId() {
        return id;
    }

    private void setId(Long id) {
        this.id = id;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date ts) {
        this.timestamp = ts;
    }

    public Point getCoordinate() {
        return this.coordinate;
    }

    public void setCoordinate(Point pos) {
        this.coordinate = pos;
    }

    public Mission getMission() {
        return this.mission;
    }

    public void setMission(Mission m) {
        this.mission = m;
    }

    public String toString() {
        return id + " -> ts: " + timestamp + ", point: " + coordinate.toString();
    }

    @Override
    public String toGeoJson() {
        return new GsonBuilder().registerTypeAdapter(PointOfInterest.class, new PointOfInterestGeoJsonSerializer()).create().toJson(this);
    }

    /**
     * Custom Geo JSON Serializer for GPS log
     */
    public static class PointOfInterestGeoJsonSerializer implements JsonSerializer<PointOfInterest> {
        @Override
        public JsonElement serialize(PointOfInterest point, java.lang.reflect.Type type, JsonSerializationContext context) {
            Gson gson = new Gson();
            JsonObject geometryObj = new JsonObject();
            geometryObj.addProperty("type", "Point");
            String str = "["+point.coordinate.getCoordinate().x +","+point.coordinate.getCoordinate().y+","+ point.coordinate.getCoordinate().z +"]";
            JsonArray jArr = gson.fromJson(str, JsonArray.class);
            geometryObj.add("coordinates", jArr);

            JsonObject propertiesObj = new JsonObject();
            propertiesObj.addProperty("id", point.getId());
            propertiesObj.addProperty("timestamp", DateFormatHelper.postgresTimestampWithMilliFormatter().format(point.getTimestamp()));
            if (point.getCoordinate() != null) {
                double[] arr = ApproxSwissProj.WGS84toLV03(point.coordinate.getCoordinate().y, point.coordinate.getCoordinate().x,point.coordinate.getCoordinate().z);
                propertiesObj.addProperty("coordinate_swiss", arr[0] + "," + arr[1]+","+arr[2]);
                //propertiesObj.addProperty("speed", gpsLog.getSpeed());
            }

            JsonObject featureObj = new JsonObject();
            featureObj.addProperty("type", "Feature");
            featureObj.add("geometry", geometryObj);
            featureObj.add("properties", propertiesObj);
            return featureObj;
        }
    }



    /**
     * Save the GpsLog in Postgres database
     */
    public Boolean save() {
        EntityManager em = JPAUtil.createEntityManager();
        Boolean res = false;
        try {
            em.getTransaction().begin();
            em.persist(this);
            em.getTransaction().commit();
            res = true;
        } catch (Exception ex) {
            System.out.println("[WARNING] "+ ex.getMessage());
        } finally {
            em.close();
        }
        return res;
    }
}
