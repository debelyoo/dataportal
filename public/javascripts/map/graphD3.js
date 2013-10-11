var GraphD3 = Backbone.Model.extend({
		defaults: { 
			margin: {top: 20, right: 100, bottom: 30, left: 50},
			widthContainer: 620,
			heightContainer: 250,
			width: 0, 
			height: 0,
			datatype: "",
			sensorId: "",
			ticksIntervalX: 15, // minutes
			nbTicksX: 4,
			nbTicksY: 7,
			containerElementId: 'graphPlaceholder',
			svgElementId: 'svgElement',
			linkWithGeoData: false,
			withTooltip: false,
			tooltip: {width: 150, height: 50, x: 0, y: 0},
			zoomable: false,
			nbZoomLines: 0,
			zoomLowerBound: "",
			zoomUpperBound: "",
			originalDataUrl: ""
		}, 
		initialize: function() { 
			//console.log("GraphD3 model - initialize()"); 
			//console.log(this);
			this.set({width: this.get('widthContainer') - this.get('margin').left - this.get('margin').right});
			this.set({height: this.get('heightContainer') - this.get('margin').top - this.get('margin').bottom});
			this.color = d3.scale.category10();
			this.x = d3.time.scale().range([0, this.get('width')]);
			this.y = d3.scale.linear().range([this.get('height'), 0]);
			//this.xAxis = d3.svg.axis().scale(this.x).orient("bottom").ticks(d3.time.minutes, this.get('ticksIntervalX'));
			//this.xAxis = d3.svg.axis().scale(this.x).orient("bottom").ticks(d3.time.minutes, 10);
			this.yAxis = d3.svg.axis().scale(this.y).orient("left").ticks(this.get('nbTicksY'));
			this.line = d3.svg.line().interpolate("basis").x(function(d) { return this.x(d.date); }).y(function(d) { return this.y(d.logValue); });
			this.formatDate = d3.time.format("%Y-%m-%d %H:%M:%S.%L")
			this.formatDateUrl = d3.time.format("%Y%m%d-%H%M%S")
			this.parseDate = this.formatDate.parse
			this.containerElement = $("#"+this.get('containerElementId'))
			
			var self = this;
			this.containerElement.mousemove(function(event) {
				self.handleMouseOverGraph(event);
			});
			if (this.get('zoomable')) {
				this.containerElement.mousedown(function(event) {
					self.handleMouseDown(event);
				});
				this.containerElement.mouseup(function(event) {
					self.handleMouseUp(event);
				});
			}
		},
		createSvgElement: function() {
			var svg = d3.select("#"+this.get('containerElementId')).append("svg")
				.attr("width", this.get('width') + this.get('margin').left + this.get('margin').right)
				.attr("height", this.get('height') + this.get('margin').top + this.get('margin').bottom)
				.attr("id", this.get('svgElementId'))
				.append("g")
				.attr("transform", "translate(" + this.get('margin').left + "," + this.get('margin').top + ")");
			return svg;
		},
		handleMouseDown: function(event) {
			// Warning: in case of multiple non-consecutive sets of measures (same day) the mouseX does not correspond to 
			// hover line x position. Thus, line position is necessary to place the zoom bar, and mouseX is necessary to get the right data point
			var mouseXY = this.getMouseXY(event);
			var lineXPos = this.hoverLine.attr("x1");
			this.tooltip.classed("hide", true);
			//console.log("MouseDown graph - "+ mouseXY.mouseX);
			//console.log(this.get('nbZoomLines'));
			if (this.get('nbZoomLines') < 2) {
				//console.log("line x: "+this.hoverLine.attr("x1")+", mouseX: "+mouseXY.mouseX);
				var v = this.getValueForPositionXFromData(mouseXY.mouseX, 0);
				var ts = this.formatDateUrl(this.formatDate.parse(v.timestamp))
				//console.log(ts);
				var svg = d3.select("#"+this.get('svgElementId'));
				//console.log(svg);
				var lineGroup = svg.append("g")
					.attr("transform", "translate(" + this.get('margin').left + "," + this.get('margin').top + ")")
					.attr("class", "zoom-line");
				// add the line to the group
				this.leftZoomLine = lineGroup
					.append("svg:line")
					.attr("x1", lineXPos).attr("x2", lineXPos)
					.attr("y1", 0).attr("y2", this.get('height')); // top to bottom
				this.set({nbZoomLines: this.get('nbZoomLines') + 1});
				if (this.get('nbZoomLines') == 2) {
					this.set({zoomUpperBound: ts})
					$("#graphZoomZoomBtn").show();
					var dataUrlZoom = config.get('URL_PREFIX') +"/api/data?data_type="+ this.get('datatype') +"&from_date="+ this.get('zoomLowerBound') +"&to_date="+ this.get('zoomUpperBound') +"&sensor_id="+this.get('sensorId')+"&max_nb="+config.get('MAX_NB_DATA_POINTS_SINGLE_GRAPH');
					$("#graphZoomZoomBtn").attr("onclick", "zoomedGraph.refreshSensorGraph('"+ dataUrlZoom +"', true);");
				} else {
					this.set({zoomLowerBound: ts})
				}
			} else {
				this.resetZoomBounds();
			}
		},
		resetZoomBounds: function() {
			d3.selectAll(".zoom-line").remove();
			this.set({nbZoomLines: 0});
			$("#graphZoomZoomBtn").hide();
		},
		handleMouseUp: function(event) {
			var mouseXY = this.getMouseXY(event);
			this.tooltip.classed("hide", false);
			//console.log("MouseUp graph - "+ mouseXY.mouseX);
			//event.stopPropagation();
		},
		handleMouseOutGraph: function(event) {
			//console.log("MouseOut graph");
		},
		/* Not really useful since we don't have a reference on a data point but only the whole data array *
		handleMouseOverLine: function(data, dataSerieIndex) {
			console.log(data);
			console.log("---");
		},*/
		handleMouseOverGraph: function(event) {
			//console.log("MouseOver graph ");
			var mouseXY = this.getMouseXY(event)
			//console.log("MouseOver graph => clientX: " + event.clientX + ", clientY: " + event.clientY + ", offsetX: " + event.offsetX +", offsetY: " + event.offsetY + ", pageX: "+ event.pageX +", pageY: " + event.pageY);
			//console.log("MouseOver graph => offsetX: "+event.offsetX+", offsetY: "+event.offsetY);
			//console.log("MouseOver graph => mouseX: "+mouseX +", mouseY: "+mouseY+", w: "+width+", h: "+height);
			if(mouseXY.mouseX >= 0 && mouseXY.mouseX <= this.get('width') && mouseXY.mouseY >= 0 && mouseXY.mouseY <= this.get('height')) {
				if (this.hoverLine != undefined) {
					// show the hover line
					this.hoverLine.classed("hide", false);
				}
				
				if (this.sensorLogs != undefined) {
					var v = this.getValueForPositionXFromData(mouseXY.mouseX, 0);
					updateInfoDiv("tooltipText", v, false); // update the details (or tooltip) text, the position is updated later
					//console.log(v);
					if (this.get('linkWithGeoData')){
						// pass by the data layer, highlight the point and then updates the position of the hover line in graph
						mapLayerUtil.highlightFeaturePoint(v.xPosFraction);
					} else {
						this.updateHoverLine(mouseXY.mouseX)
					}
					if (this.get('withTooltip')) {
						var yPosTt = mouseXY.mouseY - this.get('tooltip').height - 10 // deduce 10 to have the tooltip above the cursor
						var tt = this.get('tooltip');
						tt.y = yPosTt;
						this.set({tooltip: tt})
					}
				}
			} else {
				// proactively act as if we've left the area since we're out of the bounds we want
				this.handleMouseOutGraph(event)
			}
		},
		/**
		 * Get X and Y position of mouse relative to top left corner of graph element
		 */
		getMouseXY: function(event) {
			var graphXOffset = this.get('margin').left;
			var graphYOffset = this.get('margin').top;
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
		},
		
		getValueForPositionXFromData: function(xPosition, dataSeriesIndex) {
			var d = this.sensorLogs[dataSeriesIndex].values;
			//console.log("d.length: "+d.length+", graph width: "+this.get('width'));
			var xpf = xPosition / this.get('width');
			// The date we're given is interpolated so we have to round off to get the nearest
			// index in the data array for the xValue we're given.
			// Once we have the index, we then retrieve the data from the d[] array
			var index = Math.round(d.length * xpf);

			if(index >= d.length) {
				index = d.length-1;
			}
			
			//console.log("xPos: "+ xPosition +", index: "+index);
			var v = d[index];

			return {value: v.logValue, timestamp: this.formatDate(v.date), xPosFraction: xpf, coordinate_swiss: v.coordinate_swiss, speed: v.speed};
		},
		
		/**
		 * Update the X position of hover line and tooltip (if visible)
		 * @param xPos The x position of the line
		 */
		updateHoverLine: function(xPos) {
			this.hoverLine.classed("hide", false);
			// set position of hoverLine
			this.hoverLine.attr("x1", xPos).attr("x2", xPos)
			if (this.get('withTooltip')) {
				this.tooltip.classed("hide", false);
				var xPosTt = xPos - this.get('tooltip').width / 2
				//var yPosTt = yPos - this.get('tooltip').height - 10 // deduce 10 to have the tooltip above the cursor
				this.tooltip.attr("transform", "translate("+ xPosTt  +","+ this.get('tooltip').y +")");
			}
		},
		
		refreshSensorGraph: function(url, zoomed) {		
			//console.log("refreshSensorGraph() - "+url);
			var self = this;
			this.resetZoomBounds();
			$('#'+this.get('svgElementId')).remove(); // remove SVG element if already present
			var svg = this.createSvgElement();
			d3.json(url, function(error, data) {
			  //console.log(error);
			  //console.log(data);
			  var sensorNames = data.logs.map(function(serie) {return serie["sensor"]});
			  self.color.domain(sensorNames);
			  data.logs.forEach(function(serie) {
				serie.values.forEach(function(d) {
				  d.date = self.parseDate(d.timestamp);
				})
			  });

			  self.sensorLogs = self.color.domain().map(function(name, ind) {
				//console.log("name: "+name+", index: "+ind);
				//console.log(data.logs[ind].values);
				/* TEST to keep order
				var valueArray = new Array();
				for (var i = 0; i < data.logs[ind].values.length; i++) {
                    var d = data.logs[ind].values[i];
                    //console.log(d.id);
                    valueArray.push({date: d.date, logValue: d.value, coordinate_swiss: d.coordinate_swiss, speed: d.speed})
				}
				return {name: name, values: valueArray};
				*/
				return {
				  name: name,
				  values: data.logs[ind].values.map(function(d) {
					//console.log("[graphD3.js - 233]", d.date);
					return {date: d.date, logValue: d.value, coordinate_swiss: d.coordinate_swiss, speed: d.speed};
					//return {date: d.date, temperature: +d[name]};
				  })
				};
			  });
			  
			  var dataSerie = self.sensorLogs[0].values;
			  var firstLogTime = dataSerie[0].date.getTime();
			  var lastLogTime = dataSerie[dataSerie.length-1].date.getTime();
			  var diff = Math.round((lastLogTime - firstLogTime) / 1000);
			  //console.log(diff)
			  var tickIntervalSec = Math.round(diff/self.get('nbTicksX'));
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
				
			  self.x.domain(d3.extent(data.logs[0].values, function(d) { return d.date; }));

			  self.y.domain([
				d3.min(self.sensorLogs, function(c) { return d3.min(c.values, function(v) { return v.logValue; }); }),
				d3.max(self.sensorLogs, function(c) { return d3.max(c.values, function(v) { return v.logValue; }); })
			  ]);

			  svg.append("g")
				  .attr("class", "x axis")
				  .attr("transform", "translate(0," + self.get('height') + ")")
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
				  .attr("transform", function(d) { return "translate(" + self.x(d.value.date) + "," + self.y(d.value.logValue) + ")"; })
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
				.attr("y1", 0).attr("y2", self.get('height')); // top to bottom	
						
			  // hide it by default
			  self.hoverLine.classed("hide", true);
			  
			  if (self.get('withTooltip')) {
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
			  if (self.get('linkWithGeoData')) {
				$('#graphPanel').show();
				$('#loadingGifPlaceholder').hide();
			  }
			  if (zoomed) {
				$("#graphZoomResetZoomBtn").show();
				$("#graphZoomResetZoomBtn").attr("onclick", "zoomedGraph.resetZoom();");
			  } else {
				self.set({originalDataUrl: url});
			  }
			});
		},
		resetZoom: function() {
			this.refreshSensorGraph(this.get('originalDataUrl', false))
			$("#graphZoomResetZoomBtn").hide();
		}
});