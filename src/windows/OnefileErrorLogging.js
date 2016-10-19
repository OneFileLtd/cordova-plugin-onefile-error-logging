(function () {
    "use strict";
    var OnefileErrorLoggingProxy = {
		logError: function (win, fail, args, env) {
			try {
				if (!args[0]) {
					fail("Missing options");
				}
				var argConfig = args[0];
				logError(argConfig)
					.done(success, fail);
			} catch (e) {
				fail(e);
			}

			function success(result) {
				win(result);
			}
		}
    };

    function logError(config) {
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
    }


    require("cordova/exec/proxy").add("OnefileErrorLogging", OnefileErrorLoggingProxy);
})();