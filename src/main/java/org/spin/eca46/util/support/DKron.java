/*************************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                              *
 * Copyright (C) 2012-2018 E.R.P. Consultores y Asociados, C.A.                      *
 * Contributor(s): Yamel Senih ysenih@erpya.com                                      *
 * This program is free software: you can redistribute it and/or modify              *
 * it under the terms of the GNU General Public License as published by              *
 * the Free Software Foundation, either version 3 of the License, or                 *
 * (at your option) any later version.                                               *
 * This program is distributed in the hope that it will be useful,                   *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                    *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                     *
 * GNU General Public License for more details.                                      *
 * You should have received a copy of the GNU General Public License                 *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.            *
 ************************************************************************************/
package org.spin.eca46.util.support;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MClient;
import org.compiere.model.MScheduler;
import org.compiere.util.Env;
import org.compiere.util.Util;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.json.JSONObject;
import org.spin.model.MADAppRegistration;

/**
 * 	Util class for some helper methods
 * 	@author Yamel Senih, ysenih@erpya.com, ERPCyA http://www.erpya.com
 */
public class DKron implements IExternalProcessor {
	/**	dKron Host	*/
	private String dKronHost = null;
	/**	ADempier Token	*/
	private String adempiereToken = null;
	/**	ADempiere Host	*/
	private String adempiereHost = null;
	/**	ADempiere Endpoint	*/
	private String adempiereEndpoint = null;
	/**	Registration Id	*/
	private int registrationId = 0;
	private final String ADEMPIERE_ENDPOINT = "adempiere_endpoint";
	private String clientUuid;
	
	
	/**
	 * Validate connection
	 */
	private void validate() {
		if(getAppRegistrationId() <= 0) {
			throw new AdempiereException("@AD_AppRegistration_ID@ @NotFound@");
		}
		MADAppRegistration registration = MADAppRegistration.getById(Env.getCtx(), getAppRegistrationId(), null);
		adempiereEndpoint = registration.getParameterValue(ADEMPIERE_ENDPOINT);
		clientUuid = MClient.get(Env.getCtx(), Env.getAD_Client_ID(Env.getCtx())).getUUID();
		if(Util.isEmpty(clientUuid)) {
			throw new AdempiereException("@AD_Client_ID@ @UUID@ @IsMandatory@");
		}
		dKronHost = registration.getHost();
		if(registration.getPort() > 0) {
			dKronHost = dKronHost + ":" + registration.getPort();
		}
	}

	@Override
	public String testConnection() {
		return "Ok";
	}

	@Override
	public void setAppRegistrationId(int registrationId) {
		this.registrationId = registrationId;
		validate();
	}

	@Override
	public int getAppRegistrationId() {
		return registrationId;
	}

	@Override
	public String exportProcessor(IProcessorEntity processor) {
		//	dKron Host
		if(Util.isEmpty(dKronHost)) {
			throw new AdempiereException("@Host@ @NotFound@");
		}
		//	ADempiere Host
		if(Util.isEmpty(adempiereHost)) {
			throw new AdempiereException("ADempiere @Host@ @NotFound@");
		}
		//	ADempiere Token
		if(Util.isEmpty(adempiereToken)) {
			throw new AdempiereException("@Token@ @NotFound@");
		}
		//	ADempiere Endpoint
		if(Util.isEmpty(adempiereEndpoint)) {
			throw new AdempiereException("@Endpoint@ @NotFound@");
		}
		Invocation.Builder invocationBuilder = getClient().target(dKronHost)
				.path("v1")
				.path("jobs")
    			.request(MediaType.APPLICATION_JSON)
    			.header(HttpHeaders.ACCEPT, "application/json");
		//	
		JSONObject jsonValue = new JSONObject(getRequestDefinition(processor));
		Entity<String> entity = Entity.json(jsonValue.toString());
        Response response = invocationBuilder.post(entity);
        if(response.getStatus() != 201
        		|| response.getStatus() != 200) {
        	String output = response.readEntity(String.class);
        	return output;
        }
		return null;
	}
	
	/**
	 * Get Definition for dKron request
	 * @param processor
	 * @return
	 */
	private Map<String, Object> getRequestDefinition(IProcessorEntity processor) {
		Map<String, Object> data = new HashMap<>();
		data.put("name", clientUuid + "_" + processor.getIdentifier());
		data.put("displayname", processor.getDisplayName());
		data.put("schedule", getSchedule(processor));
		data.put("timezone", processor.getTimeZone());
		data.put("disabled", !processor.isEnabled());
		data.put("retries", 0);
		data.put("concurrency", "forbid");
		data.put("executor", "http");
		if(processor.getDateNextRun() != null) {
			SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			dateFormatter.setTimeZone(TimeZone.getTimeZone(processor.getTimeZone()));
			data.put("next", dateFormatter.format(processor.getDateNextRun()));
		}
		Map<String, Object> executorConfig = new HashMap<>();
		executorConfig.put("method", "POST");
		executorConfig.put("url", getCompleteUrl(processor));
		List<String> headers = new ArrayList<>();
		headers.add("\"Authorization: Bearer " + adempiereToken + "\"");
		headers.add("\"Content-Type: application/json\"");
		executorConfig.put("headers", headers.toString());
		executorConfig.put("timeout", "60000");
		executorConfig.put("expectCode", "200");
		executorConfig.put("expectBody", "");
		executorConfig.put("debug", "false");
		data.put("executor_config", executorConfig);
		return data;
	}
	
