var config = new Config();
var mapLayerUtil = new MapLayerUtil();
var embeddedGraph = new GraphD3({
	containerElementId: 'graphPlaceholder', 
	svgElementId: 'svgElement1',
	linkWithGeoData: true
});
var zoomedGraph;
var dataJsonUrl;
var dateList = {}; // map containing the mission dates
var currentMissions = new Array();
var nbSelectedDates, nbMissionListReceived = 0;

/**
 * Initialize the select panel (right). It is the first function to be called when document is ready.
 */
function initSelectPanel() {
	/*$('.timeField').change(function() {
		//alert('Handler for .change() called.');
		//console.log($("#startTimeField").val());
		//console.log($("#endTimeField").val());
		//console.log($("#dateSelect").val());
		var date = $("#dateSelect").val();
		var st = $("#startTimeField").val();
		var et = $("#endTimeField").val();
		//refreshSensorField(date, st, et);
	});*/
	
	
	
	/*$('#updateBtn').click(function() {
		var sensorId = $("#sensorSelect").val();
		var sensorParts = sensorId.split('--');
		var datatype = sensorParts[0];
		var sid = sensorParts[1];
		var date = $("#dateSelect").val()
		var startTime = $("#startTimeField").val()
		var endTime = $("#endTimeField").val()
		var dateNoDash = date.replace(/-/g, "");
		var stNoColumn = startTime.replace(/:/g, "");
		var etNoColumn = endTime.replace(/:/g, "");
		var startDate = dateNoDash+"-"+stNoColumn;
		var endDate = dateNoDash+"-"+etNoColumn;
		var gmlUrl = config.get('URL_PREFIX') +"/api/data?data_type="+ datatype +"&from_date="+ startDate +"&to_date="+ endDate +"&sensor_id="+sid+"&max_nb="+config.get('MAX_NB_DATA_POINTS_ON_MAP')+"&format=gml";
		var geojsonUrl = config.get('URL_PREFIX') +"/api/gpsdata?from_date="+ startDate +"&to_date="+ endDate +"&max_nb="+config.get('MAX_NB_DATA_POINTS_ON_MAP')+"&format=geojson";
		dataJsonUrl = config.get('URL_PREFIX') +"/api/data?data_type="+ datatype +"&from_date="+ startDate +"&to_date="+ endDate +"&sensor_id="+sid;
		var dataJsonUrlEmbeddedGraph = dataJsonUrl + "&max_nb="+config.get('MAX_NB_DATA_POINTS_ON_MAP')
		var callback;
		if (datatype != "gps") {
			callback = function() {
				embeddedGraph.refreshSensorGraph(dataJsonUrlEmbeddedGraph, false);
				$("#graphPanelZoomBtnPlaceholder").attr("onclick", "zoomGraph('"+ datatype +"','"+ sid +"')");
			};
			mapLayerUtil.set({activeGraph: embeddedGraph});
		}
		mapLayerUtil.refreshDataLayer(geojsonUrl, datatype, callback);
		$('#graphPanel').hide();
	});*/
	
	createLayerTreeControls();
	
	$.ajax({
	  url: config.get('URL_PREFIX') +"/api/missiondates"
	}).done(function( jsonData ) {
		for (var i=0;i<jsonData.length;i++){
			addMissionDate(jsonData[i]);
		}
		createCalendar();
	});
}

/**
 * Create the controls to hide/show the various layers
 */
function createLayerTreeControls() {
	//	Defines the tree layer. First the node ...
	var layerRoot = new Ext.tree.TreeNode({
		text: "Layers",
		expanded: true,
	});

	//	... then add a child (Overlay) ...
	layerRoot.appendChild(new GeoExt.tree.LayerContainer({
		text: "Overlays",
		map: mapLayerUtil.mapPanel,
		expanded: true,
		singleClickExpand: true,
		loader: {
			filter: function(record) {
				return record.get("layer").isBaseLayer== true
			}
		}
	}));

	//	... a child containing the trajectories ...
	layerRoot.appendChild(new GeoExt.tree.LayerContainer({
		text: "Trajectories",
		map: mapLayerUtil.mapPanel,
		expanded: true,
		singleClickExpand: true,
		loader:{
			filter: function(record){
				res=record.get("layer").CLASS_NAME == "OpenLayers.Layer.Vector" && record.get("layer").isBaseLayer == false;
				return res
			}
		}
	}));
	
	//	... and an other child containing the layers
	layerRoot.appendChild(new GeoExt.tree.LayerContainer({
		text: "Layers",
		map: mapLayerUtil.mapPanel,
		expanded: false,
		singleClickExpand: true,
		loader:{
			filter: function(record){
				res = record.get("layer").CLASS_NAME == "OpenLayers.Layer.WMS" && record.get("layer").isBaseLayer == false;
				return res
				
			}
		}
		
	}));

	// display the tree in the appropriate DOM (layerTree defined in map.html)
	var tree = new Ext.tree.TreePanel({
		renderTo: "layerTree",
		frame: false,
		border: false,
		root: layerRoot,
		rootVisible: false
	});

    
	// legend panel
	legendPanel = new GeoExt.LegendPanel({
		border: false,
		layerStore: mapLayerUtil.mapPanel.layers,
		renderTo: "legendPanel",
		filter: function(record){
			return record.get("layer").CLASS_NAME == "OpenLayers.Layer.Vector"
		}
	});
}

