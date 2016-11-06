//
//  database.m
//  testErrorLogging
//
//  Created by martin on 30/10/2016.
//  Copyright © 2016 martin. All rights reserved.
//

#import "database.h"

@implementation database
/*
 *
 */
+ (sqlite3 *)createOpenDatabase
{
    sqlite3 *db = NULL;
    NSString *databaseName = @"DATABASE.sqlite";
    NSArray *documentPaths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *documentsDir = [documentPaths objectAtIndex:0];
    NSString *databasePath = [documentsDir stringByAppendingPathComponent:databaseName];
    sqlite3_open([databasePath UTF8String], &db);
    return db;
}

/*
 *
 */
+ (void)closeDatabase:(sqlite3 *)db
{
    sqlite3_close(db);
}

/*
 *
 */
+ (void)createTable:(sqlite3 *)db
{
    sqlite3_stmt *sqlStmt;
    const char *createErrorTableString = "CREATE TABLE IF NOT EXISTS Error ( ID INTEGER PRIMARY KEY AUTOINCREMENT, DateLogged TEXT, Name TEXT, UserID TEXT, CurrentPlatform TEXT, Message TEXT, Cause TEXT, StackTrace TEXT, CurrentPlatformVersion TEXT, CurrentUsername TEXT, endpoint TEXT );";

    if((sqlite3_prepare_v2(db, createErrorTableString, -1, &sqlStmt, NULL)) == SQLITE_OK)
    {
        if((sqlite3_step(sqlStmt)) != SQLITE_DONE)
        {
            NSLog(@"%s", sqlite3_errmsg(db));
        }
    }
    else
    {
        NSLog(@"%s", sqlite3_errmsg(db));
    }
    sqlite3_clear_bindings(sqlStmt);
    sqlite3_reset(sqlStmt);
}

/*
 *
 */
+ (void)insertErrorWithDB:(sqlite3 *)db config:(NSDictionary *)config
{
    sqlite3_stmt *sqlStmt;

    NSDictionary *headers = [config objectForKey:@"headers"];
    NSDictionary *error = [config objectForKey:@"error"];

    if(headers) {
        NSString *userIdString;
        NSString *currentPlatformVersionString;

        if([headers objectForKey:@"userId"] && [[headers objectForKey:@"userId"] isKindOfClass: [NSNumber class]])
            userIdString = [NSString stringWithFormat:@"%lu", (unsigned long)[[headers objectForKey:@"userId"] longValue]];
        else
            userIdString = [headers objectForKey:@"userId"];

        if([headers objectForKey:@"currentPlatformVersion"] && [[headers objectForKey:@"currentPlatformVersion"] isKindOfClass: [NSNumber class]])
            currentPlatformVersionString = [NSString stringWithFormat:@"%lu", (unsigned long)[[headers objectForKey:@"currentPlatformVersion"] longValue]];
        else
            currentPlatformVersionString = [headers objectForKey:@"currentPlatformVersion"];

        NSDateFormatter *dateFormat = [[NSDateFormatter alloc] init];
        [dateFormat setLocale:[NSLocale localeWithLocaleIdentifier: DATE_GLOBAL_LOCALE]];
        [dateFormat setDateFormat:DATABASE_DATE_TIME_FORMAT];
        NSString *currentDateString = [dateFormat stringFromDate:[NSDate date]];

        const char *sql = "INSERT INTO Error (UserID, CurrentPlatform, CurrentPlatformVersion, Name, Message, Cause, StackTrace, CurrentUsername, endpoint, DateLogged) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        if((sqlite3_prepare_v2(db, sql, -1, &sqlStmt, NULL)) == SQLITE_OK)
        {
            sqlite3_bind_text(sqlStmt, 1, [userIdString UTF8String], -1, nil);
            sqlite3_bind_text(sqlStmt, 2, [[headers objectForKey:@"currentPlatform"] UTF8String], -1, nil);
            sqlite3_bind_text(sqlStmt, 3, [currentPlatformVersionString UTF8String], -1, nil);
            sqlite3_bind_text(sqlStmt, 4, [[error objectForKey:@"name"] UTF8String], -1, nil);
            sqlite3_bind_text(sqlStmt, 5, [[error objectForKey:@"message"] UTF8String], -1, nil);
            sqlite3_bind_text(sqlStmt, 6, [[error objectForKey:@"cause"] UTF8String], -1, nil);
            sqlite3_bind_text(sqlStmt, 7, [[error objectForKey:@"stackTrace"] UTF8String], -1, nil);
            sqlite3_bind_text(sqlStmt, 8, [[config objectForKey:@"currentUsername"] UTF8String], -1, nil);
            sqlite3_bind_text(sqlStmt, 9, [[config objectForKey:@"endpoint"] UTF8String], -1, nil);
            sqlite3_bind_text(sqlStmt, 10, [currentDateString UTF8String], -1, nil);

            if((sqlite3_step(sqlStmt)) != SQLITE_DONE)
            {
                NSLog(@"%s", sqlite3_errmsg(db));
            }
        }
        else
        {
            NSLog(@"%s", sqlite3_errmsg(db));
        }
        sqlite3_clear_bindings(sqlStmt);
        sqlite3_reset(sqlStmt);
    }
}

