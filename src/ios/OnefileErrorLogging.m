#import "OnefileErrorLogging.h"
#import "database.h"
#import "sendrequest.h"

/************************************************************************************************************
 *      OnefileErrorLogging - Initialisation point of the plugin.
 ************************************************************************************************************/
@implementation OnefileErrorLogging

@synthesize inUse = _inUse;

- (void)pluginInitialize
{
    NSLog(@"OnefileErrorLogging - pluginInitialize");
    self.inUse = NO;
}

// ----------------------------------
// -- ENTRY POINT FROM JAVA SCRIPT --
// ----------------------------------
- (void)logError:(CDVInvokedUrlCommand*)command
{
    NSString *callbackId = command.callbackId;
    NSDictionary *config = [command argumentAtIndex:0];
    NSDictionary *response;

    if ([config isKindOfClass:[NSNull class]]) {
        config = [NSDictionary dictionary];
    }

    do {
        if([config count] > 0) {
            int ID = [[config objectForKey:@"ID"] intValue];

            response = [SendRequest makeRequest: config];
            int status;
            if([[response objectForKey:@"status"] isKindOfClass: [NSNumber class]])
                status = [[response objectForKey:@"status"] intValue];
            else
                status = 0;
            if(status == 200) {
                sqlite3 *db = [database createOpenDatabase];
                [database createTable:db];
                if(ID > 0) {
                    [database deleteErrorFromDB:db usingID: ID];
                } else {
                }
                config = [database getErrorFromDB:db];
                [database closeDatabase: db];
            }
            else
            {
                sqlite3 *db = [database createOpenDatabase];
                [database createTable:db];
                if(ID > 0) {
                } else {
                    [database insertErrorWithDB:db config:config];
                }
                config = [database getErrorFromDB:db];
                [database closeDatabase: db];
            }
        }
    } while(config && [config count] > 0);

    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary: response];
    [self.commandDelegate sendPluginResult:result callbackId:callbackId];
}
@end
