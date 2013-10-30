function GraphD3() {
    this.margin = {top: 20, right: 100, bottom: 30, left: 50};
    this.widthContainer = 620;
    this.heightContainer = 250;
    this.width = 0;
    this.height = 0;
    this.datatype = "";
    this.sensorId = "";
    this.missionId = "";
    this.ticksIntervalX = 15; // minutes
    this.nbTicksX = 4;
    this.nbTicksY = 7;
    this.containerElementId = 'graphPlaceholder';
    this.svgElementId = 'svgElement';
    this.linkWithGeoData = false;
    this.withTooltip = false;
    this.tooltipBox = {width: 150, height: 50, x: 0, y: 0};
    this.zoomable = false;
    this.nbZoomLines = 0;
    this.zoomLowerBound = "";
    this.zoomUpperBound = "";
    this.originalDataUrl = "";
}

/* initialize() function */
GraphD3.prototype.initialize = function() {
    //console.log("GraphD3 model - initialize()");
    //console.log(this);
    this.width = this.widthContainer - this.margin.left - this.margin.right;
    this.height = this.heightContainer - this.margin.top - this.margin.bottom;
    this.color = d3.scale.category10();
    this.x = d3.time.scale().range([0, this.width]);
    this.y = d3.scale.linear().range([this.height, 0]);
    //this.xAxis = d3.svg.axis().scale(this.x).orient("bottom").ticks(d3.time.minutes, this.get('ticksIntervalX'));
    //this.xAxis = d3.svg.axis().scale(this.x).orient("bottom").ticks(d3.time.minutes, 10);
    this.yAxis = d3.svg.axis().scale(this.y).orient("left").ticks(this.nbTicksY);
    this.line = d3.svg.line().interpolate("basis").x(function(d) { return this.x(d.date); }).y(function(d) { return this.y(d.value); });
    this.formatDate = d3.time.format("%Y-%m-%d %H:%M:%S.%L");
    this.formatDateUrl = d3.time.format("%Y%m%d-%H%M%S");
    this.parseDate = this.formatDate.parse;
    this.containerElement = $("#"+this.containerElementId);

    var self = this;
    this.containerElement.mousemove(function(event) {
        self.handleMouseOverGraph(event);
    });
    if (this.zoomable) {
        this.containerElement.mousedown(function(event) {
            self.handleMouseDown(event);
        });
        this.containerElement.mouseup(function(event) {
            self.handleMouseUp(event);
        });
    }
};

GraphD3.prototype.createSvgElement = function() {
    var svg = d3.select("#"+this.containerElementId).append("svg")
        .attr("width", this.width + this.margin.left + this.margin.right)
        .attr("height", this.height + this.margin.top + this.margin.bottom)
        .attr("id", this.svgElementId)
        .append("g")
        .attr("transform", "translate(" + this.margin.left + "," + this.margin.top + ")");
    return svg;
};

GraphD3.prototype.handleMouseDown = function(event) {
    // Warning: in case of multiple non-consecutive sets of measures (same day) the mouseX does not correspond to
    // hover line x position. Thus, line position is necessary to place the zoom bar, and mouseX is necessary to get the right data point
    var mouseXY = this.getMouseXY(event);
    var lineXPos = this.hoverLine.attr("x1");
    this.tooltip.classed("hide", true);
    //console.log("MouseDown graph - "+ mouseXY.mouseX);
    //console.log(this.get('nbZoomLines'));
    if (this.nbZoomLines < 2) {
        //console.log("line x: "+this.hoverLine.attr("x1")+", mouseX: "+mouseXY.mouseX);
        var v = this.getValueForPositionXFromData(mouseXY.mouseX, 0);
        var ts = this.formatDateUrl(this.formatDate.parse(v.timestamp))
        //console.log(ts);
        var svg = d3.select("#"+this.svgElementId);
        //console.log(svg);
        var lineGroup = svg.append("g")
            .attr("transform", "translate(" + this.margin.left + "," + this.margin.top + ")")
            .attr("class", "zoom-line");
        // add the line to the group
        this.leftZoomLine = lineGroup
            .append("svg:line")
            .attr("x1", lineXPos).attr("x2", lineXPos)
            .attr("y1", 0).attr("y2", this.height); // top to bottom
        this.nbZoomLines = this.nbZoomLines + 1;
        if (this.nbZoomLines == 2) {
            this.zoomUpperBound = ts;
            $("#graphZoomZoomBtn").show();
            var dataUrlZoom = config.URL_PREFIX +"/api/data?data_type="+ this.datatype;
            dataUrlZoom += "&from_date="+ this.zoomLowerBound +"&to_date="+ this.zoomUpperBound;
            dataUrlZoom += "&mission_id="+ this.missionId +"&device_id="+this.sensorId+"&max_nb="+config.MAX_NB_DATA_POINTS_SINGLE_GRAPH
            $("#graphZoomZoomBtn").attr("onclick", "zoomedGraph.refreshSensorGraph('"+ dataUrlZoom +"', true);");
        } else {
            this.zoomLowerBound = ts
        }
    } else {
        this.resetZoomBounds();
    }
};

