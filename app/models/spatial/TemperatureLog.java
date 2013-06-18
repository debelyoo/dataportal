package models.spatial;

import com.google.gson.*;
import com.vividsolutions.jts.geom.Point;
import controllers.util.DateFormatHelper;
import controllers.util.JPAUtil;
import controllers.util.json.JsonSerializable;
import controllers.util.kml.KmlSerializable;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.w3c.dom.Document;

import javax.persistence.*;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "temperaturelog", uniqueConstraints = @UniqueConstraint(columnNames = {"sensor_id", "timestamp"}))
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class TemperatureLog implements JsonSerializable, KmlSerializable {

    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    @XmlElement(name="id")
    private Long id;

    @Column(name="sensor_id")
    @XmlElement(name="sensor_id")
    private Long sensorId;

    @XmlElement(name="timestamp")
    private Date timestamp;

    @XmlElement(name="value")
    private Double value;

    @Column(name="geo_pos")
    @Type(type="org.hibernate.spatial.GeometryType")
    private Point geoPos;

    public TemperatureLog() {
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

    @Override
    public String toString() {
        return "[TemperatureLog] id: "+ this.id +", sensor_id: "+this.sensorId +", TS: "+this.timestamp +", " +
                "value: "+this.value +", GPS: ("+ this.geoPos.getX() + ","+ this.geoPos.getY() +")";
    }

    @Override
    public String toJson() {
        return new GsonBuilder().registerTypeAdapter(CompassLog.class, new TemperatureLogSerializer()).create().toJson(this);
    }

    @Override
    public String toKml() {
        String kmlStr = "";
        kmlStr += "<Placemark>";
        kmlStr += "<name>temperaturelog"+ this.id +"</name>";
        kmlStr += "<description>The temperature measured in one point</description>";
        kmlStr += "<Point>";
        kmlStr += "<coordinates>"+ this.geoPos.getX()+","+ this.geoPos.getY() +"</coordinates>";
        kmlStr += "</Point>";
        kmlStr += "</Placemark>";
        return kmlStr;
    }

    /**
     * Custom Serializer for Temperature log
     */
    public static class TemperatureLogSerializer implements JsonSerializer<TemperatureLog> {
        @Override
        public JsonElement serialize(TemperatureLog temperatureLog, java.lang.reflect.Type type, JsonSerializationContext context) {
            System.out.println(this.toString());
            JsonElement logJson = new JsonObject();
            logJson.getAsJsonObject().addProperty("id", temperatureLog.getId());
            logJson.getAsJsonObject().addProperty("sensor_id", temperatureLog.getSensorId());
            logJson.getAsJsonObject().addProperty("timestamp", DateFormatHelper.postgresTimestampWithMilliFormatter().format(temperatureLog.getTimestamp()));
            logJson.getAsJsonObject().addProperty("value", temperatureLog.getValue());
            if(temperatureLog.getGeoPos() != null) {
                JsonObject point = new JsonObject();
                point.addProperty("x", temperatureLog.getGeoPos().getX());
                point.addProperty("y", temperatureLog.getGeoPos().getY());
                logJson.getAsJsonObject().add("geo_pos", point);
            }
            return logJson;
        }
    }

    /**
     * Save the TemperatureLog in Postgres database
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
            // no need to rollback, hibernate does it automatically in case of error
            System.out.println("[WARNING] "+ ex.getMessage());
        } finally {
            em.close();
        }
        return res;
    }
}
