# Dynatrace-Third-Party-Time-Monitor
<br />
The Third Party Time Monitor plugin queries a built-in Transaction Flow Dashboard to return response time, execution time, and count for third party calls captured via UEM.<br />
Metrics can also be filtered by business transaction.<br /> 
The plugin supports both HTTP and HTTPS.<br />
<br />
<br />
<b>Plugin Parameters:</b><br />
	Protocol (required) - http protocol to access the dynaTrace server rest interface<br />
	Port  (required) - http port to access the dynaTrace server rest interface<br />
	username (required) - username to log into the dynaTrace server<br />
	password (required) - password to log into the dynaTrace server<br />
	Aggregation (required) - min/avg/max aggregation of measures<br />
	System Profile Filter (required) - System Profile Name<br />
	Timeframe Filter (required) - Timeframe for tier times<br />
	Filter by Business Transaction (optional) - yes/no<br />
	Business Transaction Filter (optional) - Business Transaction name<br />
<br />
<br />
<b>Measures:</b><br />
	Response Time<br />
	Count<br />
<br />
