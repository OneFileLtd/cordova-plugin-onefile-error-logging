var exec = require('cordova/exec');
var ErrorLogging = function () { };

ErrorLogging.prototype.logError = function (config, success, error) {
	if (!config.endpoint) {
		error('Missing endpoint');
		return;
	}

	if (!config.headers) {
		error('Missing device info');
		return;
	} else if (!config.headers.userId) {
		error('Missing user id header');
		return;
	} else if (!config.headers.currentPlatform) {
		error('Missing current platform header');
		return;
	} else if (!config.headers.currentPlatformVersion) {
		error('Missing current platform version header');
		return;
	}

	if (!config.error) {
		error('Missing error');
		return;
	} else if (!config.error.name) {
		error('Missing error name');
		return;
	} else if (!config.error.message) {
		error('Missing error message');
		return;
	} else if (!config.error.cause) {
		error('Missing error cause');
		return;
	} else if (!config.error.stackTrace) {
		error('Missing error stack trace');
		return;
	}

	if (!config.currentUsername) {
		error('Missing error current username');
		return;
	}
	exec(success, error, "OnefileErrorLogging", "logError", [config]);
};

module.exports = new ErrorLogging();