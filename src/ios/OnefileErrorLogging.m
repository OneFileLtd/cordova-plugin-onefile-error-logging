#import "OnefileErrorLogging.h"
#import "database.h"
#import "sendrequest.h"

#define MAX_NUMBER_OF_ERRORS 1000
#define SYNC_n_EVERY_SECONDS (5.0 * 60.0)
#define TIME_BETWEEN_DUPLICATE_ERRORS_IN_SECONDS (20.0)

/************************************************************************************************************
 *      OnefileErrorLogging - Initialisation point of the plugin.
 ************************************************************************************************************/
@implementation OnefileErrorLogging

@synthesize inUse = _inUse;
@synthesize allowSync = _allowSync;

- (void)pluginInitialize
{
    self.inUse = NO;
    self.allowSync = YES;
}

// ----------------------------------
// -- ENTRY POINT FROM JAVA SCRIPT --
// ----------------------------------
- (void)logError:(CDVInvokedUrlCommand*)command
{
    NSString *callbackId = command.callbackId;
    NSDictionary *config = [command argumentAtIndex:0];
    if ([config isKindOfClass: [NSNull class]]) {
        config = [NSDictionary dictionary];
    }
    [self insertNewError:config];
    
    NSDictionary *response = @{
            @"status": [NSNumber numberWithInteger: 200]
    };
    CDVPluginResult *result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK messageAsDictionary: response];
    [self.commandDelegate sendPluginResult: result callbackId: callbackId];
}

- (void)insertNewError:(NSDictionary *)error
{
    sqlite3 *db = [database createOpenDatabase];
    [database createTable:db];
    NSDictionary *lastErrorLogged = [database getLastErrorFromDB:db];
    NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
    [dateFormatter setDateFormat: DATABASE_DATE_TIME_FORMAT];
    [dateFormatter setLocale: [NSLocale localeWithLocaleIdentifier: DATE_GLOBAL_LOCALE]];
    [dateFormatter setTimeZone: [NSTimeZone timeZoneWithName: @"UTC"]];
    NSDate *lastDate = [dateFormatter dateFromString: [lastErrorLogged objectForKey: @"dateLogged"]];
    if (lastDate) {
        NSDate *currentDate = [NSDate date];
        NSCalendar *cal = [[NSCalendar alloc] initWithCalendarIdentifier: NSCalendarIdentifierGregorian];
        NSDateComponents *diff = [cal components: NSCalendarUnitSecond fromDate: lastDate toDate: currentDate options: 0];
        if((int)[diff second] > TIME_BETWEEN_DUPLICATE_ERRORS_IN_SECONDS) {
            NSDictionary *last = [lastErrorLogged objectForKey: @"error"];
            NSDictionary *current =  [error objectForKey: @"error"];
            if(!([[current objectForKey: @"name"] isEqual: [last objectForKey: @"name"]] &&
               [[current objectForKey: @"message"] isEqual: [last objectForKey: @"message"]] &&
               ![[current objectForKey: @"cause"] isEqual: [last objectForKey: @"cause"]]))
            {
                [database insertErrorWithDB: db config: error];
            }
        }
    } else {
        [database insertErrorWithDB: db config: error];
    }
    [database closeDatabase: db];
    self.allowSync = YES;
    [self scheduleUploads];
}

- (void)uploadErrors
{
    NSDictionary *response;
    int processMaxErrors = MAX_NUMBER_OF_ERRORS;
    int status = 0;
    NSDictionary *oldestError = [self getOldestError];
    do {
        if([oldestError count] > 0) {
            int ID = [[oldestError objectForKey: @"ID"] intValue];
            if(ID > 0) {
                response = [SendRequest makeRequest: oldestError];
                if([[response objectForKey: @"status"] isKindOfClass: [NSNumber class]])
                    status = [[response objectForKey: @"status"] intValue];
                if(status == 200) {
                    sqlite3 *db = [database createOpenDatabase];
                    [database deleteErrorFromDB: db usingID: ID];
                    [database closeDatabase: db];
                    processMaxErrors--;
                    if(processMaxErrors > 0) {
                        oldestError = [self getOldestError];
                    }
                } else {
                    processMaxErrors = 0;
                    self.allowSync = NO;
                }
            }
        }
    } while(processMaxErrors > 0 && status > 0 && oldestError && [oldestError count] > 0);
    [self scheduleUploads];
}

- (void)scheduleUploads
{
    if(self.allowSync) {
        self.allowSync = NO;
        NSDictionary *oldestError = [self getOldestError];
        int ID = [[oldestError objectForKey: @"ID"] intValue];
        if (ID > 0) {
            [NSTimer scheduledTimerWithTimeInterval: SYNC_n_EVERY_SECONDS target: self selector: @selector(uploadErrors) userInfo: nil repeats: NO];
        }
    }
}

- (NSDictionary *)getOldestError
{
    sqlite3 *db = [database createOpenDatabase];
    NSDictionary *oldestError = [database getOldestErrorFromDB: db];
    [database closeDatabase: db];
    return oldestError;
}
@end
