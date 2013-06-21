package models.spatial;

import com.google.gson.*;
import com.vividsolutions.jts.geom.Point;
import controllers.util.DateFormatHelper;
import controllers.util.JPAUtil;
import controllers.util.SensorLog;
import controllers.util.WebSerializable;
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
public class RadiometerLog implements WebSerializable, SensorLog {

    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    private Long id;

    @Column(name="sensor_id")
    private Long sensor_id;

    private Date timestamp;

    private Integer value;

    @Column(name="geo_pos")
    @Type(type="org.hibernate.spatial.GeometryType")
    private Point geoPos;

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
