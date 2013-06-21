package models.spatial;

import com.google.gson.*;
import com.vividsolutions.jts.geom.Point;
import controllers.util.DateFormatHelper;
import controllers.util.JPAUtil;
import controllers.util.SensorLog;
import controllers.util.WebSerializable;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Date;

@Entity
@Table(name = "temperaturelog", uniqueConstraints = @UniqueConstraint(columnNames = {"sensor_id", "timestamp"}))
//@XmlRootElement
//@XmlAccessorType(XmlAccessType.FIELD)
public class TemperatureLog implements WebSerializable, SensorLog {

    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    //@XmlElement(name="id")
    private Long id;

    @Column(name="sensor_id")
    //@XmlElement(name="sensor_id")
    private Long sensor_id;

    //@XmlElement(name="timestamp")
    private Date timestamp;

    //@XmlElement(name="value")
    private Double value;

    @Column(name="geo_pos")
    @Type(type="org.hibernate.spatial.GeometryType")
    private Point geoPos;

    public TemperatureLog() {
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
        return sensor_id;
    }

    public void setSensorId(Long sId) {
        this.sensor_id = sId;
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

    public Point getGeoPos() {
        return this.geoPos;
    }

    public void setGeoPos(Point pos) {
        this.geoPos = pos;
    }

    @Override
    public String toString() {
        String gpsStr = "";
        if (this.geoPos != null) {
            gpsStr = "GPS: ("+ this.geoPos.getX() + ","+ this.geoPos.getY() +")";
        } else {
            gpsStr = "GPS: NULL";
        }
        return "[TemperatureLog] id: "+ this.id +", sensor_id: "+this.sensor_id +", TS: "+this.timestamp +", " +
                "value: "+this.value +", "+gpsStr;
    }

    @Override
    public String toJson() {
        //System.out.println(this.toString());
        return new GsonBuilder().registerTypeAdapter(TemperatureLog.class, new TemperatureLogSerializer()).create().toJson(this);
    }

    @Override
    public String toKml() {
        String kmlStr = "";
        kmlStr += "<Placemark>";
        kmlStr += "<name>temperaturelog"+ this.id +"</name>";
        kmlStr += "<description>The temperature measured in one point</description>";
        if (this.geoPos != null) {
            kmlStr += "<Point>";
            kmlStr += "<coordinates>"+ this.geoPos.getX()+","+ this.geoPos.getY() +"</coordinates>";
            kmlStr += "</Point>";
        }
        kmlStr += "</Placemark>";
        return kmlStr;
    }

    @Override
    public String toGml() {
        String gmlStr = "";
        gmlStr += "<gml:featureMember>";
        gmlStr += "<ecol:restRequest fid=\"temperaturelog"+ this.id +"\">";
        gmlStr += "<ecol:id>"+ this.id +"</ecol:id>";
        gmlStr += "<ecol:sensor_id>"+ this.sensor_id +"</ecol:sensor_id>";
        gmlStr += "<ecol:timestamp>"+ this.timestamp.toString() +"</ecol:timestamp>";
        gmlStr += "<ecol:value>"+ this.value +"</ecol:value>";
        if (this.geoPos != null) {
            gmlStr += "<ecol:geo_pos>";
            gmlStr += "<gml:Point srsName=\"http://www.opengis.net/gml/srs/epsg.xml#4326\">";
            gmlStr += "<gml:coordinates xmlns:gml=\"http://www.opengis.net/gml\" decimal=\".\" cs=\",\" ts=\" \">"+ this.geoPos.getX() +","+ this.geoPos.getY() +"</gml:coordinates>";
            gmlStr += "</gml:Point>";
            gmlStr += "</ecol:geo_pos>";
        }
        gmlStr += "</ecol:restRequest>";
        gmlStr += "</gml:featureMember>";
        return gmlStr;
    }

    /**
     * Custom Serializer for Temperature log
     */
    public static class TemperatureLogSerializer implements JsonSerializer<TemperatureLog> {
        @Override
        public JsonElement serialize(TemperatureLog temperatureLog, java.lang.reflect.Type type, JsonSerializationContext context) {
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
