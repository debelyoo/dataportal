package models.spatial;

import com.google.gson.*;
import com.vividsolutions.jts.geom.Point;
import controllers.util.*;
import controllers.util.json.JsonSerializable;
import models.Sensor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "windlog", uniqueConstraints = @UniqueConstraint(columnNames = {"sensor_id", "timestamp"}))
public class WindLog implements WebSerializable, SensorLog {
    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    private Long id;

    @OneToOne
    @JoinColumn(name="sensor_id")
    private Sensor sensor;

    private Date timestamp;

    private Double value;

    @OneToOne
    @JoinColumn(name="gps_log_id")
    private GpsLog gpsLog;

    public WindLog() {
    }

    @Override
    public Long getId() {
        return id;
    }

    private void setId(Long id) {
        this.id = id;
    }

    @Override
    public Sensor getSensor() {
        return sensor;
    }

    public void setSensor(Sensor s) {
        this.sensor = s;
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

    public GpsLog getGpsLog() {
        return this.gpsLog;
    }

    public void setGpsLog(GpsLog gLog) {
        this.gpsLog = gLog;
    }

    @Override
    public String toJson() {
        return new GsonBuilder().registerTypeAdapter(WindLog.class, new WindLogSerializer()).create().toJson(this);
    }

    /**
     * Custom Serializer for Wind log
     */
    public static class WindLogSerializer implements JsonSerializer<WindLog> {
        @Override
        public JsonElement serialize(WindLog windLog, java.lang.reflect.Type type, JsonSerializationContext context) {
            JsonElement logJson = new JsonObject();
            logJson.getAsJsonObject().addProperty("id", windLog.getId());
            logJson.getAsJsonObject().addProperty("sensor_id", windLog.getSensor().id());
            logJson.getAsJsonObject().addProperty("timestamp", DateFormatHelper.postgresTimestampWithMilliFormatter().format(windLog.getTimestamp()));
            logJson.getAsJsonObject().addProperty("value", windLog.getValue());
            //System.out.println(windLog.getGeoPos().toString());
            if(windLog.getGpsLog() != null) {
                double[] arr = ApproxSwissProj.WGS84toLV03(windLog.getGpsLog().getGeoPos().getY(), windLog.getGpsLog().getGeoPos().getX(), 0L); // get east, north, height
                logJson.getAsJsonObject().addProperty("coordinate_swiss", arr[0] +","+ arr[1]);
                JsonObject point = new JsonObject();
                point.addProperty("x", windLog.getGpsLog().getGeoPos().getX());
                point.addProperty("y", windLog.getGpsLog().getGeoPos().getY());
                logJson.getAsJsonObject().add("geo_pos", point);
            }
            return logJson;
        }
    }

    @Override
    public String toKml() {
        String kmlStr = "";
        kmlStr += "<Placemark>";
        kmlStr += "<name>temperaturelog"+ this.id +"</name>";
        kmlStr += "<description>The temperature measured in one point</description>";
        if (this.gpsLog != null) {
            kmlStr += "<Point>";
            kmlStr += "<coordinates>"+ this.gpsLog.getGeoPos().getX()+","+ this.gpsLog.getGeoPos().getY() +"</coordinates>";
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
        gmlStr += "<ecol:sensor_id>"+ this.sensor.id() +"</ecol:sensor_id>";
        gmlStr += "<ecol:timestamp>"+ this.timestamp.toString() +"</ecol:timestamp>";
        gmlStr += "<ecol:value>"+ this.value +"</ecol:value>";
        if (this.gpsLog != null) {
            double[] arr = ApproxSwissProj.WGS84toLV03(this.gpsLog.getGeoPos().getY(), this.gpsLog.getGeoPos().getX(), 0L); // get east, north, height
            gmlStr += "<ecol:coordinate_swiss>"+ arr[0] +","+ arr[1] +"</ecol:coordinate_swiss>";
            gmlStr += "<ecol:geo_pos>";
            gmlStr += "<gml:Point srsName=\"http://www.opengis.net/gml/srs/epsg.xml#4326\">";
            gmlStr += "<gml:coordinates xmlns:gml=\"http://www.opengis.net/gml\" decimal=\".\" cs=\",\" ts=\" \">"+ this.gpsLog.getGeoPos().getX() +","+ this.gpsLog.getGeoPos().getY() +"</gml:coordinates>";
            gmlStr += "</gml:Point>";
            gmlStr += "</ecol:geo_pos>";
        }
        gmlStr += "</ecol:restRequest>";
        gmlStr += "</gml:featureMember>";
        return gmlStr;
    }

    /**
     * Save the WindLog in Postgres database
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