function onLemanBtnClicked() {
	ptLeman= new OpenLayers.LonLat(6.59,46.39).transform(mapLayerUtil.get('epsg4326'),mapLayerUtil.get('epsg900913'));
    zoomLeman=10;
    mapLayerUtil.mapPanel.map.setCenter(ptLeman,zoomLeman);
}

function onBaikalBtnClicked() {
    ptBaikal= new OpenLayers.LonLat(108.208923,53.585984).transform(mapLayerUtil.get('epsg4326'),mapLayerUtil.get('epsg900913'));
    zoomBaikal=6;
    mapLayerUtil.mapPanel.map.setCenter(ptBaikal,zoomBaikal);
}

function onAddLayersClicked() {
	getMissionsForDates($('#calendar').DatePickerGetDate('ymd'));
}

function onResetClicked() {
	currentMissions = new Array(); // reset array
	//$('#dataSelectPanel').hide();
	$('#calendar').DatePickerClear(); 
	mapLayerUtil.removeLayers()
	$('#dataSelectPanel').hide();
	$('#deviceSelectRow').hide();
	$('#dataGraphPlaceholder').hide();
	closeGraphPanel();
}

/**
 * Get the missions for specific dates
 * @param dateArr The list of date
 */
function getMissionsForDates(dateArr) {
	nbSelectedDates = dateArr.length;
	nbMissionListReceived = 0;
	for (var i=0; i < dateArr.length; i++) {
		$.ajax({
			url: config.get('URL_PREFIX') +"/api/missions/fordate/"+dateArr[i]
		}).done(function( missions ) {
			nbMissionListReceived++;
			for (var j=0;j<missions.length;j++){
				currentMissions.push(missions[j]);
				// add path for each outings
				if (isUlmMission(missions[j])) {
					mapLayerUtil.addTrajectoryLayer(missions[j], true);
					//console.log("add linestring");
				} else if (isCatamaranMission(missions[j])) {
					mapLayerUtil.addTrajectoryLayer(missions[j], false);
					//console.log("add points");
				} else {
					console.log("[ERROR] Mission from unknown vehicle !!");
				}
				mapLayerUtil.addPointOfInterestLayer(missions[j]);
				mapLayerUtil.addRasterLayer(missions[j]);
			}
			if (nbMissionListReceived == nbSelectedDates) {			
				createPathSelectForData();
			}
		});
	}	
}

/**
 * Add a mission date in dateList with the proper class name (for calendar coloration)
 * @param mission The mission to consider
 */
function addMissionDate(mission) {
	var className;
	if (isUlmMission(mission)) {
		className = config.get('ULM_DATE_CLASSNAME');
	} else if (isCatamaranMission(mission)) {
		className = config.get('CAT_DATE_CLASSNAME');
	}
	var nDate = new Date(mission.departuretime.substring(0,10));
	if (!dateList.hasOwnProperty(nDate)) {
		dateList[nDate] = className;
	} else if (dateList[nDate] != className) {
		// flight & cruise exist for this date
		dateList[nDate] = config.get('ULM_CAT_DATE_CLASSNAME');
	}
}

function createCalendar() {
	// draw calendar
	$( "#calendar" ).DatePicker({
		flat: true,
		current: "2013-05-13",
		date: "2013-05-13",
		format: "Y-m-d",
		mode:"multiple",
		onRender: function(date){
			var res = checkSpecialDate(date);
			return res;
		}
	});
}

/**
 * Check if a mission is a ulm mission
 * @param mission The mission to check
 */
function isUlmMission(mission) {
	if (mission.vehicle == "ulm") {
		return true;
	} else {
		return false
	}
}

