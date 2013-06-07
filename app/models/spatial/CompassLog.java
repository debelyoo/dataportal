package models.spatial;

import com.google.gson.*;
import com.vividsolutions.jts.geom.Point;
import controllers.util.DateFormatHelper;
import controllers.util.JPAUtil;
import controllers.util.json.JsonSerializable;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "compasslog", uniqueConstraints = @UniqueConstraint(columnNames = {"sensor_id", "timestamp"}))
public class CompassLog implements JsonSerializable {

    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    private Long id;

    @Column(name="sensor_id")
    private Long sensorId;

    private Date timestamp;

    private Double value;

    @Column(name="geo_pos")
    @Type(type="org.hibernate.spatial.GeometryType")
    private Point geoPos;

    public CompassLog() {
    }

    public Long getId() {
        return id;
    }

    private void setId(Long id) {
        this.id = id;
    }

    public Long getSensorId() {
        return sensorId;
    }

    public void setSensorId(Long sId) {
        this.sensorId = sId;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date ts) {
        this.timestamp = ts;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double v) {
        this.value = v;
    }

    public Point getGeoPos() {
        return this.geoPos;
    }

    public void setGeoPos(Point pos) {
        this.geoPos = pos;
    }

    public String toString() {
        return id + " -> ts: " + timestamp + ", value: " + value;
    }

    @Override
    public String toJson() {
        return new GsonBuilder().registerTypeAdapter(CompassLog.class, new CompassLogSerializer()).create().toJson(this);
    }

    /**
     * Custom Serializer for Compass log
     */
    public static class CompassLogSerializer implements JsonSerializer<CompassLog> {
        @Override
        public JsonElement serialize(CompassLog compassLog, java.lang.reflect.Type type, JsonSerializationContext context) {
            JsonElement logJson = new JsonObject();
            logJson.getAsJsonObject().addProperty("id", compassLog.getId());
            logJson.getAsJsonObject().addProperty("sensor_id", compassLog.getSensorId());
            logJson.getAsJsonObject().addProperty("timestamp", DateFormatHelper.postgresTimestampWithMilliFormatter().format(compassLog.getTimestamp()));
            logJson.getAsJsonObject().addProperty("value", compassLog.getValue());
            if(compassLog.getGeoPos() != null) {
                JsonObject point = new JsonObject();
                point.addProperty("x", compassLog.getGeoPos().getX());
                point.addProperty("y", compassLog.getGeoPos().getY());
                logJson.getAsJsonObject().add("geo_pos", point);
            }
            return logJson;
        }
    }

    /**
     * Save the CompassLog in Postgres database
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
