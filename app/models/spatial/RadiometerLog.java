package models.spatial;

import com.google.gson.*;
import com.vividsolutions.jts.geom.Point;
import controllers.util.DateFormatHelper;
import controllers.util.JPAUtil;
import controllers.util.json.JsonSerializable;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.engine.jdbc.spi.SqlExceptionHelper;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.GenericJDBCException;

import javax.persistence.*;
import java.sql.SQLException;
import java.util.Date;

@Entity
@Table(name = "radiometerlog", uniqueConstraints = @UniqueConstraint(columnNames = {"sensor_id", "timestamp"})) // uniqueness constraint
public class RadiometerLog implements JsonSerializable {

    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    private Long id;

    @Column(name="sensor_id")
    private Long sensorId;

    private Date timestamp;

    private Integer value;

    @Column(name="geo_pos")
    @Type(type="org.hibernate.spatial.GeometryType")
    private Point geoPos;

    public RadiometerLog() {
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

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer v) {
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
        return new GsonBuilder().registerTypeAdapter(CompassLog.class, new RadiometerLogSerializer()).create().toJson(this);
    }

    /**
     * Custom Serializer for Compass log
     */
    public static class RadiometerLogSerializer implements JsonSerializer<RadiometerLog> {
        @Override
        public JsonElement serialize(RadiometerLog radiometerLog, java.lang.reflect.Type type, JsonSerializationContext context) {
            JsonElement logJson = new JsonObject();
            logJson.getAsJsonObject().addProperty("id", radiometerLog.getId());
            logJson.getAsJsonObject().addProperty("sensor_id", radiometerLog.getSensorId());
            logJson.getAsJsonObject().addProperty("timestamp", DateFormatHelper.postgresTimestampWithMilliFormatter().format(radiometerLog.getTimestamp()));
            logJson.getAsJsonObject().addProperty("value", radiometerLog.getValue());
            if(radiometerLog.getGeoPos() != null) {
                JsonObject point = new JsonObject();
                point.addProperty("x", radiometerLog.getGeoPos().getX());
                point.addProperty("y", radiometerLog.getGeoPos().getY());
                logJson.getAsJsonObject().add("geo_pos", point);
            }
            return logJson;
        }
    }

    /**
     * Save the RadiometerLog in Postgres database
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