	private String getCompleteUrl(IProcessorEntity processor) {
		
		return getAdempiereService() + "/" + getProcessCode(processor) + "/" + Env.getAD_Client_ID(Env.getCtx()) + "/" + processor.getProcessorParameterId();
	}
	
	private String getProcessCode(IProcessorEntity processor) {
		if(processor.getProcessorType() == IProcessorEntity.ALERT) {
			return "alert";
		} else if(processor.getProcessorType() == IProcessorEntity.WORKFLOW) {
			return "workflow";
		} else if(processor.getProcessorType() == IProcessorEntity.ACCOUNTING) {
			return "accounting";
		} else if(processor.getProcessorType() == IProcessorEntity.REQUEST) {
			return "request";
		} else if(processor.getProcessorType() == IProcessorEntity.SCHEDULER) {
			return "scheduler";
		} else if(processor.getProcessorType() == IProcessorEntity.PROJECT) {
			return "project";
		} else {
			throw new AdempiereException("@Processor@ @NotFound@");
		}
	}
	
	private String getSchedule(IProcessorEntity processor) {
		String schedule = null;
		if(processor.getFrequency() <= 0) {
			switch (processor.getFrequencyType()) {
			case MScheduler.FREQUENCYTYPE_Hour:
				schedule = "@hourly";
				break;
			case MScheduler.FREQUENCYTYPE_Minute:
				schedule = "@minutely";
				break;
			case MScheduler.FREQUENCYTYPE_Secound:
				schedule = "@every 1s";		
				break;
			case MScheduler.FREQUENCYTYPE_Yearly:
				schedule = "@yearly";
				break;
			case MScheduler.FREQUENCYTYPE_Monthly:
				schedule = "@monthly";
				break;
			case MScheduler.FREQUENCYTYPE_Weekly:
				schedule = "@weekly";
				break;
			case MScheduler.FREQUENCYTYPE_Day:
				schedule = "@daily";
				break;
			case MScheduler.FREQUENCYTYPE_Biweekly:
				schedule = "@every 48h";
				break;
			case MScheduler.FREQUENCYTYPE_Quarterly:
				schedule = "@every 48h";
				break;
			default:
				//	Default hours
				schedule = "@every " + processor.getFrequency() + "h";
				break;
			}	
		} else {
			switch (processor.getFrequencyType()) {
			case MScheduler.FREQUENCYTYPE_Hour:
				schedule = "@every " + processor.getFrequency() + "h";
				break;
			case MScheduler.FREQUENCYTYPE_Minute:
				schedule = "@every " + processor.getFrequency() + "m";
				break;
			case MScheduler.FREQUENCYTYPE_Secound:
				schedule = "@every " + processor.getFrequency() + "s";		
				break;
			case MScheduler.FREQUENCYTYPE_Yearly:
				schedule = "@every " + processor.getFrequency() + "h";
				break;
			case MScheduler.FREQUENCYTYPE_Monthly:
				schedule = "@every " + (processor.getFrequency()  * 24 * 30) + "h";
				break;
			case MScheduler.FREQUENCYTYPE_Weekly:
				schedule = "@every " + (processor.getFrequency()  * 24 * 7) + "h";
				break;
			case MScheduler.FREQUENCYTYPE_Day:
				schedule = "@every " + (processor.getFrequency()  * 24) + "h";
				break;
			case MScheduler.FREQUENCYTYPE_Biweekly:
				schedule = "@every " + (processor.getFrequency()  * 24 * 60) + "h";
				break;
			case MScheduler.FREQUENCYTYPE_Quarterly:
				schedule = "@every " + (processor.getFrequency()  * 24 * 60) + "h";
				break;
			default:
				//	Default hours
				schedule = "@every " + processor.getFrequency() + "h";
				break;
			}
		}
		return schedule;
	}

	/**
	 * Get client
	 * @return
	 */
	public Client getClient() {
		return ClientBuilder.newClient(new ClientConfig())
		.property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);
	}

	@Override
	public void setTokenAccess(String token) {
		adempiereToken = token;
	}

	@Override
	public void setHost(String host) {
		adempiereHost = host;
	}
	
	private String getAdempiereService() {
		return adempiereHost + adempiereEndpoint;
	}
}
