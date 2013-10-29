function MapLayerUtil() {
    this.initialize();
}

/* initialize() function */
MapLayerUtil.prototype.initialize = function() {
    /// default values
    this.selected = false;
    this.activeGraph = null;
    this.defaultStyle = new OpenLayers.Style({
                        'pointRadius': 3,
                        'strokeColor': '#ee9900',
                        'fillColor': 'transparent'
                    });
    this.selectStyle = new OpenLayers.Style({
                        'pointRadius': 5,
                        'strokeColor': '#66cccc',
                        'fillColor': '#66cccc'
                    });
    this.epsg900913 = new OpenLayers.Projection("EPSG:900913");
    this.epsg4326 = new OpenLayers.Projection("EPSG:4326");
    this.nbTrajectory = 0;
    this.interactiveLayers = {};  // map of objects {'layer':layer, 'mission': mission}
    this.maximumSpeed = 0.0;      // specific to the selected trajectory (selected in the graph panel)
    this.headingAvailable = false; // specific to the selected trajectory (selected in the graph panel)

    var position = new OpenLayers.LonLat(6.566,46.519).transform('EPSG:4326', 'EPSG:3857'); // Google.v3 uses web mercator as projection, so we have to transform our coordinates
    this.styleMap = new OpenLayers.StyleMap({
        'default': this.defaultStyle,
        'select': this.selectStyle
    });
    var gmapLayer = new OpenLayers.Layer.Google(
        "Google Satellite",
        {type: google.maps.MapTypeId.SATELLITE, numZoomLevels: 21} // put only 21 levels. At level 22 data layer is not displayed correctly
    );
    var maxHeight = $('#mapPanel').height();
    this.mapPanel = new GeoExt.MapPanel({
        renderTo: "mapPanel", 		// Name of the HTML DOM the panel will be rendered in
        map: new OpenLayers.Map({
            projection: this.epsg900913, // Make sure the projection is Google's spherical mercator
            units: "m",		// Map horizontal units are meters
            center: position 	// Center on Leman lake
        }),
        zoom: 11,
        layers: [
            //new OpenLayers.Layer.OSM("Open Street Map"),	// We want OSM as base layer
            gmapLayer
        ],
        height: maxHeight,			// in pixels
        border: false,
        maxResolution: 1000
    });
    // disable 3D effect with google map
    gmapLayer.mapObject.setTilt(0);
    window.onmousemove = this.handleMouseMove;
};

/**
 * Add all the layers related to a mission (trajectory, raster, POI)
 * @param mission
 * @param isUlmMission
 */
MapLayerUtil.prototype.addLayers = function(mission) {
    this.interactiveLayers = {}; // reset map when loading new layers
    this.addRasterLayer(mission);
    if (isUlmMission(mission)) {
        // for ULM mission, add line + points (trajectory)
        this.addTrajectoryLayer(mission, config.MODE_LINESTRING);
        this.addTrajectoryLayer(mission, config.MODE_POINTS);
    } else {
        // for catamaran mission add only points (trajectory)
        this.addTrajectoryLayer(mission, config.MODE_POINTS);
    }
    this.getPoiForMission(mission, this.addControls);
    this.testFeatures(0, createPathSelectForData);
};

/**
 * Add the controls (highlight & select), called from addPointOfInterestLayer() as callback
 */
MapLayerUtil.prototype.addControls = function() {
    var layers = new Array();
    var map = mapLayerUtil.interactiveLayers;
    for (m in map) {
        layers.push(map[m].layer);
    }
    //console.log(layers);
    // layers for highlight and select must be the same (two sets of layers create a bug)
    mapLayerUtil.setHighlightCtrl(layers);
    mapLayerUtil.setSelectCtrl(layers);
};

MapLayerUtil.prototype.addLayerInInteractiveLayerMap = function(layerKey, layer, mission) {
    var map = this.interactiveLayers;
    map[layerKey] = {'layer':layer, 'mission': mission};
    this.interactiveLayers = map;
};

/**
 * Add a layer with a trajectory (either linestring or points)
 * @param mission The mission
 * @param mode Indicates if the data are displayed as linestring (otherwise set of points)
 */
