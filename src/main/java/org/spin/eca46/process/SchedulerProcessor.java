/******************************************************************************
 * Product: ADempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 2006-2017 ADempiere Foundation, All Rights Reserved.         *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * or (at your option) any later version.										*
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY, without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program, if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * or via info@adempiere.net or http://www.adempiere.net/license.html         *
 *****************************************************************************/

package org.spin.eca46.process;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MClient;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MPInstance;
import org.compiere.model.MProcess;
import org.compiere.model.MRole;
import org.compiere.model.MScheduler;
import org.compiere.model.MSchedulerLog;
import org.compiere.model.MSchedulerPara;
import org.compiere.model.MUser;
import org.compiere.print.ReportEngine;
import org.compiere.process.ProcessInfo;
import org.compiere.process.ProcessInfoUtil;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.TimeUtil;
import org.compiere.util.Trx;
import org.eevolution.services.dsl.ProcessBuilder;
import org.spin.queue.notification.DefaultNotifier;
import org.spin.queue.util.QueueLoader;

/** 
 * 	Generated Process for (Product Internal Use)
 *  @author Jorg Janke
 *  @version $Id: RequestProcessor.java,v 1.3 2006/07/30 00:53:33 jjanke Exp $
 *  @version Release 3.9.3
 */
public class SchedulerProcessor extends SchedulerProcessorAbstract {
	
	/**	The Concrete Model			*/
	private MScheduler			schedulerProcessor = null;
	/**	Last Summary				*/
	private StringBuffer 		summary = new StringBuffer();
	
	// ctx for the report/process
	Properties 					schedulerContext = new Properties();
	/**	Initial work time			*/
	private long 				startWork;
	private static final String COLUMNNAME_ParameterDefault_To = "ParameterDefault_To";
	
	@Override
	protected void prepare() {
		super.prepare();
		if(getSchedulerId() <= 0) {
			throw new AdempiereException("@AD_WorkflowProcessor_ID@ @NotFound@");
		}
		schedulerProcessor = new MScheduler(getCtx(), getSchedulerId(), get_TrxName());
//		client = MClient.get(getCtx(), schedulerConfiguration.getAD_Client_ID());
	}

	@Override
	protected String doIt() throws Exception {
		startWork = System.currentTimeMillis();
		summary = new StringBuffer(schedulerProcessor.toString())
				.append(" - ");
		// Prepare a ctx for the report/process - BF [1966880]
		schedulerContext.clear();
		MClient schedclient = MClient.get(getCtx(), schedulerProcessor.getAD_Client_ID());
		Env.setContext(schedulerContext, "#AD_Client_ID", schedclient.getAD_Client_ID());
		Env.setContext(schedulerContext, "#AD_Language", schedclient.getAD_Language());
		Env.setContext(schedulerContext, "#AD_Org_ID", schedulerProcessor.getAD_Org_ID());
		if (schedulerProcessor.getAD_Org_ID() != 0) {
			MOrgInfo schedorg = MOrgInfo.get(getCtx(), schedulerProcessor.getAD_Org_ID(), null);
			if (schedorg.getM_Warehouse_ID() > 0)
				Env.setContext(schedulerContext, "#M_Warehouse_ID", schedorg.getM_Warehouse_ID());
		}
		Env.setContext(schedulerContext, "#AD_User_ID", getRunningUserId());
		Env.setContext(schedulerContext, "#SalesRep_ID", getRunningUserId());
		// TODO: It can be convenient to add  AD_Scheduler.AD_Role_ID
		MUser scheduser = MUser.get(getCtx(), getRunningUserId());
		MRole[] schedroles = scheduser.getRoles(schedulerProcessor.getAD_Org_ID());
		if (schedroles != null && schedroles.length > 0)
			Env.setContext(schedulerContext, "#AD_Role_ID", schedroles[0].getAD_Role_ID()); // first role, ordered by AD_Role_ID
		Timestamp ts = new Timestamp(System.currentTimeMillis());
		SimpleDateFormat dateFormat4Timestamp = new SimpleDateFormat("yyyy-MM-dd"); 
		Env.setContext(schedulerContext, "#Date", dateFormat4Timestamp.format(ts)+" 00:00:00" );    //  JDBC format
		Trx.run(transactionName -> {
			try {
				MProcess process = new MProcess(schedulerContext, schedulerProcessor.getAD_Process_ID(), transactionName);
				summary.append(runProcess(process));
			} catch (Exception e) {
				summary.append(e.toString());
				throw new AdempiereException(e);
			}
		});	
		//
		int no = schedulerProcessor.deleteLog();
		summary.append(" Logs deleted=").append(no);
		if (schedulerProcessor.get_TrxName() == null ) {
			Trx.run(this::addSchedulerLog);
		} else {
			addSchedulerLog(schedulerProcessor.get_TrxName());
		}
		return TimeUtil.formatElapsed(new Timestamp(startWork));
	}

