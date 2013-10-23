package models;

import com.google.gson.*;
import controllers.util.*;
import controllers.util.DateFormatHelper;
import controllers.util.json.JsonSerializable;
import org.hibernate.annotations.GenericGenerator;
import scala.None;
import scala.Option;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "temperaturelog", uniqueConstraints = @UniqueConstraint(columnNames = {"device_id", "timestamp"}))
public class TemperatureLog implements JsonSerializable, ISensorLog {

    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    private Long id;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name="device_id")
    private Device device;

    private Date timestamp;

    private Double value;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name="mission_id")
    private Mission mission;

    public TemperatureLog(Device dev, Date ts, Double val, Mission m) {
        this.device = dev;
        this.timestamp = ts;
        this.value = val;
        this.mission = m;
    }

    public TemperatureLog() {}

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

    public void setDevice(Device d) {
        this.device = d;
    }

    @Override
    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date ts) {
        this.timestamp = ts;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double v) {
        this.value = v;
    }

    public Mission getMission() {
        return this.mission;
    }

    public void setMission(Mission m) {
        this.mission = m;
    }

    @Override
    public String toString() {
        return "[TemperatureLog] id: "+ this.id +", device: "+this.device +", TS: "+this.timestamp +", " +
                "value: "+this.value +", mission: "+ this.mission;
    }

    @Override
    public String toJson() {
        //System.out.println(this.toString());
        return new GsonBuilder().registerTypeAdapter(TemperatureLog.class, new TemperatureLogSerializer()).create().toJson(this);
    }

    /**
     * Custom Serializer for Temperature log
     */
    public static class TemperatureLogSerializer implements JsonSerializer<TemperatureLog> {
        @Override
        public JsonElement serialize(TemperatureLog temperatureLog, java.lang.reflect.Type type, JsonSerializationContext context) {
            JsonElement logJson = new JsonObject();
            logJson.getAsJsonObject().addProperty("id", temperatureLog.getId());
            logJson.getAsJsonObject().addProperty("device_id", temperatureLog.getDevice().id());
            logJson.getAsJsonObject().addProperty("mission_id", temperatureLog.getMission().id());
            logJson.getAsJsonObject().addProperty("timestamp", DateFormatHelper.postgresTimestampWithMilliFormatter().format(temperatureLog.getTimestamp()));
            logJson.getAsJsonObject().addProperty("value", temperatureLog.getValue());
            return logJson;
        }
    }

    /**
     * Save the TemperatureLog in Postgres database
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
