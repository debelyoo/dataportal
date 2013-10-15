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