/**
 * Check if a mission is a catamaran mission
 * @param mission The mission to check
 */
function isCatamaranMission(mission) {
	if (mission.vehicle == "catamaran") {
		return true;
	} else {
		return false
	}
}

/**
 * Check if calendar date is a special date (defined in dateList)
 * @param date The current date to check
 */
function checkSpecialDate(date) {
	var res = { disabled: true };
	//console.log(dateList);
	for (var d in dateList) {
		var specialDate = new Date(d);
		// For some reason that eludes me, need the day before...
		var tmp = new Date(
			specialDate.getFullYear(), 
			specialDate.getMonth(), 
			specialDate.getDate() - 1,
			specialDate.getHours(),
			specialDate.getMinutes(),
			specialDate.getSeconds(),
			specialDate.getMilliseconds()
		)
		//console.log(date.toISOString().substring(0,10) + " - " +tmp.toISOString().substring(0,10));
		if (date.toISOString().substring(0,10)==tmp.toISOString().substring(0,10)){
			res = {
				disabled: false,
				className: dateList[d]
			}
		}
	};
	return res;
};

/**
 * Refresh the time fields 
 * @param date The selected date (2013-06-13)
 * @param set The data set number
 * @param refreshSensorSelect Indicates if the sensor field has to be refreshed
 */
function refreshTimeFields(date, set, refreshSensorSelect) {
	$.ajax({
		url: config.get('URL_PREFIX') +"/api/times/fordate/"+ date +"/andset/"+set
	}).done(function( jsonData ) {
		var startTime = jsonData['first_time']; // 16:30:10
		var endTime = jsonData['last_time'];
		$("#startTimeField").val(startTime);
		$("#endTimeField").val(endTime);
		if (refreshSensorSelect) {
			refreshSensorField(date, startTime, endTime);
		}
	});
}
/**
 * Refresh the set field
 */
function refreshSetField(date) {
	$.ajax({
		url: config.get('URL_PREFIX') +"/api/sets/fordate/"+ date
	}).done(function( jsonData ) {
		//console.log(jsonData);
		var setNumbers = jsonData['sets'];
		var options = "";
		setNumbers.map(function(d) {
			options += "<option value=\""+ d +"\">"+ d +"</option>"
		});
		if (setNumbers.length > 1) {
			options += "<option value=\"all\">All</option>" // add 'all' value
		}
		var setSelect = "<select id=\"setSelect\">" + options + "</select>";
		$('#setSelectPlaceholder').html(setSelect);
		refreshTimeFields(date, setNumbers[0], true);
		// event listener
		$('#setSelect').change(function() {
			var set = $("#setSelect").val();
			refreshTimeFields($("#dateSelect").val(), set, true);
		});
	});
}

/**
 * Create the date select field
 * @param jsonData The available dates (JSON)
 */
/*function createDateSelect(jsonData) {
	var dates = jsonData['dates'];
	//console.log(dates);
	
	var options = "";
	dates.map(function(d) {
		options += "<option value=\""+ d +"\">"+ d +"</option>"
	});
	var dateSelect = "<select id=\"dateSelect\">" + options + "</select>";
	$('#dateSelectPlaceholder').html(dateSelect);	
	refreshSetField(dates[0]);
	// event listener
	$('#dateSelect').change(function() {
		var date = $("#dateSelect").val();
		refreshSetField(date);
	});
}*/

function createPathSelectForData() {
	//console.log("createPathSelectForData()", missions);
	var dataGraphAvailable = false;
	var options = "";
	//var options = "<option value=0>Select a trajectory</option>"; // default value
	for (var i = 0; i < currentMissions.length; i++) {
		mission = currentMissions[i];
		var missionName = mission.date + " - " + mission.vehicle
		if (isCatamaranMission(mission)) {
			dataGraphAvailable = true;
			options += "<option value="+ mission.id +">"+ missionName +"</option>"
		}
	}
	
	var pathSelect = "<select id=\"pathSelect\">" + options + "</select>";
	$('#trajectorySelectPlaceholder').html(pathSelect);
	$('#pathSelect').change(function() {
		var missionId = $("#pathSelect").val();
		if (missionId > 0) {
			// get sensors for this trajectory
			getDevicesForMission(missionId);
		} else {
			$('#deviceSelectRow').hide();
		}
	});
	if (dataGraphAvailable) {
	    getDevicesForMission(currentMissions[0].id);
		$('#dataSelectPanel').show();
		toggleGraphPanel();
	}
}

