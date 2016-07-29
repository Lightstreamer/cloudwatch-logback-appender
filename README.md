# Logback appender for AWS CloudWatch

## CloudWatch Logback Appender

Send logs to Amazon CloudWatch Logs.

The appender, internally, uses an asynchronous bounded FIFO log queue for CloudWatch communication.
Data encoding and submission is managed using a dedicated thread.
After data submission, if the log queue contains at least *minLogSize*, then new data is submitted immediately, else it waits for *maxLogSize* data in FIFO or for *logPollTimeMillis* timeout.

## Build

Run `mvn package` and replace files in `Lightstreamer/lib/log` with `target/dist`.

### Requirements:
 - Amazon IAM user with 'CloudWatchLogsFullAccess' (arn:aws:iam::aws:policy/CloudWatchLogsFullAccess) policy
 
 or
```
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "logs:PutLogEvents"
    ],
      "Resource": [
        "arn:aws:logs:eu-west-1:*:test-log-group:log-stream:*"
    ]
  }
 ]
}
```

### Usage:

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

	<appender name="AWS_LOGS" class="com.lightstreamer.cloudwatch.logback.appender.AwsLogsJsonAppender">
		<awsRegionName>region</awsRegionName>
		<logGroupName>test-log-group</logGroupName>
	</appender>

	<root level="DEBUG">
		<appender-ref ref="CONSOLE" />
		<appender-ref ref="AWS_LOGS" />
	</root>
	
</configuration>
```

#### Properties:

| Property      | Required  | Description                                        |
| :------------ | :-------: | :------------------------------------------------- |
| awsRegionName | no        | CloudWatch region name.                            |
| logGroupName  | no        | CloudWatch log group name. Default: test-log-group |
| logStreamName | no        | CloudWatch stream name. Default: hostName+timeStamp|
| logPollTimeMillis | no    | Log polling time in milliseconds. Default: 3000    |
| minLogSize    | no        | Min event for wait polling. Default: 128           |
| maxLogSize    | no        | Max events in putLogEvents. Default: 1024          |
