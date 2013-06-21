package models.spatial;

import com.google.gson.*;
import com.vividsolutions.jts.geom.Point;
import controllers.util.DateFormatHelper;
import controllers.util.JPAUtil;
import controllers.util.SensorLog;
import controllers.util.json.JsonSerializable;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "gpslog")
public class GpsLog implements JsonSerializable, SensorLog {

    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    private Long id;

    @Column(name="sensor_id")
    private Long sensorId;

    private Date timestamp;

    @Column(name="geo_pos")
    @Type(type="org.hibernate.spatial.GeometryType")
    private Point geoPos;

    public GpsLog() {
    }

    @Override
    public Long getId() {
        return id;
    }

    private void setId(Long id) {
        this.id = id;
    }

    @Override
    public Long getSensorId() {
        return sensorId;
    }

    public void setSensorId(Long sId) {
        this.sensorId = sId;
    }

    @Override
    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date ts) {
        this.timestamp = ts;
    }

    public Point getGeoPos() {
        return this.geoPos;
    }

    public void setGeoPos(Point pos) {
        this.geoPos = pos;
    }

    public String toString() {
        return id + " -> ts: " + timestamp + ", point: " + geoPos.toString();
    }

    @Override
    public String toJson() {
        return new GsonBuilder().registerTypeAdapter(GpsLog.class, new GpsLogSerializer()).create().toJson(this);
    }

    /**
     * Custom Serializer for GPS log
     */
    public static class GpsLogSerializer implements JsonSerializer<GpsLog> {
        @Override
        public JsonElement serialize(GpsLog gpsLog, java.lang.reflect.Type type, JsonSerializationContext context) {
            //JsonElement gpsLogJson = new Gson().toJsonTree(gpsLog);
            JsonElement gpsLogJson = new JsonObject();
            gpsLogJson.getAsJsonObject().addProperty("id", gpsLog.getId());
            gpsLogJson.getAsJsonObject().addProperty("sensor_id", gpsLog.getSensorId());
            gpsLogJson.getAsJsonObject().addProperty("timestamp", DateFormatHelper.postgresTimestampWithMilliFormatter().format(gpsLog.getTimestamp()));
            JsonObject point = new JsonObject();
            point.addProperty("x", gpsLog.getGeoPos().getX());
            point.addProperty("y", gpsLog.getGeoPos().getY());
            gpsLogJson.getAsJsonObject().add("geo_pos", point);
            return gpsLogJson;
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