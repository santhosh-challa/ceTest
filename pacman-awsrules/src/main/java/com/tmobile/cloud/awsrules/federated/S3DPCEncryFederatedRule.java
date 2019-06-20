/*******************************************************************************
 * Copyright 2019 T Mobile, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
/**
  Copyright (C) 2019 T Mobile Inc - All Rights Reserve
  Purpose:
  Author :Avinash
  Modified Date: Feb 27, 2019

 **/
package com.tmobile.cloud.awsrules.federated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tmobile.cloud.awsrules.utils.PacmanUtils;
import com.tmobile.cloud.constants.PacmanRuleConstants;
import com.tmobile.pacman.commons.PacmanSdkConstants;
import com.tmobile.pacman.commons.exception.InvalidInputException;
import com.tmobile.pacman.commons.rule.BaseRule;
import com.tmobile.pacman.commons.rule.PacmanRule;
import com.tmobile.pacman.commons.rule.RuleResult;

@PacmanRule(key = "check-for-s3-DPC-Encrypted-ACL", desc = "checks entirely for S3 Buckets With Global Write Permission", severity = PacmanSdkConstants.SEV_HIGH, category = PacmanSdkConstants.SECURITY)
public class S3DPCEncryFederatedRule extends BaseRule {

	private static final Logger logger = LoggerFactory.getLogger(S3DPCEncryFederatedRule.class);

	/**
	 * The method will get triggered from Rule Engine with following parameters
	 *
	 * @param ruleParam
	 *
	 *            ************* Following are the Rule Parameters********* <br><br>
	 *
	 *            apiKeyName : Value of API key <br><br>
	 *
	 *            apiKeyValue : Value of the API key name <br><br>
	 *
	 *            apiGWURL : API gateway URL <br><br>
	 *
	 *            ruleKey : check-for-s3-DPC-Encrypted-ACL <br><br>
	 *            
	 *            severity : Enter the value of severity <br><br>
	 *
	 *            ruleCategory : Enter the value of category <br><br>
	 *
	 *            checkId : value of check id <br><br>
	 *
	 *            esServiceURL : Enter the Es url <br><br>
	 *
	 * @param resourceAttributes this is a resource in context which needs to be scanned this is provided by execution engine
	 *
	 */
	public static final String NOT_ENCRYPTED = "notEncrypted";
    public static final String INVALID_DPC_VALUES = "Invalid_DPC_Value";
	public RuleResult execute(Map<String, String> ruleParam, Map<String, String> resourceAttributes) {
		logger.debug("========S3DPCEncryFederatedRule started=========");
		String s3BucketName = ruleParam.get(PacmanSdkConstants.RESOURCE_ID);
		String apiKeyName = ruleParam.get(PacmanRuleConstants.API_KEY_NAME);
		String apiKeyValue = ruleParam.get(PacmanRuleConstants.API_KEY_VALUE);
		String apiGWURL = ruleParam.get(PacmanRuleConstants.APIGW_URL);
		String valueOfDpc = resourceAttributes.get("dpcvalue");
		String bucketEncryption = resourceAttributes.get("bucketencryp");
		String description = "S3 bucket has DPC key";
		String severity = ruleParam.get(PacmanRuleConstants.SEVERITY);
		String category = ruleParam.get(PacmanRuleConstants.CATEGORY);
		List<String> sourcesverified = new ArrayList<>();
		sourcesverified.add("BucketPolicy");
		LinkedHashMap<String,Object>accessLevels=new LinkedHashMap<>();
		accessLevels.put("ACL", PacmanRuleConstants.PUBLIC);
		String checkEsUrl = null;
		Map<String, Boolean> s3HasOpenAccess;

		String checkId = ruleParam.get(PacmanRuleConstants.CHECK_ID);
		boolean aclFound = false;
		boolean bucketPolicyFound = false;

		String formattedUrl = PacmanUtils.formatUrl(ruleParam,PacmanRuleConstants.ES_CHECK_SERVICE_SEARCH_URL_PARAM);

        if(!StringUtils.isEmpty(formattedUrl)){
            checkEsUrl =  formattedUrl;
        }
        if (!PacmanUtils.doesAllHaveValue(apiGWURL, apiKeyValue, apiKeyName, severity, category, checkEsUrl,checkId)) {
			logger.info(PacmanRuleConstants.MISSING_CONFIGURATION);
			throw new InvalidInputException(PacmanRuleConstants.MISSING_CONFIGURATION);
		}
		if (!resourceAttributes.isEmpty()) {
			try {
				//Check for S3 bucket DPC exists or not
				if (valueOfDpc != null && (("Confidential").equalsIgnoreCase(valueOfDpc)
						|| ("Internal").equalsIgnoreCase(valueOfDpc) || ("Public").equalsIgnoreCase(valueOfDpc))) {
					//Checking Bucket is encrypted or not
					if(bucketEncryption != null) {
						//Checking S3 bucket is public
						String accountId = StringUtils.trim(resourceAttributes.get(PacmanRuleConstants.ACCOUNTID));
						s3HasOpenAccess = PacmanUtils.checkS3HasOpenAccess(checkId, accountId, checkEsUrl,
								s3BucketName);
						if (!s3HasOpenAccess.isEmpty() && s3HasOpenAccess != null) {
							aclFound = s3HasOpenAccess.get("acl_found");
							bucketPolicyFound = s3HasOpenAccess.get("bucketPolicy_found");
						}
						if(aclFound || bucketPolicyFound){
							return new RuleResult(PacmanSdkConstants.STATUS_FAILURE,
									PacmanRuleConstants.FAILURE_MESSAGE,
									PacmanUtils.createS3Annotation(ruleParam, description, severity, category,
											PacmanRuleConstants.PUBLIC_ACCESS, sourcesverified, accessLevels,
											resourceAttributes.get(PacmanRuleConstants.RESOURCE_ID)));
						}else {
							return new RuleResult(PacmanSdkConstants.STATUS_SUCCESS, PacmanRuleConstants.SUCCESS_MESSAGE);
						}

					}else {
						return new RuleResult(PacmanSdkConstants.STATUS_FAILURE,
								PacmanRuleConstants.FAILURE_MESSAGE,
								PacmanUtils.createS3Annotation(ruleParam, description, severity, category,
										NOT_ENCRYPTED, sourcesverified, accessLevels,
										resourceAttributes.get(PacmanRuleConstants.RESOURCE_ID)));
					}
				} else {
					return new RuleResult(PacmanSdkConstants.STATUS_FAILURE, PacmanRuleConstants.FAILURE_MESSAGE,
                            PacmanUtils.createS3Annotation(ruleParam, description, severity, category,
                                    INVALID_DPC_VALUES, sourcesverified, accessLevels,
                                    resourceAttributes.get(PacmanRuleConstants.RESOURCE_ID)));
				}

			} catch (Exception e1) {
				throw new InvalidInputException(e1.getMessage());
			}
		}

		logger.debug("========S3DPCEncryFederatedRule ended=========");
		return new RuleResult(PacmanSdkConstants.STATUS_SUCCESS, PacmanRuleConstants.SUCCESS_MESSAGE);

	}

	public String getHelpText() {
		return "This rule checks s3 bucket name with the global write access";
	}
}
