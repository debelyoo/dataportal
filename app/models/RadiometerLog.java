package models;

import com.google.gson.*;
import controllers.util.*;
import controllers.util.DateFormatHelper;
import controllers.util.json.JsonSerializable;
import models.Device;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "radiometerlog", uniqueConstraints = @UniqueConstraint(columnNames = {"device_id", "timestamp"})) // uniqueness constraint
public class RadiometerLog implements JsonSerializable, SensorLog {

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

    public RadiometerLog() {
    }

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
        return "[RadiometerLog] id: "+ this.id +", device: "+this.device +", TS: "+this.timestamp +", " +
                "value: "+this.value +", mission: "+ this.mission;
    }

    @Override
    public String toJson() {
        return new GsonBuilder().registerTypeAdapter(RadiometerLog.class, new RadiometerLogSerializer()).create().toJson(this);
    }

    /**
     * Custom Serializer for Radiometer log
     */
    public static class RadiometerLogSerializer implements JsonSerializer<RadiometerLog> {
        @Override
        public JsonElement serialize(RadiometerLog radiometerLog, java.lang.reflect.Type type, JsonSerializationContext context) {
            JsonElement logJson = new JsonObject();
            logJson.getAsJsonObject().addProperty("id", radiometerLog.getId());
            logJson.getAsJsonObject().addProperty("device_id", radiometerLog.getDevice().id());
            logJson.getAsJsonObject().addProperty("mission_id", radiometerLog.getMission().getId());
            logJson.getAsJsonObject().addProperty("timestamp", DateFormatHelper.postgresTimestampWithMilliFormatter().format(radiometerLog.getTimestamp()));
            logJson.getAsJsonObject().addProperty("value", radiometerLog.getValue());
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