//
//  database.h
//  testErrorLogging
//
//  Created by martin on 30/10/2016.
//  Copyright © 2016 martin. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <sqlite3.h>

// Database Buffer size.
#define BUFFER_SIZE 1024
#define DATABASE_DATE_TIME_FORMAT           @"yyyy-MM-dd'T'HH:mm:ssZ"
#define DATE_GLOBAL_LOCALE                  @"en_US_POSIX"

@interface database : NSObject
{
}
+ (NSString *)validateStringForUTF8: (char *)stringUTF;

+ (sqlite3 *)createOpenDatabase;
+ (void)closeDatabase:(sqlite3 *)db;

+ (void)createTable:(sqlite3 *)db;

+ (void)deleteErrorFromDB: (sqlite3 *)db usingID:(int)id;
+ (void)deleteErrorTable:(sqlite3 *)db;
+ (int)queryUserVersion:(sqlite3 *)db;

+ (void)insertErrorWithDB:(sqlite3 *)db config:(NSDictionary *)config;
+ (NSDictionary *)getErrorFromDB: (sqlite3 *)db;
@end