MapLayerUtil.prototype.addTrajectoryLayer = function(mission, mode) {
    var geojsonUrl = config.URL_PREFIX +"/api/trajectory?max_nb="+config.MAX_NB_DATA_POINTS_ON_MAP+"&format=geojson&mode="+mode+"&mission_id="+mission.id;
    console.log(geojsonUrl);
    var suffix = "";
    if (mode == config.MODE_LINESTRING)
        suffix = " (Line)"
    var layerTitle = mission.date + " - " + mission.vehicle + suffix;
    var nbTraj = this.nbTrajectory + 1;
    var color = "rgb(255,"+Math.floor(175/nbTraj)+",0)";
    this.nbTrajectory = nbTraj;
    var trajectoryLayer = new OpenLayers.Layer.Vector(layerTitle, {
        strategies: [new OpenLayers.Strategy.Fixed()],
        projection: this.epsg4326,
        protocol: new OpenLayers.Protocol.HTTP({
            url: geojsonUrl,
            format: new OpenLayers.Format.GeoJSON({
                ignoreExtraDims: true // necessary to ignore 3rd coordinate (z) in geojson points
            })
        }),
        styleMap: this.getStyleMap(mode, isUlmMission(mission), color)
    });

    // Add layer to map
    this.mapPanel.map.addLayer(trajectoryLayer);
    if (mode == "points") {
        this.addLayerInInteractiveLayerMap(mission.id, trajectoryLayer, mission);
    }
};

/**
 * Add a layer with the points of interest
 * @param mission The mission linked to the points of interest
 * @param callback The callback to add the controls
 */
MapLayerUtil.prototype.addPointOfInterestLayer = function(mission, callback) {
    var geojsonUrl = config.URL_PREFIX +"/api/pointsofinterest/formission/"+mission.id;
    console.log(geojsonUrl);
    var layerTitle = mission.date + " - " + mission.vehicle + " (POI)";

    var pink = "rgb(255,105,180)"; // pink
    var white = "rgb(255,255,255)";
    var customStyleMap = new OpenLayers.StyleMap({
        "default": new OpenLayers.Style({
            pointRadius: 5,
            strokeColor: pink,
            fillColor: pink
        }),
        "temporary": new OpenLayers.Style({
            pointRadius: 5,
            strokeColor: white,
            fillColor: pink
        }),
        "select": new OpenLayers.Style({
            pointRadius: 5,
            strokeColor: pink,
            fillColor: white
        }),
    })

    var poiLayer = new OpenLayers.Layer.Vector(layerTitle, {
        strategies: [new OpenLayers.Strategy.Fixed()],
        projection: this.epsg4326,
        protocol: new OpenLayers.Protocol.HTTP({
            url: geojsonUrl,
            format: new OpenLayers.Format.GeoJSON({
                ignoreExtraDims: true // necessary to ignore 3rd coordinate (z) in geojson points
            })
        }),
        styleMap: customStyleMap
    });

    // Add layer to map
    //console.log(poiLayer.features.length);
    this.mapPanel.map.addLayer(poiLayer);
    // add POI layer in interactive layers map
    this.addLayerInInteractiveLayerMap(mission.id+"-POI", poiLayer);

    // Add controls
    callback();
};

MapLayerUtil.prototype.selectPoi = function(ev) {
    var selectedLayer = ev.layer;
    //console.log(ev.layer);
    if (selectedLayer.name.indexOf("POI") > -1) {
        console.log("POI selected");
    }
};

/**
 * Add a raster layer
 * @param mission The mission linked to the raster layer
 */
MapLayerUtil.prototype.addRasterLayer = function(mission) {
    var self = this;
    var datesURL = config.URL_PREFIX +"/api/rasterdata/formission/"+mission.id;
    $.ajax({
        url:datesURL
    }).done(function(jsonData){
        jsonData=jsonData;
        for (j=0;j<jsonData.length;j++){
            var raster = new OpenLayers.Layer.WMS(jsonData[j].device.name+" ("+ mission.date +" - "+mission.vehicle+")", "http://ecolvm1.epfl.ch/geoserver/"+mission.vehicle+"/wms",
                {
                    layers: jsonData[j].imagename,
                    transparent: true

                },
                {
                    isBaseLayer: false,
                    displayOutsideMaxExtent: true // Will not appear with certain zooms without this option
                }
            );

            self.mapPanel.map.addLayers([raster])
        }
    })
};