	/**
	 * Add Scheduler Log
	 * @param trxName
	 */
	private void addSchedulerLog(String trxName) {
		MSchedulerLog schedulerLog = new MSchedulerLog(schedulerProcessor, summary.toString(), trxName);
		schedulerLog.setReference(TimeUtil.formatElapsed(new Timestamp(startWork)));
		schedulerLog.saveEx();
	}
	/**
	 * 	Run Process or Report
	 *	@param process process
	 *	@return summary
	 *	@throws Exception
	 */
	private String runProcess(MProcess process) throws Exception {
		log.info(process.toString());
		
		boolean isReport = (process.isReport() || process.getAD_ReportView_ID() > 0);
		//
		ProcessBuilder builder = ProcessBuilder.create(getCtx())
			.process(process.getAD_Process_ID())
			.withClientId(schedulerProcessor.getAD_Client_ID())
			.withUserId(getRunningUserId())
			.withTitle(process.getName())
			.withRecordId(schedulerProcessor.getAD_Table_ID(), schedulerProcessor.getRecord_ID());
		//	Fill It
		fillParameter(builder);
		try {
			builder.execute();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
		}
		//	Get Process Info
		ProcessInfo info = builder.getProcessInfo();
		if(info.isError()) {
			// notify supervisor if error
			int supervisor = schedulerProcessor.getSupervisor_ID();
			if (supervisor > 0) {
				ProcessInfoUtil.setLogFromDB(info);
				//	
				Trx.run(transactionName -> {
					//	Get instance for notifier
					DefaultNotifier notifier = (DefaultNotifier) QueueLoader.getInstance().getQueueManager(DefaultNotifier.QUEUETYPE_DefaultNotifier)
							.withContext(getCtx())
							.withTransactionName(transactionName);
					//	Send notification to queue
					notifier
						.clearMessage()
						.withApplicationType(DefaultNotifier.DefaultNotificationType_UserDefined)
						.withUserId(getRunningUserId())
						.addRecipient(supervisor)
						.withText(info.getSummary() + " " + info.getLogInfo())
						.withDescription(process.getName())
						.withEntity(MPInstance.Table_ID, info.getAD_PInstance_ID());
					//	Add to queue
					notifier.addToQueue();
				});
			}
		} else {
			// notify recipients on success
			Integer[] userIds = schedulerProcessor.getRecipientByUserIds();
			StringBuffer errorsSending = new StringBuffer();
			if (userIds.length > 0)  {
				ProcessInfoUtil.setLogFromDB(info);
				Arrays.asList(userIds)
				.stream()
				.filter(userId -> userId!= schedulerProcessor.getCreatedBy())
				.forEach(userId -> {
					AtomicReference<File> report = new AtomicReference<File>();
					if (isReport) {
						//	Report
						ReportEngine re = ReportEngine.get(getCtx(), info);
						if (re != null) {
							report.set(re.getPDF());
						} else {
							if(errorsSending.length() > 0) {
								errorsSending.append(Env.NL);
							}
							errorsSending.append("@Error@ " + process.getAD_Process_ID() + " - " + process.getName());
						}
					}
					//	
					Trx.run(transactionName -> {
						//	Get instance for notifier
						DefaultNotifier notifier = (DefaultNotifier) QueueLoader.getInstance().getQueueManager(DefaultNotifier.QUEUETYPE_DefaultNotifier)
								.withContext(getCtx())
								.withTransactionName(transactionName);
						//	Send notification to queue
						notifier
							.clearMessage()
							.withApplicationType(DefaultNotifier.DefaultNotificationType_UserDefined)
							.withUserId(getRunningUserId())
							.addRecipient(userId)
							.addAttachment(report.get())
							.withText(info.getSummary() + " " + info.getLogInfo())
							.withDescription(process.getName())
							.withEntity(schedulerProcessor);
						//	Change Subject and Text
						if (isReport) {
							notifier
								.withText(schedulerProcessor.getDescription())
								.withDescription(schedulerProcessor.getName());
						}
						//	Add to queue
						notifier.addToQueue();
					});
				});
				//	report all errors
				if(errorsSending.length() > 0) {
					info.setSummary(Optional.ofNullable(info.getSummary()).orElse("") + Env.NL + errorsSending.toString());
				}
			}
		}
		return info.getSummary();
	}	//	runProcess

	private int getRunningUserId() {
		int AD_User_ID;
		if (schedulerProcessor.getSupervisor_ID() > 0)
			AD_User_ID = schedulerProcessor.getSupervisor_ID();
		else if (schedulerProcessor.getCreatedBy() > 0)
			AD_User_ID = schedulerProcessor.getCreatedBy();
		else if (schedulerProcessor.getUpdatedBy() > 0)
			AD_User_ID = schedulerProcessor.getUpdatedBy();
		else
			AD_User_ID = 100; //fall back to SuperUser
		return AD_User_ID;
	}
	
