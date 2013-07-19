package models.spatial;

import com.google.gson.*;
import com.vividsolutions.jts.geom.Point;
import controllers.util.*;
import controllers.util.json.JsonSerializable;
import models.Sensor;
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
public class RadiometerLog implements WebSerializable, SensorLog {

    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    private Long id;

    @OneToOne
    @JoinColumn(name="sensor_id")
    private Sensor sensor;

    private Date timestamp;

    private Integer value;

    @OneToOne
    @JoinColumn(name="gps_log_id")
    private GpsLog gpsLog;

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

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer v) {
        this.value = v;
    }

    public GpsLog getGpsLog() {
        return this.gpsLog;
    }

    public void setGpsLog(GpsLog gLog) {
        this.gpsLog = gLog;
    }

    public String toString() {
        return id + " -> ts: " + timestamp + ", value: " + value;
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
            logJson.getAsJsonObject().addProperty("sensor_id", radiometerLog.getSensor().id());
            logJson.getAsJsonObject().addProperty("timestamp", DateFormatHelper.postgresTimestampWithMilliFormatter().format(radiometerLog.getTimestamp()));
            logJson.getAsJsonObject().addProperty("value", radiometerLog.getValue());
            if(radiometerLog.getGpsLog() != null) {
                double[] arr = ApproxSwissProj.WGS84toLV03(radiometerLog.getGpsLog().getGeoPos().getY(), radiometerLog.getGpsLog().getGeoPos().getX(), 0L); // get east, north, height
                logJson.getAsJsonObject().addProperty("coordinate_swiss", arr[0] +","+ arr[1]);
                JsonObject point = new JsonObject();
                point.addProperty("x", radiometerLog.getGpsLog().getGeoPos().getX());
                point.addProperty("y", radiometerLog.getGpsLog().getGeoPos().getY());
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