GraphD3.prototype.resetZoomBounds = function() {
    d3.selectAll(".zoom-line").remove();
    this.nbZoomLines = 0;
    $("#graphZoomZoomBtn").hide();
};

GraphD3.prototype.handleMouseUp = function(event) {
    var mouseXY = this.getMouseXY(event);
    this.tooltip.classed("hide", false);
};
GraphD3.prototype.handleMouseOutGraph = function(event) {
    //console.log("MouseOut graph");
};

GraphD3.prototype.handleMouseOverGraph = function(event) {
    //console.log("MouseOver graph ");
    var mouseXY = this.getMouseXY(event)
    //console.log("MouseOver graph => clientX: " + event.clientX + ", clientY: " + event.clientY + ", offsetX: " + event.offsetX +", offsetY: " + event.offsetY + ", pageX: "+ event.pageX +", pageY: " + event.pageY);
    //console.log("MouseOver graph => offsetX: "+event.offsetX+", offsetY: "+event.offsetY);
    //console.log("MouseOver graph => mouseX: "+mouseX +", mouseY: "+mouseY+", w: "+width+", h: "+height);
    if(mouseXY.mouseX >= 0 && mouseXY.mouseX <= this.width && mouseXY.mouseY >= 0 && mouseXY.mouseY <= this.height) {
        if (this.hoverLine != undefined) {
            // show the hover line
            this.hoverLine.classed("hide", false);
        }

        if (this.sensorLogs != undefined) {
            var v = this.getValueForPositionXFromData(mouseXY.mouseX, 0);
            updateInfoDiv("tooltipText", v, false); // update the details (or tooltip) text, the position is updated later
            //console.log(v);
            if (this.linkWithGeoData){
                // pass by the data layer, highlight the point and then updates the position of the hover line in graph
                mapLayerUtil.highlightFeaturePoint(v.xPosFraction);
            } else {
                this.updateHoverLine(mouseXY.mouseX)
            }
            if (this.withTooltip) {
                var yPosTt = mouseXY.mouseY - this.tooltipBox.height - 10 // deduce 10 to have the tooltip above the cursor
                var tt = this.tooltipBox;
                tt.y = yPosTt;
                this.tooltipBox = tt
            }
        }
    } else {
        // proactively act as if we've left the area since we're out of the bounds we want
        this.handleMouseOutGraph(event)
    }
};

/**
 * Get X and Y position of mouse relative to top left corner of graph element
 */
