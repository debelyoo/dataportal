/**
 * Get size of associative array
 * Extend JS Object with a size() method
 */
Object.size = function(obj) {
    var size = 0, key;
    for (key in obj) {
        if (obj.hasOwnProperty(key)) size++;
    }
    return size;
};

/**
 * Get first element of associative array
 */
function first(obj) {
    for (var a in obj) return obj[a];
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