function getDevicesForMission(missionId) {
	$.ajax({
		url: config.get('URL_PREFIX') +"/api/devices/formission/"+missionId
	}).done(function( jsonData ) {
		createDeviceSelectForData(jsonData, missionId);
	});
}

function createDeviceSelectForData(jsonData, missionId) {
	var devices = jsonData['devices'];
	//console.log("createDeviceSelectForData()", devices);
	var options = "<option value=\"altitude--0\">Altitude</option>";
	devices.map(function(d) {
		var deviceName, deviceId;
		if (d['id'] != 0) {
			deviceName = d['name'];
			deviceId = d['id'];
		} else {
			deviceName = "All "+d['name'];
			deviceId = "all";
		}
		
		var datatype = d['datatype'];
		// id is datatype--ID (ex: temperature--3)
		options += "<option value=\""+ datatype +"--"+ deviceId +"\">"+ deviceName +"</option>"
	});
	var pathSelect = "<select id=\"deviceSelect\">" + options + "</select>";
	$('#deviceSelectPlaceholder').html(pathSelect);
	$('#deviceSelect').change(function() {
		var deviceId = $("#deviceSelect").val();
		var deviceParts = deviceId.split('--');
		var datatype = deviceParts[0];
		var did = deviceParts[1];
		//console.log("load data for device: "+did+" and mission: "+missionId);
		getDeviceData(datatype, missionId, did);
	});
	$('#deviceSelectRow').show();
	/// show graph with altitude
	getDeviceData("altitude", $("#pathSelect").val(), 0);
}

function getDeviceData(datatype, missionId, deviceId) {
	var url = config.get('URL_PREFIX') +"/api/data?data_type="+datatype+"&mission_id="+missionId+"&device_id="+deviceId
	console.log(url);
	var graphHeight = $('#graphPanel').height();
	//console.log("graph height: "+graphHeight)
	var embeddedGraph = new GraphD3({
		containerElementId: 'dataGraphPlaceholder', 
		svgElementId: 'svgElement1',
		linkWithGeoData: true,
		heightContainer: graphHeight
	});
	mapLayerUtil.set({activeGraph: embeddedGraph});
	embeddedGraph.refreshSensorGraph(url, false);
	$('#dataGraphPlaceholder').show();
}

function refreshSensorField(date, startTime, endTime) {
	var dateNoDash = date.replace(/-/g, "");
	var stNoColumn = startTime.replace(/:/g, "");
	var etNoColumn = endTime.replace(/:/g, "");
	var startDate = dateNoDash+"-"+stNoColumn;
	var endDate = dateNoDash+"-"+etNoColumn;
	$.ajax({
		url: config.get('URL_PREFIX') +"/api/sensors/from/"+ startDate +"/to/"+ endDate
	}).done(function( jsonData ) {
		createSensorSelect(jsonData);
	});
}

/**
 * Create the sensor select to choose the sensor to display.
 * @param jsonData The list of available sensors
 */
function createSensorSelect(jsonData) {
	var sensors = jsonData['sensors'];
	//console.log(sensors);
	
	var options = "<option value=\"gps--0\">GPS only</option>"; // default value
	sensors.map(function(s) {
		var sensorName, sensorId;
		if (s['id'] != 0) {
			sensorName = s['name'];
			sensorId = s['id'];
		} else {
			sensorName = "All "+s['name'];
			sensorId = "all";
		}
		
		var datatype = s['datatype'];
		// id is datatype--ID (ex: temperature--3)
		options += "<option value=\""+ datatype +"--"+ sensorId +"\">"+ sensorName +"</option>"
	});
	var sensorSelect = "<select id=\"sensorSelect\">" + options + "</select>";
	$('#sensorSelectPlaceholder').html(sensorSelect);
	/*$('#sensorSelect').change(function() {
		var sid = $("#sensorSelect").val();
		var date = $("#dateSelect").val();
		refreshTimeFields(date, false);
	});*/
}

/**
 * Update the details info
 * @param containerElementId The id of the HTML element that contains the details
 * @param attr The data object {value: string, timestamp: string}
 * @param withDay Indicates if the day of the log has to be printed
 */
