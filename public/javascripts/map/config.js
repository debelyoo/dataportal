var Config = Backbone.Model.extend({
	defaults: {
		URL_PREFIX: "/playproxy",
		MAX_NB_DATA_POINTS_ON_MAP: 2000,
		MAX_NB_DATA_POINTS_SINGLE_GRAPH: 2500,
		ULM_DATE_CLASSNAME: 'ulmDate',
		CAT_DATE_CLASSNAME: 'catDate',
		ULM_CAT_DATE_CLASSNAME: 'ulmCatDate'
	},
	initialize: function() {
	}
});
