(function () {
    "use strict";
    var OnefileSupporOnefileErrorLoggingProxytProxy = {
		onefileErrorLogging: function (win, fail, args, env) {
			try {
				if (!args[0]) {
					fail("Missing options");
				}
				var argConfig = args[0];
				win(argConfig);
			} catch (e) {
				fail(e);
			}
		}
	};
    require("cordova/exec/proxy").add("OnefileErrorLogging", OnefileErrorLoggingProxy);
})();