package models;

import com.google.gson.*;
import controllers.util.*;
import controllers.util.DateFormatHelper;
import controllers.util.json.JsonSerializable;
import org.hibernate.annotations.GenericGenerator;
import scala.Option;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "compasslog", uniqueConstraints = @UniqueConstraint(columnNames = {"device_id", "timestamp"}))
public class CompassLog implements JsonSerializable, SensorLog {

    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    private Long id;

    @OneToOne
    @JoinColumn(name="device_id")
    private Device device;

    private Date timestamp;

    private Double value;

    @OneToOne
    @JoinColumn(name="mission_id")
    private Mission mission;

    public CompassLog(Device dev, Date ts, Double val, Mission m) {
        this.device = dev;
        this.timestamp = ts;
        this.value = val;
        this.mission = m;
    }

    public CompassLog() {}

    @Override
    public Long getId() {
        return id;
    }

    private void setId(Long id) {
        this.id = id;
    }

    @Override
    public Device getDevice() {
        return device;
    }

    public void setDevice(Device s) {
        this.device = s;
    }

    @Override
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

    public Mission getMission() {
        return this.mission;
    }

    public void setMission(Mission m) {
        this.mission = m;
    }

    public String toString() {
        return "[CompassLog] id: "+ this.id +", device: "+this.device +", TS: "+this.timestamp +", " +
                "value: "+this.value +", mission: "+ this.mission;
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
            logJson.getAsJsonObject().addProperty("device_id", compassLog.getDevice().getId());
            logJson.getAsJsonObject().addProperty("mission_id", compassLog.getMission().getId());
            logJson.getAsJsonObject().addProperty("timestamp", DateFormatHelper.postgresTimestampWithMilliFormatter().format(compassLog.getTimestamp()));
            logJson.getAsJsonObject().addProperty("value", compassLog.getValue());
            return logJson;
        }
    }

    /**
     * Save the CompassLog in Postgres database
     */
    public Boolean save(Option<EntityManager> emOpt) {
        EntityManager em;
        if (emOpt.isEmpty()) {
            em = JPAUtil.createEntityManager();
        } else {
            em = emOpt.get();
        }
        Boolean res = false;
        try {
            if (emOpt.isEmpty()) em.getTransaction().begin();
            em.persist(this);
            if (emOpt.isEmpty()) em.getTransaction().commit();
            res = true;
        } catch (Exception ex) {
            // no need to rollback, hibernate does it automatically in case of error
            System.out.println("[WARNING] "+ ex.getMessage());
        } finally {
            if (emOpt.isEmpty()) em.close();
        }
        return res;
    }

}
