(function () {
	"use strict";
	var winNetConn = Windows.Networking.Connectivity;
	var networkInfo = winNetConn.NetworkInformation;

	var OnefileErrorLoggingProxy = {
		logError: function (win, fail, args, env) {
			try {
				if (!args[0]) {
					fail("Missing options");
				}
				var argConfig = args[0];
				networkInfo.addEventListener("networkstatuschanged", tryUploadErrors);
				logErrorToServer(argConfig)
					.then(success, saveErrorToDb)
					.done(success, fail);
			} catch (e) {
				fail(e);
			}

			function success(result) {
				win(result);
			}
		}
	};

	function saveErrorToDb(config) {
		// save error to database
	}

	function tryUploadErrors() {
		var profile = networkInfo.getInternetConnectionProfile();

		if (profile) {
			var conLevel = profile.getNetworkConnectivityLevel();
			var interfaceType = profile.networkAdapter.ianaInterfaceType;

			if (conLevel === Windows.Networking.Connectivity.NetworkConnectivityLevel.internetAccess) {
				// get next error from the database
				// log the error with the server
			}
		}
	}

	function logErrorToServer(config) {
		var profile = networkInfo.getInternetConnectionProfile();

		if (profile) {
			var conLevel = profile.getNetworkConnectivityLevel();
			var interfaceType = profile.networkAdapter.ianaInterfaceType;

			if (conLevel === Windows.Networking.Connectivity.NetworkConnectivityLevel.internetAccess) {
				var options = {
					url: config.endpoint,
					type: 'POST',
					headers: {
						'X-UserID': config.headers.userId,
						'X-Current-Platform': config.headers.currentPlatform,
						'X-Current-Platform-Version': config.headers.currentPlatformVersion,
						"Content-type": "application/json"
					},
					data: JSON.stringify({
						'Name': config.error.name,
						'Message': config.error.message,
						'Cause': config.error.cause,
						'StackTrace': config.error.stackTrace,
						'CurrentUsername': config.currentUsername
					})
				}
				return WinJS.xhr(options);
			} else {
				return WinJS.Promise.wrapError(config);
			}
		} else {
			return WinJS.Promise.wrapError(config);
		}
	}

	require("cordova/exec/proxy").add("OnefileErrorLogging", OnefileErrorLoggingProxy);
})();