	/**
	 * 	Fill Parameter
	 *	@param pInstance process instance
	 */
	private void fillParameter(ProcessBuilder builder) {
		Arrays.asList(schedulerProcessor.getParameters(false))
			.forEach(schedulerParameter -> {
				String variable = schedulerParameter.getParameterDefault();
				String variableTo = schedulerParameter.get_ValueAsString(COLUMNNAME_ParameterDefault_To);
				log.fine(schedulerParameter.getColumnName() + " = " + variable);
				//	Value - Constant/Variable
				Object value = parseValue(variable, schedulerParameter);
				Object valueTo = parseValue(variableTo, schedulerParameter);
				
				Object parameterOfProcess = processValue(variable, value, schedulerParameter);
				Object parameterOfProcessTo = processValue(variableTo, valueTo, schedulerParameter);
				
				//	add to builder
				if (parameterOfProcess != null) {
					log.fine("ColumnName=" + schedulerParameter.getColumnName() + ", Value=" + parameterOfProcess);
					if (parameterOfProcessTo != null) {
						log.fine("ColumnName=" + schedulerParameter.getColumnName() + ", ValueTo=" + parameterOfProcessTo);
						builder.withParameter(schedulerParameter.getColumnName(), parameterOfProcess, parameterOfProcessTo);
					}else 
						builder.withParameter(schedulerParameter.getColumnName(), parameterOfProcess);
					
				}
			});
	}
	
	/**
	 * Parse Value
	 * @param variable
	 * @param schedulerParameter
	 * @return
	 */
	private String parseValue(String variable, MSchedulerPara schedulerParameter) {
		String value = variable;
		if (variable == null
				|| (variable != null && variable.length() == 0)) {
				value = null;
			} else if (  variable.indexOf('@') != -1
					&& variable.indexOf('@') != variable.lastIndexOf('@')) {
				//	Strip
				int index = variable.indexOf('@');
				String columnName = variable.substring(index+1);
				index = columnName.indexOf('@');
				if (index != -1) {
					columnName = columnName.substring(0, index);
					//	try Env
					String environment = Env.getContext(schedulerContext, columnName);
					if (environment == null || environment.length() == 0)
						environment = Env.getContext(getCtx(), columnName);
					if (environment.length() == 0) {
						log.warning(schedulerParameter.getColumnName()
							+ " - not in environment =" + columnName
							+ "(" + variable + ")");
					} else {
						value = environment;
					}
				}
			}	//	@variable@
		return value;
	}
	
	/**
	 * Process Value
	 * @param variable
	 * @param value
	 * @param schedulerParameter
	 * @return
	 */
	private Object processValue (String variable, Object value, MSchedulerPara schedulerParameter ) {
		//	Object Parameter
		Object parameterOfProcess = null;
		if (value != null) {

			//	Convert to Type
			try {
				if (DisplayType.isNumeric(schedulerParameter.getDisplayType())
					|| DisplayType.isID(schedulerParameter.getDisplayType())) {
					BigDecimal decimalValue = null;
					if (value instanceof BigDecimal)
						decimalValue = (BigDecimal)value;
					else if (value instanceof Integer)
						decimalValue = new BigDecimal (((Integer)value).intValue());
					else
						decimalValue = new BigDecimal (value.toString());
					parameterOfProcess = decimalValue;
					log.fine(schedulerParameter.getColumnName()
						+ " = " + variable + " (=" + decimalValue + "=)");
				} else if (DisplayType.YesNo == schedulerParameter.getDisplayType()) {
					boolean booleanValue = false;
					if(value instanceof Boolean) {
						booleanValue = ((Boolean) value);
					} else {
						booleanValue = value.toString().replaceAll("'", "").equals("Y");
					}
					parameterOfProcess = booleanValue;
					log.fine(schedulerParameter.getColumnName()
							+ " = " + variable + " (=" + booleanValue + "=)");
				} else if (DisplayType.isDate(schedulerParameter.getDisplayType())) {
					Timestamp dateValue = null;
					if (value instanceof Timestamp)
						dateValue = (Timestamp)value;
					else
						dateValue = Timestamp.valueOf(value.toString());
					parameterOfProcess = dateValue;
					log.fine(schedulerParameter.getColumnName()
						+ " = " + variable + " (=" + dateValue + "=)");
				} else {
					parameterOfProcess = value.toString().replaceAll("'", "");
					log.fine(schedulerParameter.getColumnName()
						+ " = " + variable
						+ " (=" + value + "=) " + value.getClass().getName());
				}
			}
			catch (Exception e) {
				log.warning(schedulerParameter.getColumnName()
					+ " = " + variable + " (" + value
					+ ") " + value.getClass().getName()
					+ " - " + e.getLocalizedMessage());
			}
		}
		
		return parameterOfProcess;
	}
	/**
	 * 	Get Server Info
	 *	@return info
	 */
	public String getServerInfo()
	{
		return "Last=" + summary.toString();
	}	//	getServerInfo
}