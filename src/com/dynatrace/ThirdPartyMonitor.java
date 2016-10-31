
/**
  ***************************************
  * Third Party Time Monitor Plugin
  ***************************************
  * Author: Daniel Pohanka (Dynatrace)
  * Version: 1.0.0
  * Created: 10/31/2016
  *
  * This plugin retrieves the response, execution, and count per third party host in the transaction flow dashlet.
  * For information, please visit https://github.com/dynaTrace/Dynatrace-Third-Party-Time-Monitor
  **/ 

package com.dynatrace;

import com.dynatrace.diagnostics.pdk.*;
import com.dynatrace.MapVariableResolver;

import java.util.logging.Logger;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.namespace.QName;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;

import org.apache.http.client.ClientProtocolException;
import java.io.IOException;


public class ThirdPartyMonitor implements Monitor {

	private static final Logger log = Logger.getLogger(ThirdPartyMonitor.class.getName());
	
	// measure constants
		private static final String METRIC_GROUP = "Third Party Timer";
		private static final String MSR_ResponseTime = "Response Time";
		private static final String MSR_Count = "Count";

		//variables
		private Collection<MonitorMeasure>  measures  = null;
		private URLConnection connection;
		private String urlprotocol;
		private int urlport;
		private String dynaTraceURL;
		private String username;
		private String password;
		private String responseTimeMeasure;
		private MonitorMeasure dynamicMeasure;
		private NodeList xpathNodeList;

	/**
	 * Initializes the Plugin. 
	 * 
	 * If the returned status is null or the status code is a
	 * non-success code then {@link Plugin#teardown() teardown()} will be called
	 * next.
	 *
	 * Resources like sockets or files can be opened in this method.
	 * @param env
	 *            the configured <tt>MonitorEnvironment</tt> for this Plugin;
	 *            contains subscribed measures, but <b>measurements will be
	 *            discarded</b>
	 * 
	 * @return a Status object that describes the result of the method call
	 */
	@Override
	public Status setup(MonitorEnvironment env) throws Exception {
		log.fine("*****BEGIN PLUGIN LOGGING*****");
		log.fine("Entering setup method");
		log.finer("Entering variables from plugin.xml");
		
		urlprotocol = env.getConfigString("protocol");
		urlport = env.getConfigLong("httpPort").intValue();
		
		username = env.getConfigString("username");
		password = env.getConfigPassword("password");
		
		if (username.equals("") || password.equals("")){
			log.severe("username and password are required");
				return new Status(Status.StatusCode.ErrorInternal);
		}
		
		log.finer("URL Protocol: " + urlprotocol);
		log.finer("URL Port: " + urlport);
		log.finer("Username: " + username);
		
		//set up measures
		String aggregation = env.getConfigString("aggregation");
		responseTimeMeasure = "response_" + aggregation;		
		
		//Create Report Url
		dynaTraceURL = "/rest/management/reports/create/TransactionFlow?type=XML&format=XML+Export";
		if (env.getConfigString("systemProfileFilter").equals("")){
			log.severe("System Profile Filter entry is required");
				return new Status(Status.StatusCode.ErrorInternal);
		}
		dynaTraceURL = dynaTraceURL + "&source=live:" + env.getConfigString("systemProfileFilter");
		String timeframe = env.getConfigString("timeframeFilter").replaceAll(" ",":").toUpperCase();
		dynaTraceURL = dynaTraceURL +  "&filter=tf:OffsetTimeframe?" + timeframe;
		
		if (env.getConfigBoolean("btBoolean")==true){
			String btFilter;
			if (!(btFilter = env.getConfigString("btFilter")).equals("")){	
				dynaTraceURL = dynaTraceURL + "&filter=bt:" + btFilter;}
		}
		
		log.finer("Report URL: " + dynaTraceURL);
		log.fine("Exiting setup method");
		
		return new Status(Status.StatusCode.Success);
	}

