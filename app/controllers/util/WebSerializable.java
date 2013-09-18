package controllers.util;

import controllers.util.json.GeoJsonSerializable;
import controllers.util.json.JsonSerializable;
import controllers.util.xml.GmlSerializable;
import controllers.util.xml.KmlSerializable;

public interface WebSerializable extends JsonSerializable, KmlSerializable, GmlSerializable {
}
