var MapLayerUtil = Backbone.Model.extend({
	defaults: { 
		selected: false,
		activeGraph: null,
		defaultStyle: new OpenLayers.Style({
			'pointRadius': 3,
			'strokeColor': '#ee9900',
			'fillColor': 'transparent'
		}),
		selectStyle: new OpenLayers.Style({
			'pointRadius': 5,
			'strokeColor': '#66cccc',
			'fillColor': '#66cccc'
		}),
		nbTrajectory: 0,
		trajectoryLayers: {}
	}, 
	initialize: function() { 
		//this.map = new OpenLayers.Map("mapPanel");
		var position    = new OpenLayers.LonLat(6.566,46.519).transform('EPSG:4326', 'EPSG:3857'); // Google.v3 uses web mercator as projection, so we have to transform our coordinates
		//var zoom        = 17;
		var epsg900913 = new OpenLayers.Projection("EPSG:900913");
		
		this.styleMap = new OpenLayers.StyleMap({
			'default': this.get('defaultStyle'),
			'select': this.get('selectStyle')
		})

		var gmapLayer = new OpenLayers.Layer.Google(
			"Google Satellite",
			{type: google.maps.MapTypeId.SATELLITE, numZoomLevels: 21} // put only 21 levels. At level 22 data layer is not displayed correctly
		);		

		// Add layer (Google map)
		//this.map.addLayer(gmapLayer);
		
		// set map center point, and zoom level
		//this.map.setCenter(position, zoom);
		
		var maxHeight = $('#mapPanel').height();
		this.mapPanel = new GeoExt.MapPanel({
			renderTo: "mapPanel", 		// Name of the HTML DOM the panel will be rendered in
			map: new OpenLayers.Map({
				projection: epsg900913, // Make sure the projection is Google's spherical mercator
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
	},
	
	/**
	 * Add a layer with a trajectory (either linestring or points)
	 * @param mission The mission 
	 * @param asLinestring Indicates if the data are displayed as linestring (otherwise set of points)
	 */
	addTrajectoryLayer: function(mission, asLinestring) {
		var mode = "";
		if (asLinestring)
			mode = "linestring";
		else
			mode = "points";
		var geojsonUrl = config.get('URL_PREFIX') +"/api/trajectory?max_nb="+config.get('MAX_NB_DATA_POINTS_ON_MAP')+"&format=geojson&mode="+mode+"&mission_id="+mission.id;
		console.log(geojsonUrl);
		var layerTitle = mission.date + " - " + mission.vehicle;
		var nbTraj = this.get('nbTrajectory') + 1;
		var color = "rgb(255,"+Math.floor(175/nbTraj)+",0)";
		var customStyleMap = new OpenLayers.StyleMap({
			"default": new OpenLayers.Style({
				pointRadius: 3,
				strokeColor: color,
				fillColor: color
			})
		})
		this.set({nbTrajectory: nbTraj});
		var trajectoryLayer = new OpenLayers.Layer.Vector(layerTitle, {
			strategies: [new OpenLayers.Strategy.Fixed()],
			projection: new OpenLayers.Projection("EPSG:4326"),
			protocol: new OpenLayers.Protocol.HTTP({
				url: geojsonUrl,
				format: new OpenLayers.Format.GeoJSON()
			}),
			styleMap: customStyleMap
		});
		
		// Add layer to map
		this.mapPanel.map.addLayer(trajectoryLayer);
		var map = this.get('trajectoryLayers');
		map[mission.id] = layerTitle;
		this.set({trajectoryLayers: map});
		
		if (!asLinestring) {
			this.highlightCtrl = new OpenLayers.Control.SelectFeature(trajectoryLayer, {
				hover: true,
				highlightOnly: true,
				renderIntent: "temporary",
				eventListeners: {
					//beforefeaturehighlighted: printFeatureDetails,
					featurehighlighted: this.printFeatureDetails
					//featureunhighlighted: printFeatureDetails
				}
			});
			// Add controls
			this.mapPanel.map.addControl(this.highlightCtrl);
			this.highlightCtrl.activate();
			//this.map.addControl(new OpenLayers.Control.LayerSwitcher());
			//this.testFeatures(this.gmlLayer, 0, callback);
		}
		
		this.addRasterLayer(mission);
	},
	
	addRasterLayer: function(mission) {
		var self = this;
		var url = config.get('URL_PREFIX') +"/api/footage/fordate/"+mission.date;
		$.ajax({
			url: url
		}).done(function( jsonData ) {
			for (j=0;j<jsonData.length;j++){
				var raster = new OpenLayers.Layer.WMS("layer "+ mission.date +"("+j+")", "http://topopc12.epfl.ch:8080/geoserver/opengeo/wms",
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
		});
	},
	
	/**
	 * Reload the data layer (the itinerary of the boat). Called when click on update button - NO MORE USED
	 * @param gmlUrl The url for the GML data
	 * @param datatype The type of data displayed
	 * @param callback The callback function to call when the features are loaded
	 */
	refreshDataLayer: function(gmlUrl, datatype, callback) {
		this.removeGmlLayer();
		$('#loadingGifPlaceholder').show();
		$('#infoDiv').hide();
		//var gmlUrl = "http://localhost/playproxy/api/gml/"+ datatype +"/from/"+ startDate +"/to/"+ endDate +"/sensorid/"+sid;
		//var gmlUrl = "http://localhost/playproxy/api/data?data_type="+ datatype +"&from_date="+ startDate +"&to_date="+ endDate +"&sensor_id="+sid+"&format=gml";
		console.log(gmlUrl);
		var layerTitle = datatype + " logs";
		/*this.gmlLayer = new OpenLayers.Layer.GML(layerTitle, gmlUrl, {
			projection: new OpenLayers.Projection("EPSG:4326"),
			styleMap: this.styleMap
		});*/
		this.gmlLayer = new OpenLayers.Layer.Vector(layerTitle, {
			strategies: [new OpenLayers.Strategy.Fixed()],
			projection: new OpenLayers.Projection("EPSG:4326"),
			protocol: new OpenLayers.Protocol.HTTP({
				url: gmlUrl,
				format: new OpenLayers.Format.GeoJSON()
			}),
			styleMap: this.styleMap
		});
		/* not necessary to use external/internal projections
		var geojson_format = new OpenLayers.Format.GeoJSON({
			externalProjection: new OpenLayers.Projection("EPSG:4326"),
			internalProjection: new OpenLayers.Projection("EPSG:3857")
		});
		*/
		
		this.highlightCtrl = new OpenLayers.Control.SelectFeature(this.gmlLayer, {
			hover: true,
			highlightOnly: true,
			renderIntent: "temporary",
			eventListeners: {
				//beforefeaturehighlighted: printFeatureDetails,
				featurehighlighted: this.printFeatureDetails
				//featureunhighlighted: printFeatureDetails
			}
		});

		this.selectCtrl = new OpenLayers.Control.SelectFeature(this.gmlLayer, {
			clickout: true,
			onSelect: this.selectData,
			onUnselect: this.unselectData
		});
		
		// Add GML layer
		this.mapPanel.map.addLayer(this.gmlLayer);
		
		// Add controls
		this.mapPanel.map.addControl(this.highlightCtrl);
		this.mapPanel.map.addControl(this.selectCtrl);
		this.highlightCtrl.activate();
		this.selectCtrl.activate();
		this.mapPanel.map.addControl(new OpenLayers.Control.LayerSwitcher());
		this.testFeatures(this.gmlLayer, 0, callback);
	},
	/**
	 * Remove the gml layer (data)
	 */
	removeGmlLayer: function() {
		if (this.gmlLayer) {	
			this.mapPanel.map.removeLayer(this.gmlLayer);
		}
	},
	/**
	 * Remove all path layers
	 */
	removeLayers: function() {
		var nbLayers = this.mapPanel.map.getLayersBy("isBaseLayer",false);
		for(var a = 0; a < nbLayers.length; a++ ){
			if (nbLayers[a].isBaseLayer==false){
				this.mapPanel.map.removeLayer(nbLayers[a])
			}
		};
		this.set({nbTrajectory: 0});
	},
	printFeatureDetails: function(e) {
		//console.log(e.feature['attributes']);
		//console.log(mapLayerUtil.gmlLayer.features[0]); // e.feature['attributes'].id);
		updateInfoDiv("infoDiv", e.feature['attributes'], true);
		if (mapLayerUtil.has('activeGraph')) {
			mapLayerUtil.updateHoverLine(e);
		}
	},
	/**
	 * Check that the features are loaded
	 * @param layer The data layer to check
	 * @param inc The nb of times we checked (check is done every 500ms, 40x at most -> 20s max)
	 * @param callback The callback function to call when the features are loaded
	 */
	testFeatures: function(layer, inc, callback) {
		var self = this;
		var nb = layer.features.length;
		if (inc < 40 && nb == 0) {
			setTimeout(function() {self.testFeatures(layer, inc + 1, callback)}, 500)
		} else {
			if (nb > 0) {
				console.log("Features are loaded ["+ nb +"]");
				//console.log(layer.features[0]);
				var offset = 300; // use an offset to set the first point a bit on the top right (because of graph on the left)
				var x = layer.features[0].geometry.x + offset;
				//var x = layer.features[0].geometry.components[0].x + offset; // if trajectory is linestring
				var y = layer.features[0].geometry.y - offset;
				//var y = layer.features[0].geometry.components[0].y - offset; // if trajectory is linestring
				var newPos = new OpenLayers.LonLat(x,y); //.transform('EPSG:4326', 'EPSG:3857');
				this.mapPanel.map.setCenter(newPos, 17);
				$('#infoDiv').show();
				// call the function to load the data for the embedded graph 
				if (callback != undefined) {
					callback();
				} else {
					$('#loadingGifPlaceholder').hide();
				}
			} else {
				console.log("Features loading took too much time !");
			}
		}
	},
	selectData: function(e) {
		//console.log("Click: "+e['attributes'].value);
		this.set({selected: false});
		$('#infoDiv').css({'border-color': '#66cccc'})
		updateInfoDiv(e['attributes']);
		this.set({selected: true});
	},
	unselectData: function(e) {
		$('#infoDiv').css({'border-color': 'white'})
		this.set({selected: false});
	},
	/**
	 * Highlight a specific feature (called from graphD3.js
	 * @param xpf The index of the feature to highlight as a fraction of the total nb of features
	 */
	highlightFeaturePoint: function(xpf) {
		this.highlightCtrl.unselectAll();
		//console.log("highlightFeaturePoint() - "+xpf);
		var map = this.get('trajectoryLayers');
		var layerName = map[$('#pathSelect').val()];
		var layerForGraph = this.mapPanel.map.getLayersByName(layerName)[0];
		//console.log(layerForGraph.features.length);
		var index = Math.round(xpf * layerForGraph.features.length);
		if (index == layerForGraph.features.length)
			index = layerForGraph.features.length - 1;
		//console.log("highlightFeaturePoint() - "+index)
		this.highlightCtrl.select(layerForGraph.features[index]);
	},
	/**
	 * Update the position of the line on the embedded graph according to the selected feature
	 */
	updateHoverLine: function(e) {
		//console.log("updateHoverLine()");
		//var gpsDataFirstId = mapLayerUtil.gmlLayer.features[0]['attributes'].id;
		//var featureDeltaId = e.feature['attributes'].id - gpsDataFirstId;
		//console.log("GPS data len: "+mapLayerUtil.gmlLayer.features.length +", delta with highlighted: "+featureDeltaId);
		var d = this.get('activeGraph').sensorLogs[0].values;
		var firstLogTime = d[0].date.getTime();
		//console.log("sensorLogs length: "+d.length);
		var lastLogTime = d[d.length-1].date.getTime();
		var timeRange = lastLogTime - firstLogTime;
		var logTime = this.get('activeGraph').formatDate.parse(e.feature['attributes'].timestamp).getTime() - firstLogTime;
		//console.log("logTime: "+logTime+", width: "+this.get('activeGraph').get('width')+", timeRange: "+timeRange);
		var xPos = logTime * this.get('activeGraph').get('width') / timeRange;
		//console.log("xPos: "+xPos);
		if(xPos >= this.get('activeGraph').get('width')) {
			xPos = this.get('activeGraph').get('width');
		} else if (xPos < 0) {
			xPos = 0;
		}
		xPos = Math.round(xPos);
		var dataValue = this.get('activeGraph').getValueForPositionXFromData(xPos, 0);
		var valueHtml = "<b>Value:</b> "+ Number(dataValue.value).toFixed(3) +"<br/>";
		if (this.get('activeGraph').sensorLogs.length == 1) {
			$('#'+ this.getInfoContainerId() +' > #dataValuePlaceholder').html(valueHtml);
		}
		//console.log("xPos: "+xPos);
		this.get('activeGraph').updateHoverLine(xPos);
	},
	/**
	 * Get the id of the container for the details of the highlighted data
	 */
	getInfoContainerId: function() {
		var containerElementId = "infoDiv";
		if (this.has('activeGraph') && this.get('activeGraph').get('withTooltip')) {
			containerElementId = "tooltipText";
		}
		return containerElementId;
	}
});