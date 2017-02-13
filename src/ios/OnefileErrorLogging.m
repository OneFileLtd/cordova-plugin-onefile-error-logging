#import "OnefileErrorLogging.h"
#import "database.h"
#import "sendrequest.h"

#define MAX_NUMBER_OF_ERRORS 1000

/************************************************************************************************************
 *      OnefileErrorLogging - Initialisation point of the plugin.
 ************************************************************************************************************/
@implementation OnefileErrorLogging

@synthesize inUse = _inUse;

- (void)pluginInitialize
{
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
    int processMaxErrors = MAX_NUMBER_OF_ERRORS;
    int status = 0;
    if ([config isKindOfClass:[NSNull class]]) {
        config = [NSDictionary dictionary];
    }
    do {
        if([config count] > 0) {
            int ID = [[config objectForKey:@"ID"] intValue];
            response = [SendRequest makeRequest: config];
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
                processMaxErrors--;
                if(processMaxErrors > 0)
                    config = [database getErrorFromDB:db];
                [database closeDatabase: db];
            }
            else
            {
                sqlite3 *db = [database createOpenDatabase];
                [database createTable:db];
                if(ID == 0) {
                    [database insertErrorWithDB:db config:config];
                }
                [database closeDatabase: db];
            }
        }
    } while(processMaxErrors > 0 && status > 0 && config && [config count] > 0);

    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary: response];
    [self.commandDelegate sendPluginResult:result callbackId:callbackId];
}
@end