var updateInfoDiv = function(containerElementId, attr, withDay) {
	if (!mapLayerUtil.get('selected')) {
		//console.log("updateInfoDiv()");
		var dateStr = attr.timestamp;
		var dateArr = dateStr.split(' ');
		//var coordinates = attr.coordinate_swiss.split(",");
		var containerElementId = mapLayerUtil.getInfoContainerId();
		if (withDay) {
			$('#'+ containerElementId +' > #dataTimePlaceholder').html(dateArr[1]+"  ("+ dateArr[0] +")");
		} else {
			$('#'+ containerElementId +' > #dataTimePlaceholder').html(dateArr[1]);
		}
		var valueHtml = "";
		if (mapLayerUtil.has('activeGraph') && mapLayerUtil.get('activeGraph').sensorLogs.length == 1 && mapLayerUtil.get('activeGraph').get('withTooltip')) {
			valueHtml = "<b>Value:</b> "+ Number(attr.value).toFixed(3) +"<br/>";
		}
		$('#'+ containerElementId +' > #dataValuePlaceholder').html(valueHtml);
		//$('#'+ containerElementId +' > #eastValuePlaceholder').html(coordinates[0].substr(0, 6));
		//$('#'+ containerElementId +' > #northValuePlaceholder').html(coordinates[1].substr(0, 6));
		$('#'+ containerElementId +' > #speedValuePlaceholder').html(Number(attr.speed).toFixed(2));
	}
}

function toggleControlPanel() {
	//var slideDistance = 430;
	//console.log($("#graphPanel").position().left);
	if (!$("#controlPanelHideBtnPlaceholder").hasClass('rotated180')) {
		$("#controlPanelHideBtnPlaceholder").addClass('rotated180');
		$("#controlPanelHideBtnPlaceholder").animate({left: '+=95%'}, 1000)
		$("#controlPanel").animate({left: '+=100%'}, 1000)
	} else {
		$("#controlPanelHideBtnPlaceholder").removeClass('rotated180');
		$("#controlPanelHideBtnPlaceholder").animate({left: '-=95%'}, 1000)
		$("#controlPanel").animate({left: '-=100%'}, 1000)
	}
}

function toggleGraphPanel() {
	//console.log("toggleGraphPanel()");
	//console.log($("#graphPanelContainer").position().top);
	//console.log($("#mapPanel").height());
	var maxTop = $("#mapPanel").height() - 50;
	//if ($("#graphPanelContainer").position().top < maxTop) {
	if (!$("#graphPanelHideBtnPlaceholder").hasClass('rotated270')) {
	$("#graphPanelHideBtnPlaceholder").removeClass('rotated90');
		$("#graphPanelHideBtnPlaceholder").addClass('rotated270');
		$("#graphPanelContainer").animate({top: '+=25%'}, 1000)
	} else {
		$("#graphPanelHideBtnPlaceholder").removeClass('rotated270');
		$("#graphPanelHideBtnPlaceholder").addClass('rotated90');
		$("#graphPanelContainer").animate({top: '-=25%'}, 1000)
	}
}

function closeGraphPanel() {
	// if graph panel is visible (open), close it (slide down)
	if (!$("#graphPanelHideBtnPlaceholder").hasClass('rotated270')) {
		toggleGraphPanel();
	}
}


function zoomGraph(datatype, sid) {
	// disable zoom if multiple sets are selected, because zoomed graph is buggy
	if ($("#setSelect").val() == "all") {
		alert("Zoom is not available on multiple sets. Please select only one set.");
	} else {
		$("#graphZoomPanel").show();
		//console.log()
		if(zoomedGraph == undefined) {
			zoomedGraph = new GraphD3({
				containerElementId: 'graphZoomPlaceholder', 
				svgElementId: 'svgElement2',
				margin: {top: 50, right: 100, bottom: 50, left: 100},
				widthContainer: $("#graphZoomPlaceholder").width(),
				heightContainer: $("#graphZoomPlaceholder").height(),
				datatype: datatype,
				sensorId: sid,
				zoomable: true,
				ticksIntervalX: 5,
				nbTicksX: 10,
				nbTicksY: 14,
				linkWithGeoData: false,
				withTooltip: true
			});
		} else {
			zoomedGraph.set({datatype: datatype, sensorId: sid});
		}
		var dataJsonUrlZoomedGraph = dataJsonUrl + "&max_nb="+config.get('MAX_NB_DATA_POINTS_SINGLE_GRAPH')
		zoomedGraph.refreshSensorGraph(dataJsonUrlZoomedGraph);
		mapLayerUtil.set({activeGraph: zoomedGraph});
	}
}

function closeZoomGraph() {
	zoomedGraph.resetZoomBounds();
	mapLayerUtil.set({activeGraph: embeddedGraph});
	$("#graphZoomResetZoomBtn").hide();
	$('#graphZoomPanel').hide();
}