/*
 *
 *  Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * /
 */

package com.wso2telco.dep.mediator.impl.payment;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.carbon.utils.CarbonUtils;

import com.wso2telco.core.dbutils.exception.BusinessException;
import com.wso2telco.core.dbutils.fileutils.FileReader;
import com.wso2telco.dep.mediator.MSISDNConstants;
import com.wso2telco.dep.mediator.OperatorEndpoint;
import com.wso2telco.dep.mediator.entity.OparatorEndPointSearchDTO;
import com.wso2telco.dep.mediator.internal.ApiUtils;
import com.wso2telco.dep.mediator.internal.Base64Coder;
import com.wso2telco.dep.mediator.mediationrule.OriginatingCountryCalculatorIDD;
import com.wso2telco.dep.mediator.util.APIType;
import com.wso2telco.dep.mediator.util.FileNames;
import com.wso2telco.dep.oneapivalidation.exceptions.CustomException;
import com.wso2telco.dep.subscriptionvalidator.exceptions.ValidatorException;
import com.wso2telco.dep.subscriptionvalidator.util.ValidatorUtils;

/**
 *
 * @author Wso2telco
 */

public class PaymentUtil {
	private static Log log = LogFactory.getLog(PaymentUtil.class);

	
	public static OperatorEndpoint validateAndGetOperatorEndpoint(PaymentExecutor executor, 
			OriginatingCountryCalculatorIDD occi, ApiUtils apiUtils, MessageContext context) throws ValidatorException, BusinessException, Exception {

		OperatorEndpoint endpoint = null;
		JSONObject jsonBody = executor.getJsonBody();
		String endUserId = jsonBody.getJSONObject("amountReservationTransaction").getString("endUserId");
		String msisdn = endUserId.substring(5);
		context.setProperty(MSISDNConstants.USER_MSISDN, msisdn);
		context.setProperty(MSISDNConstants.MSISDN, endUserId);
		
		if (ValidatorUtils.getValidatorForSubscriptionFromMessageContext(context).validate(context)) {
			OparatorEndPointSearchDTO searchDTO = new OparatorEndPointSearchDTO();
			searchDTO.setApi(APIType.PAYMENT);
			searchDTO.setApiName((String) context.getProperty("API_NAME"));
			searchDTO.setContext(context);
			searchDTO.setIsredirect(false);
			searchDTO.setMSISDN(endUserId.replace("tel:", ""));
			searchDTO.setOperators(executor.getValidoperators(context));
			searchDTO.setRequestPathURL(executor.getSubResourcePath());
			endpoint = occi.getOperatorEndpoint(searchDTO);
		}
		return endpoint;
	}
	
	@SuppressWarnings("deprecation")
	public static String formatClientCorrelator(PaymentExecutor executor, ApiUtils apiUtils, String appId, String subscriber, String requestId, String clientCorrelator) {
		
		FileReader fileReader = new FileReader();
        String file = CarbonUtils.getCarbonConfigDirPath() + File.separator + FileNames.MEDIATOR_CONF_FILE.getFileName();
 		Map<String, String> mediatorConfMap = fileReader.readPropertyFile(file);
		String hub_gateway_id = mediatorConfMap.get("hub_gateway_id");
        log.debug("Hub / Gateway Id : " + hub_gateway_id);
        
        JSONObject jsonBody = executor.getJsonBody();
		if (clientCorrelator == null || clientCorrelator.equals("")) {
			log.debug("clientCorrelator not provided by application and hub/plugin generating clientCorrelator on behalf of application");
			String hashString = apiUtils.getHashString(jsonBody.toString());
			log.debug("hashString : " + hashString);
			clientCorrelator = hashString + "-" + requestId + ":" + hub_gateway_id + ":" + appId;
		} else {

			log.debug("clientCorrelator provided by application");
			clientCorrelator = clientCorrelator + ":" + hub_gateway_id + ":" + appId;
		}
		
		return clientCorrelator;
	}
	
	public static String nullOrTrimmed(String s) {
		String rv = null;
		if (s != null && s.trim().length() > 0) {
			rv = s.trim();
		}
		return rv;
	}
	
	public static String storeSubscription(MessageContext context)
			throws AxisFault {
		String subscription = null;

		org.apache.axis2.context.MessageContext axis2MessageContext = ((Axis2MessageContext) context)
				.getAxis2MessageContext();
		Object headers = axis2MessageContext
				.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
		if (headers != null && headers instanceof Map) {
			try {
				Map headersMap = (Map) headers;
				String jwtparam = (String) headersMap.get("x-jwt-assertion");
				String[] jwttoken = jwtparam.split("\\.");
				String jwtbody = Base64Coder.decodeString(jwttoken[1]);
				JSONObject jwtobj = new JSONObject(jwtbody);
				subscription = jwtobj
						.getString("http://wso2.org/claims/subscriber");

			} catch (JSONException ex) {
				throw new AxisFault("Error retriving application id");
			}
		}

		return subscription;
	}

	
   
	public static boolean isAggregator(MessageContext context) throws AxisFault {
		boolean aggregator = false;

		try {
			org.apache.axis2.context.MessageContext axis2MessageContext = ((Axis2MessageContext) context)
					.getAxis2MessageContext();
			Object headers = axis2MessageContext
					.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
			if (headers != null && headers instanceof Map) {
				Map headersMap = (Map) headers;
				String jwtparam = (String) headersMap.get("x-jwt-assertion");
				String[] jwttoken = jwtparam.split("\\.");
				String jwtbody = Base64Coder.decodeString(jwttoken[1]);
				JSONObject jwtobj = new JSONObject(jwtbody);
				String claimaggr = jwtobj
						.getString("http://wso2.org/claims/role");
				if (claimaggr != null) {
					String[] allowedRoles = claimaggr.split(",");
					for (int i = 0; i < allowedRoles.length; i++) {
						if (allowedRoles[i]
								.contains(MSISDNConstants.AGGRIGATOR_ROLE)) {
							aggregator = true;
							break;
						}
					}
				}
			}
		} catch (Exception e) {
			log.info("Error retrive aggregator");
		}

		return aggregator;
	}

	public void validatePaymentCategory(JSONObject chargingdmeta,
			List<String> lstCategories) throws JSONException {
		boolean isvalid = false;
		String chargeCategory = "";
		if ((!chargingdmeta.isNull("purchaseCategoryCode"))
				&& (!chargingdmeta.getString("purchaseCategoryCode").isEmpty())) {

			chargeCategory = chargingdmeta.getString("purchaseCategoryCode");
			for (String d : lstCategories) {
				if (d.equalsIgnoreCase(chargeCategory)) {
					isvalid = true;
					break;
				}
			}
		} else {
			isvalid = true;
		}

		if (!isvalid) {
			throw new CustomException("POL0001",
					"A policy error occurred. Error code is %1",
					new String[] { "Invalid " + "purchaseCategoryCode : "
							+ chargeCategory });
		}
	}

	public static String str_piece(String str, char separator, int index) {
		String str_result = "";
		int count = 0;
		for (int i = 0; i < str.length(); i++) {
			if (str.charAt(i) == separator) {
				count++;
				if (count == index) {
					break;
				}
			} else {
				if (count == index - 1) {
					str_result += str.charAt(i);
				}
			}
		}
		return str_result;
	}

}