GraphD3.prototype.getMouseXY = function(event) {
    var graphXOffset = this.margin.left;
    var graphYOffset = this.margin.top;
    var offsetX, offsetY;
    if(event.offsetX==undefined) {
        // Firefox
        offsetX = event.pageX-this.containerElement.offset().left;
        offsetY = event.pageY-this.containerElement.offset().top;
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

GraphD3.prototype.getValueForPositionXFromData = function(xPosition, dataSeriesIndex) {
    var d = this.sensorLogs[dataSeriesIndex].values;
    //console.log("d.length: "+d.length+", graph width: "+this.get('width'));
    var xpf = xPosition / this.width;
    // The date we're given is interpolated so we have to round off to get the nearest
    // index in the data array for the xValue we're given.
    // Once we have the index, we then retrieve the data from the d[] array
    var index = Math.round(d.length * xpf);

    if(index >= d.length) {
        index = d.length-1;
    }

    //console.log("xPos: "+ xPosition +", index: "+index);
    var v = d[index];

    //return {value: v.value, timestamp: this.formatDate(v.date), xPosFraction: xpf, coordinate_swiss: v.coordinate_swiss, speed: v.speed};
    return {value: v.value, timestamp: this.formatDate(v.date), xPosFraction: xpf};
};

/**
 * Update the X position of hover line and tooltip (if visible)
 * @param xPos The x position of the line
 */
GraphD3.prototype.updateHoverLine = function(xPos) {
    this.hoverLine.classed("hide", false);
    // set position of hoverLine
    this.hoverLine.attr("x1", xPos).attr("x2", xPos)
    if (this.withTooltip) {
        this.tooltip.classed("hide", false);
        var xPosTt = xPos - this.tooltipBox.width / 2
        this.tooltip.attr("transform", "translate("+ xPosTt  +","+ this.tooltipBox.y +")");
    }
};

/**
 * Refresh the plot with new data - called from panelUtil.js
 * @param url The url from whom to get the new data
 * @param zoomed Indicates if the current graph is the zoomed one
 */
GraphD3.prototype.refreshSensorGraph = function(url, zoomed) {
    //console.log("refreshSensorGraph() - "+url);
    var self = this;
    this.resetZoomBounds();
    $('#'+this.svgElementId).remove(); // remove SVG element if already present
    var svg = this.createSvgElement();
    d3.json(url, function(error, data) {
      //console.log(error);
      //console.log(data);
      var sensorNames = data.logs.map(function(serie) {return serie["sensor"]});
      self.color.domain(sensorNames);
      data.logs.forEach(function(serie) {
        serie.values.forEach(function(d) {
          d.date = self.parseDate(d.timestamp); // parse timestamp to date (D3 takes date in X axis)
        })
      });

      self.sensorLogs = self.color.domain().map(function(name, ind) {
        //console.log("name: "+name+", index: "+ind);
        //console.log(data.logs[ind].values);
        return {
          name: name,
          values: data.logs[ind].values
          /*values: data.logs[ind].values.map(function(d) {
            //console.log("[graphD3.js - 242]", d);
            return {date: self.parseDate(d.timestamp), logValue: d.value};
            //return {date: self.parseDate(d.timestamp), logValue: d.value, coordinate_swiss: d.coordinate_swiss, speed: d.speed};
          })*/
        };
      });

      var dataSerie = self.sensorLogs[0].values;
      var firstLogTime = dataSerie[0].date.getTime();
      var lastLogTime = dataSerie[dataSerie.length-1].date.getTime();
      var diff = Math.round((lastLogTime - firstLogTime) / 1000);
      //console.log(diff)
      var tickIntervalSec = Math.round(diff/self.nbTicksX);
      //console.log("tickInterval: "+tickIntervalSec+"s");
      if (tickIntervalSec >= 60) {
        var tickIntervalMinute = Math.round(tickIntervalSec / 60);
        self.xAxis = d3.svg.axis().scale(self.x).orient("bottom").ticks(d3.time.minutes, tickIntervalMinute);
      } else if (tickIntervalSec >= 30) {
        self.xAxis = d3.svg.axis().scale(self.x).orient("bottom").ticks(d3.time.seconds, 30);
      } else if (tickIntervalSec >= 10) {
        self.xAxis = d3.svg.axis().scale(self.x).orient("bottom").ticks(d3.time.seconds, 10);
      } else {
        self.xAxis = d3.svg.axis().scale(self.x).orient("bottom").ticks(d3.time.seconds, tickIntervalSec);
      }

      self.x.domain(d3.extent(self.sensorLogs[0].values, function(d) { return d.date; }));

      self.y.domain([
        d3.min(self.sensorLogs, function(c) { return d3.min(c.values, function(v) { return v.value; }); }),
        d3.max(self.sensorLogs, function(c) { return d3.max(c.values, function(v) { return v.value; }); })
      ]);

      svg.append("g")
          .attr("class", "x axis")
          .attr("transform", "translate(0," + self.height + ")")
          .call(self.xAxis);

      svg.append("g")
          .attr("class", "y axis")
          .call(self.yAxis)
          .append("text")
          .attr("transform", "rotate(-90)")
          .attr("y", 6)
          .attr("dy", ".71em")
          .style("text-anchor", "end")
          //.text("Temperature (°C)");

      var sl = svg.selectAll(".sensorLog")
        .data(self.sensorLogs)
        .enter().append("g")
        .attr("class", "sensorLog");

      sl.append("path")
          .attr("class", "line")
          .attr("d", function(d) { return self.line(d.values); })
          .style("stroke", function(d) { return self.color(d.name); });
          //.on('mouseover', function(d, i) { self.handleMouseOverLine(d, i); });

      sl.append("text")
          .datum(function(d) { return {name: d.name, value: d.values[d.values.length - 1]}; })
          .attr("transform", function(d) { return "translate(" + self.x(d.value.date) + "," + self.y(d.value.value) + ")"; })
          .attr("x", 3)
          .attr("dy", ".35em")
          .style("stroke", "black")
          .text(function(d) { return d.name; });

      // add a 'hover' line that we'll show as a user moves their mouse (or finger)
      // so we can use it to show detailed values of each line
      var hoverLineGroup = svg.append("g")
        .attr("class", "hover-line");
      // add the line to the group
      self.hoverLine = hoverLineGroup
        .append("svg:line")
        .attr("x1", 10).attr("x2", 10) // vertical line so same value on each
        .attr("y1", 0).attr("y2", self.height); // top to bottom

      // hide it by default
      self.hoverLine.classed("hide", true);

      if (self.withTooltip) {
        self.tooltip = svg.append("g")
            .attr("class", "dataTooltip")
            .attr("transform", "translate(0,0)");
        rect = self.tooltip.append('rect')
            .attr('width', 150)
            .attr('height', 50)
            .style('fill', 'none')
            .attr('stroke', 'white')
        text = self.tooltip.append('foreignObject')
            .attr('width', 150)
            .attr('height', 50)
            .append("xhtml:body")
            .html('<div id="tooltipText"><b>Time:</b> <span id="dataTimePlaceholder"></span><br/><span id="dataValuePlaceholder"></span></div>')
        // hide it by default
        self.tooltip.classed("hide", true);
      }
      if (self.linkWithGeoData) {
        $('#graphPanel').show();
        $('#loadingGifPlaceholder').hide();
      }
      if (zoomed) {
        $("#graphZoomResetZoomBtn").show();
        $("#graphZoomResetZoomBtn").attr("onclick", "zoomedGraph.resetZoom();");
      } else {
        self.originalDataUrl = url;
      }
    });
};

GraphD3.prototype.resetZoom = function() {
    this.refreshSensorGraph(this.originalDataUrl, false);
    $("#graphZoomResetZoomBtn").hide();
};