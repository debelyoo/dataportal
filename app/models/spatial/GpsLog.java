package models.spatial;

import com.google.gson.*;
import com.vividsolutions.jts.geom.Point;
import controllers.util.*;
import controllers.util.json.GeoJsonSerializable;
import controllers.util.json.JsonSerializable;
import models.Device;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "gpslog")
public class GpsLog implements WebSerializable, GeoJsonSerializable, SensorLog {

    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    private Long id;

    @OneToOne
    @JoinColumn(name="device_id")
    private Device device;

    private Date timestamp;

    @Column(name="geo_pos")
    @Type(type="org.hibernate.spatial.GeometryType")
    private Point geoPos;

    @Column(name="speed")
    private Double speed;

    @Column(name="set_number")
    private int setNumber;

    public GpsLog() {
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

    public void setSensor(Device d) {
        this.device = d;
    }

    @Override
    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date ts) {
        this.timestamp = ts;
    }

    public Point getGeoPos() {
        return this.geoPos;
    }

    public void setGeoPos(Point pos) {
        this.geoPos = pos;
    }

    public Double getSpeed() {
        return this.speed;
    }

    public void setSpeed(Double sp) {
        this.speed = sp;
    }

    public void setSetNumber(int sn) {
        this.setNumber = sn;
    }

    public int getSetNumber() {
        return this.setNumber;
    }

    public String toString() {
        return id + " -> ts: " + timestamp + ", point: " + geoPos.toString();
    }

    @Override
    public String toJson() {
        return new GsonBuilder().registerTypeAdapter(GpsLog.class, new GpsLogSerializer()).create().toJson(this);
    }

    @Override
    public String toGeoJson() {
        return new GsonBuilder().registerTypeAdapter(GpsLog.class, new GpsLogGeoJsonSerializer()).create().toJson(this);
    }

    /**
     * Custom JSON Serializer for GPS log
     */
    public static class GpsLogSerializer implements JsonSerializer<GpsLog> {
        @Override
        public JsonElement serialize(GpsLog gpsLog, java.lang.reflect.Type type, JsonSerializationContext context) {
            JsonElement gpsLogJson = new JsonObject();
            gpsLogJson.getAsJsonObject().addProperty("id", gpsLog.getId());
            gpsLogJson.getAsJsonObject().addProperty("device_id", gpsLog.getDevice().id());
            gpsLogJson.getAsJsonObject().addProperty("timestamp", DateFormatHelper.postgresTimestampWithMilliFormatter().format(gpsLog.getTimestamp()));
            JsonObject point = new JsonObject();
            point.addProperty("x", gpsLog.getGeoPos().getX());
            point.addProperty("y", gpsLog.getGeoPos().getY());
            gpsLogJson.getAsJsonObject().add("geo_pos", point);
            return gpsLogJson;
        }
    }

    /**
     * Custom Geo JSON Serializer for GPS log
     */
    public static class GpsLogGeoJsonSerializer implements JsonSerializer<GpsLog> {
        @Override
        public JsonElement serialize(GpsLog gpsLog, java.lang.reflect.Type type, JsonSerializationContext context) {
            Gson gson = new Gson();
            JsonObject geometryObj = new JsonObject();
            geometryObj.addProperty("type", "Point");
            String str = "["+gpsLog.getGeoPos().getX() +","+gpsLog.getGeoPos().getY()+"]";
            JsonArray jArr = gson.fromJson(str, JsonArray.class);
            geometryObj.add("coordinates", jArr);

            JsonObject propertiesObj = new JsonObject();
            propertiesObj.addProperty("id", gpsLog.getId());
            propertiesObj.addProperty("device_id", gpsLog.getDevice().id());
            propertiesObj.addProperty("timestamp", DateFormatHelper.postgresTimestampWithMilliFormatter().format(gpsLog.getTimestamp()));
            if (gpsLog.getGeoPos() != null) {
                double[] arr = ApproxSwissProj.WGS84toLV03(gpsLog.getGeoPos().getY(), gpsLog.getGeoPos().getX(), 0L);
                propertiesObj.addProperty("coordinate_swiss", arr[0] + "," + arr[1]);
                propertiesObj.addProperty("speed", gpsLog.getSpeed());
            }

            JsonObject featureObj = new JsonObject();
            featureObj.addProperty("type", "Feature");
            featureObj.add("geometry", geometryObj);
            featureObj.add("properties", propertiesObj);
            return featureObj;
        }
    }

    @Override
    public String toKml() {
        String kmlStr = "";
        kmlStr += "<Placemark>";
        kmlStr += "<name>gpslog"+ this.id +"</name>";
        kmlStr += "<description>The gps point</description>";
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
        gmlStr += "<ecol:sensor_id>"+ this.device.id() +"</ecol:sensor_id>";
        gmlStr += "<ecol:timestamp>"+ this.timestamp.toString() +"</ecol:timestamp>";
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
     * Save the GpsLog in Postgres database
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