# Dynatrace-Third-Party-Time-Monitor
The Third Party Time Monitor plugin queries a built-in Transaction Flow Dashboard to return response time, execution time, and count for third party calls captured via UEM. 
Metrics can also be filtered by business transaction. 
The plugin supports both HTTP and HTTPS.


Plugin Parameters:
Protocol (required) - http protocol to access the dynaTrace server rest interface
Port (required) - http port to access the dynaTrace server rest interface
username (required) - username to log into the dynaTrace server
password (required) - password to log into the dynaTrace server
Aggregation (required) - min/avg/max aggregation of measures
System Profile Filter (required) - System Profile Name
Timeframe Filter (required) - Timeframe for tier times
Filter by Business Transaction (optional) - yes/no
Business Transaction Filter (optional) - Business Transaction name


Measures:
Response Time
Count