/**
 * Get the styleMap according to the type of mission and trajectory display mode
 * @param mode
 * @param isUlmMission
 * @param color
 */
MapLayerUtil.prototype.getStyleMap = function(mode, isUlmMission, color) {
    var styleMap;
    if (mode == config.MODE_POINTS && isUlmMission) {
        // if it is a ULM mission and the trajectory is shown as points -> make them transparent
        styleMap = new OpenLayers.StyleMap({
            "default": new OpenLayers.Style({
              pointRadius: 3,
              strokeColor: "transparent",
              fillColor: "transparent"
            }),
            "temporary": new OpenLayers.Style({
              pointRadius: 6,
              strokeColor: "rgb(255,255,255)",
              fillColor: "transparent"
            }),
            "select": new OpenLayers.Style({
              pointRadius: 3,
              strokeColor: color,
              fillColor: color
            })
        })
    } else {
        styleMap = new OpenLayers.StyleMap({
            "default": new OpenLayers.Style({
                pointRadius: 3,
                strokeColor: color,
                fillColor: color
            }),
            "temporary": new OpenLayers.Style({
                pointRadius: 6,
                strokeColor: "rgb(255,255,255)",
                fillColor: "transparent"
            }),
            "select": new OpenLayers.Style({
                pointRadius: 3,
                strokeColor: color,
                fillColor: color
            })
        })
    }
    return styleMap;
};

/**
 * Get the points of interest of a mission. If there are some, then create layer for them
 * @param mission The mission
 * @param callback The callback to call when layer is loaded
 */
MapLayerUtil.prototype.getPoiForMission = function(mission, callback) {
    //console.log("getPoiForMission()", mission);
    var self = this;
    $.ajax({
        url: config.URL_PREFIX +"/api/pointsofinterest/formission/"+mission.id
    }).done(function( jsonData ) {
        if(jsonData.features.length > 0) {
            self.addPointOfInterestLayer(mission, callback);
        } else {
            callback();
        }
    });
};

/**
 * Set a highlight control on the map. It takes an array of layers because
 * multiple highlight controls are not supported (http://lists.osgeo.org/pipermail/openlayers-dev/2010-October/006589.html)
 * @param layers The array of layers impacted by this control
 */
MapLayerUtil.prototype.setHighlightCtrl = function(layers) {
    //console.log("setHighlightCtrl()", layers);
    this.highlightCtrl = new OpenLayers.Control.SelectFeature(layers, {
        hover: true,
        highlightOnly: true,
        renderIntent: "temporary",
        eventListeners: {
            //beforefeaturehighlighted: printFeatureDetails,
            featurehighlighted: this.printFeatureDetails,
            featureunhighlighted: function() {
                $('#speedVectorPlaceholder').hide();
            }
        }
    });
    this.mapPanel.map.addControl(this.highlightCtrl);
    this.highlightCtrl.activate();
};

/**
 * Set a select control on the map. It takes an array of layers because
 * multiple select controls are not supported (http://lists.osgeo.org/pipermail/openlayers-dev/2010-October/006589.html)
 * @param layers The array of layers impacted by this control
 */
MapLayerUtil.prototype.setSelectCtrl = function(layers) {
    this.selectCtrl = new OpenLayers.Control.SelectFeature(layers, {
        clickout: true,
        onSelect: this.selectPoi,
        //onUnselect: this.unselectData
    });
    this.mapPanel.map.addControl(this.selectCtrl);
    this.selectCtrl.activate();
};

/**
 * Remove all path layers
 */
MapLayerUtil.prototype.removeLayers = function() {
    var nbLayers = this.mapPanel.map.getLayersBy("isBaseLayer",false);
    for(var a = 0; a < nbLayers.length; a++ ){
        if (nbLayers[a].isBaseLayer==false){
            this.mapPanel.map.removeLayer(nbLayers[a])
        }
    };
    this.nbTrajectory = 0;
};

