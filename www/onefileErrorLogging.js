var exec = require('cordova/exec');
var ErrorLogging = function () { };

ErrorLogging.prototype.logError = function (config, success, error) {
	if (!config.endpoint) {
		error('Missing endpoint');
		return;
	}
	if (!config.device) {
		error('Missing device info');
		return;
	}
	if (!config.files) {
		error('Missing files');
		return;
	}
	if (!config.error) {
		error('Missing error');
		return;
	}
	exec(success, error, "OnefileErrorLogging", "logError", [config]);
};

module.exports = new ErrorLogging();