	/**
	 * Executes the Monitor Plugin to retrieve subscribed measures and store
	 * measurements.
	 *
	 * <p>
	 * This method is called at the scheduled intervals. If the Plugin execution
	 * takes longer than the schedule interval, subsequent calls to
	 * {@link #execute(MonitorEnvironment)} will be skipped until this method
	 * returns. After the execution duration exceeds the schedule timeout,
	 * {@link TaskEnvironment#isStopped()} will return <tt>true</tt>. In this
	 * case execution should be stopped as soon as possible. If the Plugin
	 * ignores {@link TaskEnvironment#isStopped()} or fails to stop execution in
	 * a reasonable timeframe, the execution thread will be stopped ungracefully
	 * which might lead to resource leaks!
	 *
	 * @param env
	 *            a <tt>MonitorEnvironment</tt> object that contains the
	 *            Plugin configuration and subscribed measures. These
	*            <tt>MonitorMeasure</tt>s can be used to store measurements.
	 * @return a <tt>Status</tt> object that describes the result of the
	 *         method call
	 */
	@Override
	public Status execute(MonitorEnvironment env) throws Exception {
		
		log.fine("Entering execute method");
		
		log.finer("Entering URL Setup");
		URL overviewurl = new URL(urlprotocol, env.getHost().getAddress(), urlport, dynaTraceURL);		
		log.finer("Executing URL: " + overviewurl.toString());
		
		try {
			
			//login to dynatrace server
			log.finer("Entering username/password setup");
			String userpass = username + ":" + password;
			String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(userpass.getBytes());
			
			disableCertificateValidation();
				
			//URL to grab XML file
			log.finer("Entering XML file grab");
			connection = overviewurl.openConnection();
			connection.setRequestProperty("Authorization", basicAuth);
			connection.setConnectTimeout(50000);
			
			InputStream responseIS = connection.getInputStream();	
			DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = xmlFactory.newDocumentBuilder();
			Document xmlDoc = docBuilder.parse(responseIS);
			XPathFactory xpathFact = XPathFactory.newInstance();
			XPath xpath = xpathFact.newXPath();
												
			//used to store each unique tier and its values
			Set<String> uniqueTierSet = new HashSet<String>();
			xpathNodeList = (NodeList) xpath.evaluate("/dashboardreport/data/admdashlet/adm/agent[contains(@name, 'Third Party Content')]", xmlDoc, XPathConstants.NODESET);
			log.finer("Number of Unique xpathNodeList: " + xpathNodeList.getLength());
					
			//count number of unique rows
			if (xpathNodeList.getLength() >= 1)
			{
				
				for (int i = 0; i < xpathNodeList.getLength(); ++i){
					log.finer("i: " + i);
					String tempString = xpathNodeList.item(i).getAttributes().getNamedItem("host").toString();
					log.finer("tempString orig: " + tempString);
					String changedTempString = tempString.replaceAll("\"","").replace("host=","");
					log.finer("tempString chg: " + changedTempString);
					uniqueTierSet.add(changedTempString);
				}
			}	
					
			log.finer("Number of Unique Rows: " + uniqueTierSet.size());
			String[] tempStringArray = uniqueTierSet.toArray(new String[0]);
										
			//loop through array of unique tiers
			for (int j = 0; j < uniqueTierSet.size(); ++j){
				log.finer("Splitting for Row: " + tempStringArray[j]);
				String tempString = tempStringArray[j];
				log.finer("Splitting for tempString: " + tempString);
				dynamicMetric(env, xpath, tempString, xmlDoc);
			}	
				
		
		} catch (ClientProtocolException e) {
			log.severe("ClientProtocolException: " + e);
			return new Status(Status.StatusCode.ErrorInternal);

		} catch (IOException e) {
			log.severe("IOException: " + e);
			return new Status(Status.StatusCode.ErrorInternal);

		} catch (Exception e){
			log.severe("Exception: " + e);
			return new Status(Status.StatusCode.ErrorInternal);
		}
		
		log.fine("Exiting execute method");
		log.fine("*****END PLUGIN LOGGING*****");
		
		return new Status(Status.StatusCode.Success);
	}
	
	
	/**
	 * Converts an attribute map from a NamedNodeMap object to a String value.
	 *
	 * @param 	env
	 *          	a MonitorEnvironment object that contains the
	 *            	Plugin configuration and subscribed measures. These
	*            	MonitorMeasures can be used to store measurements.
	 *			xpath
	 *          	a XPath object that contains syntax for defining parts 
	 *				of an XML document. xpath uses path expressions to navigate in XML documents. 
	 *				xpath contains a library of standard functions. 
	 *			tempStringMeasure
	 *          	a String that contains the to be evaluated splitting
	 *			xmlDoc
	 *          	a document objectobject that contains the parsed xml response
	 */
	private void dynamicMetric(MonitorEnvironment env, XPath xpath, String tempStringMeasure, Document xmlDoc) throws XPathExpressionException {
		
		log.fine("Entering dynamicMetrics method");
		
		//assign string as a variable to use in xPath evaluate statement
		MapVariableResolver vr = new MapVariableResolver() ;
		vr.setVariable("myVar", tempStringMeasure);
		vr.setVariable("thirdParty", "Third Party Content@" + tempStringMeasure);
		xpath.setXPathVariableResolver(vr);
		log.finer("myVar: " + vr.resolveVariable( new QName ("myVar")));
		NodeList thirdPartyTimeNodeList = null;
		thirdPartyTimeNodeList = (NodeList) xpath.evaluate("/dashboardreport/data/admdashlet/adm/agent[contains(@host, $myVar)]", xmlDoc, XPathConstants.NODESET);
		
		NodeList thirdPartyCountNodeList = null;
		thirdPartyCountNodeList = (NodeList) xpath.evaluate("/dashboardreport/data/admdashlet/adm_links/agent[contains(@to, $thirdParty)]", xmlDoc, XPathConstants.NODESET);
		
		
		log.finer("Size tierNodeList: " + thirdPartyTimeNodeList.getLength());
		
		if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_ResponseTime)) != null) {
			
			List<Double> resultList = new ArrayList<Double>();
			double responseTime = 0;
			
			for (int i = 0; i < thirdPartyTimeNodeList.getLength(); ++i){
				log.finer("Entering dynamicMetrics for Loop");				
				String attributesAsString = getAttributesAsString(thirdPartyTimeNodeList.item(i).getAttributes());
				log.finer("NodeName " + thirdPartyTimeNodeList.item(i).getNodeName() + " " + attributesAsString);
				if (attributesAsString.contains(responseTimeMeasure)){
					log.finer("Entering dynamicMetrics for Loop If Statement");
					String tempString = thirdPartyTimeNodeList.item(i).getAttributes().getNamedItem(responseTimeMeasure).toString();
					log.finer("tempString: " + tempString);
					String stringAsDouble = tempString.replaceAll("\"","").replaceAll(responseTimeMeasure + "=","");
					responseTime = Double.parseDouble(stringAsDouble);
					log.finer("responseTime: " + responseTime);
					resultList.add(responseTime);
				}
				responseTime = 0;
			}
			
			log.finer("Calculating Average");
			for (int r = 0; r < resultList.size(); ++r){
				responseTime = responseTime + resultList.get(r);
			}
			
			if (resultList.size() >= 1){
				responseTime = responseTime / resultList.size();
				log.finer("responseTime Avg: " + responseTime);
				
				log.finer("Assigning Measure Value");
				for (MonitorMeasure measure : measures){
					log.finer("MeasureName: " + measure.getMeasureName());	
					log.finer("getParameter: " + measure.getParameter("Tier Filter"));
					dynamicMeasure = env.createDynamicMeasure(measure, "host", tempStringMeasure);
					dynamicMeasure.setValue(responseTime);
				}
			}
		}
		
		
		//Count
		if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_Count)) != null) {
			
			List<Integer> resultList = new ArrayList<Integer>();
			int count = 0;
			
			for (int i = 0; i < thirdPartyCountNodeList.getLength(); ++i){
				log.finer("Entering dynamicMetrics for Loop");				
				String attributesAsString = getAttributesAsString(thirdPartyCountNodeList.item(i).getAttributes());
				log.finer("NodeName " + thirdPartyCountNodeList.item(i).getNodeName() + " " + attributesAsString);
				if (attributesAsString.contains("remoting_count")){
					log.finer("Entering dynamicMetrics for Loop If Statement");
					String tempString = thirdPartyCountNodeList.item(i).getAttributes().getNamedItem("remoting_count").toString();
					log.finer("tempString: " + tempString);
					String stringAsDouble = tempString.replaceAll("\"","").replaceAll("remoting_count" + "=","");
					count = Integer.parseInt(stringAsDouble);
					log.finer("count: " + count);
					resultList.add(count);
				}
				count = 0;
			}
			
			log.finer("Result List Size: " + resultList.size());
			log.finer("Calculating Average");
			for (int r = 0; r < resultList.size(); ++r){
				count = count + resultList.get(r);
			}
			
			if (resultList.size() >= 1){
				log.finer("count: " + count);
				
				log.finer("Assigning Measure Value");
				for (MonitorMeasure measure : measures){
					log.finer("MeasureName: " + measure.getMeasureName());	
					log.finer("getParameter: " + measure.getParameter("Tier Filter"));
					dynamicMeasure = env.createDynamicMeasure(measure, "host", tempStringMeasure);
					dynamicMeasure.setValue(count);					
				}
			}				
		}
		
		log.fine("Exiting dynamicMetricsCollector method");
	}

	/**
	 * Shuts the Plugin down and frees resources.
	 * 
	 * The Plugin methods >setup, execute and teardown are called on 
	 * different threads, but they are called sequentially. This means that 
	 * the execution of these methods does not overlap, they are executed 
	 * one after the other.
	 *
	 * Failed means that either an unhandled exception is thrown or the status
	 * returned by the method contains a non-success code.
	 *
	 * All by the Plugin allocated resources should be freed in this method.
	 * @see Monitor#setup(MonitorEnvironment)
	 */	@Override
	public void teardown(MonitorEnvironment env) throws Exception {
		
		// Releasing variables
		urlprotocol = null;
		username = null;
		password = null;
		responseTimeMeasure = null;
		dynaTraceURL = null;
	}	
	
	
	public static void disableCertificateValidation() {
		
		log.fine("Entering disableCertificateValidation method");  
		
		// Create a trust manager that does not validate certificate chains
		  TrustManager[] trustAllCerts = new TrustManager[] { 
		    new X509TrustManager() {
		      public X509Certificate[] getAcceptedIssuers() { 
		        return new X509Certificate[0]; 
		      }
		      public void checkClientTrusted(X509Certificate[] certs, String authType) {}
		      public void checkServerTrusted(X509Certificate[] certs, String authType) {}
		  }};

		  // Ignore differences between given hostname and certificate hostname
		  HostnameVerifier hv = new HostnameVerifier() {
		    public boolean verify(String hostname, SSLSession session) { return true; }
		  };

		  // Install the all-trusting trust manager
		  try {
		    SSLContext sc = SSLContext.getInstance("SSL");
		    sc.init(null, trustAllCerts, new SecureRandom());
		    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		    HttpsURLConnection.setDefaultHostnameVerifier(hv);
		  } catch (Exception e) {}
		  
		  log.fine("Leaving disableCertificateValidation method");
	}
	
	/**
	 * Converts an attribute map from a NamedNodeMap object to a String value.
	 *
	 * @param attributes
	 *            a NamedNodeMap object that contains the attributes
	 *            of a Node
	 * @return a String object that contains all Node attributes
	 */
	private String getAttributesAsString(NamedNodeMap attributes) {
		StringBuilder sb = new StringBuilder("\n");
		for (int j = 0; j < attributes.getLength(); j++) {
			sb.append("\t- ").append(attributes.item(j).getNodeName()).append(": ").append(attributes.item(j).getNodeValue()).append("\n");
		}
		return sb.toString();
	}
}