/*
 *
 */
+ (NSString *)validateStringForUTF8: (char *)stringUTF
{
    NSString *validString = ((stringUTF == nil) || (strcmp(stringUTF, "\0") == 0)) ? @"" : [NSString stringWithUTF8String: stringUTF];
    return validString;
}

/*
 *
 */
+ (NSDictionary *)getErrorFromDB: (sqlite3 *)db
{
    sqlite3_stmt *sqlStmt;
    NSDictionary *config = [[NSDictionary alloc] init];
    NSDictionary *header = [[NSDictionary alloc] init];
    NSDictionary *error = [[NSDictionary alloc] init];
    const char *sql = "SELECT UserID, CurrentPlatform, CurrentPlatformVersion, Name, Message, Cause, StackTrace, ID, CurrentUsername, endpoint, DateLogged FROM Error LIMIT 1;";
    if((sqlite3_prepare_v2(db, sql, BUFFER_SIZE, &sqlStmt, NULL)) == SQLITE_OK)
    {
        while((sqlite3_step(sqlStmt)) == SQLITE_ROW)
        {

            NSString *userIdString = [NSString stringWithFormat:@"%s", (char *)sqlite3_column_text(sqlStmt, 0)];
            NSString *currentPlatformVersionString = [NSString stringWithFormat:@"%s", (char *)sqlite3_column_text(sqlStmt, 2)];

            header = [NSDictionary dictionaryWithObjectsAndKeys:
                      userIdString, @"userId",
                      [database validateStringForUTF8: (char *)sqlite3_column_text(sqlStmt, 1)], @"currentPlatform",
                      currentPlatformVersionString, @"currentPlatformVersion",
                      nil];

            error = [NSDictionary dictionaryWithObjectsAndKeys:
                     [database validateStringForUTF8: (char *)sqlite3_column_text(sqlStmt, 3)], @"name",
                     [database validateStringForUTF8: (char *)sqlite3_column_text(sqlStmt, 4)], @"message",
                     [database validateStringForUTF8: (char *)sqlite3_column_text(sqlStmt, 5)], @"cause",
                     [database validateStringForUTF8: (char *)sqlite3_column_text(sqlStmt, 6)], @"stackTrace",
                     nil];

            NSString *idString = [NSString stringWithFormat:@"%s", (char *)sqlite3_column_text(sqlStmt, 7)];

            config = [NSDictionary dictionaryWithObjectsAndKeys:
                      header, @"headers",
                      error, @"error",
                      idString, @"ID",
                      [database validateStringForUTF8: (char *)sqlite3_column_text(sqlStmt, 8)], @"currentUserName",
                      [database validateStringForUTF8: (char *)sqlite3_column_text(sqlStmt, 9)], @"endpoint",
                      [database validateStringForUTF8: (char *)sqlite3_column_text(sqlStmt, 10)], @"dateLogged",
                      nil];
        }
    }
    else
    {
        NSLog(@"%s", sqlite3_errmsg(db));
    }
    sqlite3_clear_bindings(sqlStmt);
    sqlite3_reset(sqlStmt);
    return config;
}

/*
 *
 */
+ (void)deleteErrorFromDB: (sqlite3 *)db usingID:(int)id
{
    sqlite3_stmt *sqlStmt;
    const char *sql = "DELETE FROM Error WHERE ID = ?";

    if((sqlite3_prepare_v2(db, sql, -1, &sqlStmt, NULL)) == SQLITE_OK)
    {
        sqlite3_bind_int(sqlStmt, 1, id);
        if ((sqlite3_step(sqlStmt)) != SQLITE_DONE)
        {
            NSLog( @"Error while deleting %ld '%s'", (long)id, sqlite3_errmsg(db));
        }
    }
    sqlite3_reset(sqlStmt);
    sqlite3_finalize(sqlStmt);
}

/*
 *
 */
+ (void)deleteErrorTable:(sqlite3 *)db
{
    sqlite3_stmt *sqlStmt;
    const char *sql = "DROP TABLE IF EXISTS Error;";

    if(sqlite3_prepare_v2(db, sql, -1, &sqlStmt, NULL) == SQLITE_OK)
    {
        if (sqlite3_step(sqlStmt) != SQLITE_DONE)
        {
            NSLog( @"Error while deleting debug_log '%s'", sqlite3_errmsg(db));
        }
    }
    sqlite3_reset(sqlStmt);
    sqlite3_finalize(sqlStmt);
}

/*
 *
 */
+ (int)queryUserVersion:(sqlite3 *)db {
    static sqlite3_stmt *stmt_version;
    int databaseVersion = 0;
    if((sqlite3_prepare_v2(db, "PRAGMA user_version;", -1, &stmt_version, NULL)) == SQLITE_OK) {
        while((sqlite3_step(stmt_version)) == SQLITE_ROW) {
            databaseVersion = sqlite3_column_int(stmt_version, 0);
        }
    }
    sqlite3_finalize(stmt_version);
    return databaseVersion;
}
@end
