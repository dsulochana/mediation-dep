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

import java.util.HashMap;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.json.JSONObject;

import com.wso2telco.dep.mediator.OperatorEndpoint;
import com.wso2telco.dep.mediator.internal.ApiUtils;
import com.wso2telco.dep.mediator.internal.Type;
import com.wso2telco.dep.mediator.internal.UID;
import com.wso2telco.dep.mediator.mediationrule.OriginatingCountryCalculatorIDD;
import com.wso2telco.dep.mediator.service.PaymentService;
import com.wso2telco.dep.mediator.util.HandlerUtils;
import com.wso2telco.dep.mediator.util.ValidationUtils;
import com.wso2telco.dep.oneapivalidation.service.IServiceValidate;
import com.wso2telco.dep.oneapivalidation.service.impl.payment.ValidateReserveAmount;

public class AmountReserveHandler implements PaymentHandler {

	private static Log log = LogFactory.getLog(AmountReserveHandler.class);
	private OriginatingCountryCalculatorIDD occi;
	private PaymentExecutor executor;
	private PaymentService dbservice;
	private ApiUtils apiUtils;
	private PaymentUtil paymentUtil;
	IServiceValidate validator;

	public AmountReserveHandler(PaymentExecutor executor) {
		this.executor = executor;
		occi = new OriginatingCountryCalculatorIDD();
		dbservice = new PaymentService();
		apiUtils = new ApiUtils();
		paymentUtil = new PaymentUtil();
		validator = new ValidateReserveAmount(); 
	}

	@Override
	public boolean validate(String httpMethod, String requestPath, JSONObject jsonBody, MessageContext context) throws Exception {
		if (!httpMethod.equalsIgnoreCase("POST")) {
			((Axis2MessageContext) context).getAxis2MessageContext()
			                               .setProperty("HTTP_SC", 405);
			throw new Exception("Method not allowed");
		}
		validator.validateUrl(requestPath);
		validator.validate(jsonBody.toString());
		ValidationUtils.compareMsisdn(executor.getSubResourcePath(), executor.getJsonBody());
		return true;
	}

	@Override
	public boolean handle(MessageContext context) throws Exception {
		
		String requestId = UID.getUniqueID(Type.PAYMENT.getCode(), context,
				executor.getApplicationid());

		HashMap<String, String> jwtDetails = apiUtils.getJwtTokenDetails(context);
		String appId = jwtDetails.get("applicationid");
		log.debug("Application Id : " + appId);
		String subscriber = jwtDetails.get("subscriber");
		log.debug("Subscriber Name : " + subscriber);
		
		String clientCorrelator = null;
		OperatorEndpoint endpoint = PaymentUtil.validateAndGetOperatorEndpoint(executor, occi, apiUtils, context);	
		JSONObject jsonBody = executor.getJsonBody();	
		
		String sending_add = endpoint.getEndpointref().getAddress();
		log.debug("sending endpoint found: " + sending_add);
		
		JSONObject objPaymentAmount = (JSONObject) jsonBody.getJSONObject("amountReservationTransaction").getJSONObject("paymentAmount");
		if(!objPaymentAmount.isNull("chargingMetaData")) {
			JSONObject chargingdmeta = (JSONObject) objPaymentAmount.get("chargingMetaData");
	        // validate payment categories
	     	List<String> validCategoris = dbservice.getValidPayCategories();
	     	paymentUtil.validatePaymentCategory(chargingdmeta, validCategoris);
		}
		
		JSONObject objAmountTransaction = jsonBody.getJSONObject("amountReservationTransaction");
		if (!objAmountTransaction.isNull("clientCorrelator")) {
			clientCorrelator = PaymentUtil.nullOrTrimmed(objAmountTransaction.get("clientCorrelator").toString());
		}
		clientCorrelator = PaymentUtil.formatClientCorrelator(executor, apiUtils, appId, subscriber, requestId, clientCorrelator);

		HandlerUtils.setHandlerProperty(context, this.getClass().getSimpleName());
        HandlerUtils.setEndpointProperty(context, sending_add);
        HandlerUtils.setGatewayHost(context);
        HandlerUtils.setAuthorizationHeader(context, executor, endpoint);
        	
        context.setProperty("requestResourceUrl", executor.getResourceUrl());
        context.setProperty("requestID", requestId);
        context.setProperty("clientCorrelator", clientCorrelator);
        context.setProperty("operator", endpoint.getOperator());
		context.setProperty("OPERATOR_NAME", endpoint.getOperator());
		context.setProperty("OPERATOR_ID", endpoint.getOperatorId());
		return true;
	}
}