MapLayerUtil.prototype.printFeatureDetails = function(e) {
    var selectedLayer = e.feature.layer;
    // show speed vector if there is a maximum speed, not otherwise
    if (mapLayerUtil.maximumSpeed > 0 && mapLayerUtil.headingAvailable) {
        $('#speedVectorPlaceholder').show();
        var customStyle = new OpenLayers.Style({ // highlight is all transparent because we show speed cursor
            pointRadius: 6,
            strokeColor: "transparent",
            fillColor: "transparent"
        });
        selectedLayer.styleMap.styles.temporary = customStyle;
    }
    //console.log(e.object.handlers.feature);
    //console.log(mapLayerUtil.gmlLayer.features[0]); // e.feature['attributes'].id);
    //updateInfoDiv("infoDiv", e.feature['attributes'], true);
    if (selectedLayer.name.indexOf("POI") == -1) {
        // not a POI layer
        if (mapLayerUtil.activeGraph != null) {
            mapLayerUtil.updateHoverLine(e);
            mapLayerUtil.updateSpeedVector(e);
        }
    }
};

/**
 * Check that the features are loaded
 * @param inc The nb of times we checked (check is done every 500ms, 40x at most -> 20s max)
 * @param callback The callback function to call when the features are loaded
 */
MapLayerUtil.prototype.testFeatures = function(inc, callback) {
    var self = this;
    //var nb = layer.features.length;
    if (inc < 40 && !this.featuresAreLoaded()) {
        setTimeout(function() {self.testFeatures(inc + 1, callback)}, 500)
    } else {
        if (self.featuresAreLoaded()) {
            //console.log("Features are loaded [All]");
            $('#loadingGifPlaceholder').hide();
            self.centerOnLoadedFeatures();
            // call the function to load the data for the embedded graph
            if (callback != undefined) {
                callback();
            }
        } else {
            console.log("Features loading took too much time !");
        }
    }
};

/**
 * Check that features of all interactive layers are loaded
 */
MapLayerUtil.prototype.featuresAreLoaded = function() {
    var loaded = true;
    var map = mapLayerUtil.interactiveLayers;
    for (m in map) {
        if (map[m].layer.features.length == 0) {
            // if one layer is not loaded yet, set loaded to false
            loaded = false;
        }
    }
    return loaded;
};

/**
 * Center map on selected trajectory
 */
MapLayerUtil.prototype.centerOnLoadedFeatures = function() {
    var map = mapLayerUtil.interactiveLayers;
    var layerObj = first(map);
    var offset = 300; // use an offset to set the first point a bit on the top right (because of graph on the left)
    var x = layerObj.layer.features[0].geometry.x + offset;
    var y = layerObj.layer.features[0].geometry.y - offset;
    var newPos = new OpenLayers.LonLat(x,y); //.transform('EPSG:4326', 'EPSG:3857');
    var zoom;
    if (isUlmMission(layerObj.mission)) {
        zoom = config.ZOOM_LEVEL_ULM;
    } else {
        zoom = config.ZOOM_LEVEL_CATAMARAN;
    }
    try {
        // sometimes get an exception from GeoExt.js, so catch it
        mapLayerUtil.mapPanel.map.setCenter(newPos, zoom);
    } catch (err) {
        //console.log("[WARNING] "+err.message)
    }
};

MapLayerUtil.prototype.selectData = function(e) {
    //console.log("Click: "+e['attributes'].value);
    this.selected = false;
    $('#infoDiv').css({'border-color': '#66cccc'})
    updateInfoDiv(e['attributes']);
    this.selected = true;
};

MapLayerUtil.prototype.unselectData = function(e) {
    $('#infoDiv').css({'border-color': 'white'})
    this.selected = false;
};

/**
 * Highlight a specific feature (called from graphD3.js
 * @param xpf The index of the feature to highlight as a fraction of the total nb of features
 */
MapLayerUtil.prototype.highlightFeaturePoint = function(xpf) {
    this.highlightCtrl.unselectAll();
    //console.log("highlightFeaturePoint() - "+xpf);
    var map = this.interactiveLayers;
    var layerForGraph = map[$('#pathSelect').val()].layer;
    //var layerForGraph = this.mapPanel.map.getLayersByName(layerName)[0];
    //console.log(layerForGraph.features.length);
    var index = Math.round(xpf * layerForGraph.features.length);
    if (index == layerForGraph.features.length)
        index = layerForGraph.features.length - 1;
    //console.log("highlightFeaturePoint() - "+index)
    this.highlightCtrl.select(layerForGraph.features[index]);
};

