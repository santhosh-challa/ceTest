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
  Date: Jan 30, 2019

 **/
package com.tmobile.cloud.awsrules.federated;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tmobile.cloud.awsrules.utils.PacmanUtils;
import com.tmobile.cloud.constants.PacmanRuleConstants;
import com.tmobile.pacman.commons.PacmanSdkConstants;
import com.tmobile.pacman.commons.rule.BaseRule;
import com.tmobile.pacman.commons.rule.PacmanRule;
import com.tmobile.pacman.commons.rule.RuleResult;

@PacmanRule(key = "check-access-log-for-cloudfront", desc = "checks for access log for cloudfront and s3 bucket name ", severity = PacmanSdkConstants.SEV_HIGH, category = PacmanSdkConstants.GOVERNANCE)
public class AccessLogForCloudFront extends BaseRule {

	private static final Logger logger = LoggerFactory.getLogger(AccessLogForCloudFront.class);

	/**
	 * The method will get triggered from Rule Engine with following parameters
	 *
	 * @param ruleParam
	 *
	 ************** Following are the Rule Parameters********* <br><br>
	 *
	 *ruleKey : check-for-access-log-for-cloudfront <br><br>
	 *
	 *esAppElbWithInstanceUrl : Enter the application elb with instance api <br><br>
	 *
	 *threadsafe : if true , rule will be executed on multiple threads <br><br>
	 *
	 *severity : Enter the value of severity <br><br>
	 *
	 *ruleCategory : Enter the value of category <br><br>
	 *
	 * @param resourceAttributes this is a resource in context which needs to be scanned this is provided by execution engine
	 *
	 */

	public RuleResult execute(final Map<String, String> ruleParam,Map<String, String> resourceAttributes) {
		logger.debug("========AccessLogForCloudfront started=========");
		String accessLogBucketName = resourceAttributes.get("bucketname");
		String accessLogEnabled = resourceAttributes.get("accesslogenabled");
		String ruleParamBucketKey = ruleParam.get("accessLogBucketName");
		String severity = ruleParam.get(PacmanRuleConstants.SEVERITY);
		String category = ruleParam.get(PacmanRuleConstants.CATEGORY);
		String loggingTags = resourceAttributes.get("tags.logging");
		String description = "Access log for Cloud front";
		if (resourceAttributes != null) {
			if (loggingTags == null || loggingTags.equalsIgnoreCase("true")) {
				if (accessLogBucketName != null && accessLogBucketName.equalsIgnoreCase(ruleParamBucketKey)
						&& accessLogEnabled.equalsIgnoreCase("true")) {
					logger.info("Access log for Cloud front is available in bucket " + accessLogBucketName);
					return new RuleResult(PacmanSdkConstants.STATUS_SUCCESS, PacmanRuleConstants.SUCCESS_MESSAGE);
				} else {
					description += "is not available in S3 bucket";
					return new RuleResult(PacmanSdkConstants.STATUS_FAILURE, PacmanRuleConstants.FAILURE_MESSAGE,
							PacmanUtils.createELBAnnotation("Application", ruleParam, description, severity, category));
				}
			} else {
				return new RuleResult(PacmanSdkConstants.STATUS_SUCCESS, PacmanRuleConstants.SUCCESS_MESSAGE);
			}
		}
		return new RuleResult(PacmanSdkConstants.STATUS_SUCCESS,PacmanRuleConstants.SUCCESS_MESSAGE);
	}

	public String getHelpText() {
		return "This rule checks unused application elb which are not associated with any instance";
	}
}
