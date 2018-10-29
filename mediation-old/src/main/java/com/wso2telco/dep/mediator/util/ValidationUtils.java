/*******************************************************************************
 * Copyright  (c) 2018, WSO2.Telco Inc. (http://www.wso2telco.com) All Rights Reserved.
 *
 *  WSO2.Telco Inc. licences this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.wso2telco.dep.mediator.util;

import com.wso2telco.dep.mediator.MSISDNConstants;
import com.wso2telco.dep.oneapivalidation.exceptions.CustomException;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

/**
 *@author WSO2telco
 * Created on 2018/08/15
 *
 */
public final class ValidationUtils {

    static Log log = LogFactory.getLog(ValidationUtils.class);

    /**
	 * This method extracts userId from payload and resource url and passed to
	 * validate whether they are same
	 */
	public static void compareMsisdn(String resourcePath, JSONObject jsonBody) {
		String urlmsisdn = null;
		try {
			urlmsisdn = URLDecoder.decode(resourcePath.substring(1,
					resourcePath.indexOf("transactions") - 1), "UTF-8");

		} catch (UnsupportedEncodingException e) {
			log.debug("Url MSISDN can not be decoded ");
		}
		String payloadMsisdn = getPayloadMsisdn(jsonBody);
		
		if(urlmsisdn != null){
        	if (urlmsisdn.startsWith(MSISDNConstants.ETEL_1)) {
        		urlmsisdn = urlmsisdn.substring(6).trim();
        	} else if ((urlmsisdn.startsWith(MSISDNConstants.TEL_1)) || urlmsisdn.startsWith(MSISDNConstants.ETEL_2)) {
                urlmsisdn = urlmsisdn.substring(5).trim();
            } else if (urlmsisdn.startsWith(MSISDNConstants.TEL_2)|| urlmsisdn.startsWith(MSISDNConstants.ETEL_3)) {
                urlmsisdn = urlmsisdn.substring(4);
            } else if (urlmsisdn.startsWith(MSISDNConstants.TEL_3)) {
                urlmsisdn = urlmsisdn.substring(3);
            } else if (urlmsisdn.startsWith(MSISDNConstants.PLUS)) {
                urlmsisdn = urlmsisdn.substring(1);
            }
        } else {
           log.debug("Not valid msisdn in resourceURL");
            throw new CustomException(MSISDNConstants.SVC0002, "", new String[] {"Not valid msisdn in URL"});
        }

        if(payloadMsisdn.equalsIgnoreCase(urlmsisdn.trim()) ){
            log.debug("msisdn in resourceURL and payload msisdn are same");
        } else {
            log.debug("msisdn in resourceURL and payload msisdn are not same");
            throw new CustomException(MSISDNConstants.SVC0002, "", new String[] { "Two different endUserId provided" });
        }

	}
	
	/**
	 * Extract MSISDN from payload
	 */
	private static String getPayloadMsisdn(JSONObject jsonBody) {
		
		// This validation assumes that userID should be with the prefix "tel:+" and back end
		// still does not support with other prefixes for this API.
		// Therefore below line should be modified in future depending on requirement
		if(!jsonBody.isNull("amountReservationTransaction")){
			return jsonBody.getJSONObject("amountReservationTransaction").getString("endUserId").substring(5);
		} else {
			return jsonBody.getJSONObject("amountTransaction").getString("endUserId").substring(5);
		}
	}
}