/**
 * Update the position of the line on the embedded graph according to the selected feature
 */
MapLayerUtil.prototype.updateHoverLine = function(e) {
    //console.log("updateHoverLine()");
    //var gpsDataFirstId = mapLayerUtil.gmlLayer.features[0]['attributes'].id;
    //var featureDeltaId = e.feature['attributes'].id - gpsDataFirstId;
    //console.log("GPS data len: "+mapLayerUtil.gmlLayer.features.length +", delta with highlighted: "+featureDeltaId);
    var d = this.activeGraph.sensorLogs[0].values;
    var firstLogTime = d[0].date.getTime();
    //console.log("sensorLogs length: "+d.length);
    var lastLogTime = d[d.length-1].date.getTime();
    var timeRange = lastLogTime - firstLogTime;
    var logTime = this.activeGraph.formatDate.parse(e.feature['attributes'].timestamp).getTime() - firstLogTime;
    //console.log("logTime: "+logTime+", width: "+this.get('activeGraph').get('width')+", timeRange: "+timeRange);
    var xPos = logTime * this.activeGraph.width / timeRange;
    //console.log("xPos: "+xPos);
    if(xPos >= this.activeGraph.width) {
        xPos = this.activeGraph.width;
    } else if (xPos < 0) {
        xPos = 0;
    }
    xPos = Math.round(xPos);
    var dataValue = this.activeGraph.getValueForPositionXFromData(xPos, 0);
    var valueHtml = "<b>Value:</b> "+ Number(dataValue.value).toFixed(3) +"<br/>";
    if (this.activeGraph.sensorLogs.length == 1) {
        $('#'+ this.getInfoContainerId() +' > #dataValuePlaceholder').html(valueHtml);
    }
    //console.log("xPos: "+xPos);
    this.activeGraph.updateHoverLine(xPos);
};

/**
 * Get the id of the container for the details of the highlighted data
 */
MapLayerUtil.prototype.getInfoContainerId = function() {
    var containerElementId = "infoDiv";
    if (this.activeGraph != null && this.activeGraph.withTooltip) {
        containerElementId = "tooltipText";
    }
    return containerElementId;
};

MapLayerUtil.prototype.updateSpeedVector = function(e) {
    //console.log("updateSpeedVector()", e.feature.attributes.heading);
    var vectorWidth = $('#speedVectorPlaceholder').width();
    var vectorHeight = $('#speedVectorPlaceholder').height();
    // get x,y position on map from lon, lat properties of feature
    var lonLat = new OpenLayers.LonLat(e.feature.geometry.x, e.feature.geometry.y);
    var mapXY = mapLayerUtil.mapPanel.map.getViewPortPxFromLonLat(lonLat);
    var xPos = mapXY.x - vectorWidth/2; // + Math.sin(heading) * vectorWidth;
    var yPos = mapXY.y - vectorHeight/2; // + Math.cos(heading) * vectorHeight;
    var normalizedSpeed = 1.0;
    if (mapLayerUtil.get('maximumSpeed') > 0) {
        normalizedSpeed = e.feature.attributes.speed / (mapLayerUtil.get('maximumSpeed') / 1.7);
    }
    $('#speedVectorPlaceholder').css({
        top: yPos,
        left: xPos,
        'transform': 'rotate('+e.feature.attributes.heading+'deg) scale('+ normalizedSpeed +')',
        '-webkit-transform': 'rotate('+e.feature.attributes.heading+'deg) scale('+ normalizedSpeed +')'
    });
};

/**
 * Get X and Y position of mouse
 */
MapLayerUtil.prototype.getMouseXY = function(event) {
    var graphXOffset = 0;
    var graphYOffset = 0;
    var offsetX, offsetY;
    if(event.offsetX==undefined) {
        // Firefox
        offsetX = event.pageX; //-this.containerElement.offset().left;
        offsetY = event.pageY; //-this.containerElement.offset().top;
    } else {
        // Chrome
        offsetX = event.offsetX;
        offsetY = event.offsetY;
    }

    var mouseX = offsetX - graphXOffset;
    var mouseY = offsetY - graphYOffset;
    //console.log("x: "+mouseX+", y: "+mouseY);
    return {mouseX: mouseX, mouseY: mouseY};
};
