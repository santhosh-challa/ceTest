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
  Copyright (C) 2017 T Mobile Inc - All Rights Reserve
  Purpose:
  Author :Anukriti
  Date: Feb 27, 2019

 **/
package com.tmobile.cloud.awsrules.federated;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tmobile.cloud.awsrules.utils.PacmanUtils;
import com.tmobile.cloud.constants.PacmanRuleConstants;
import com.tmobile.pacman.commons.PacmanSdkConstants;
import com.tmobile.pacman.commons.exception.InvalidInputException;
import com.tmobile.pacman.commons.rule.Annotation;
import com.tmobile.pacman.commons.rule.BaseRule;
import com.tmobile.pacman.commons.rule.PacmanRule;
import com.tmobile.pacman.commons.rule.RuleResult;

@PacmanRule(key = "check-PowerUserGroup-is-mfa-enabled", desc = "This rule should look for PowerUser Group with MFA enabled", severity = PacmanSdkConstants.SEV_HIGH, category = PacmanSdkConstants.GOVERNANCE)
public class CheckPowerUserGroupIsMFAEnabled extends BaseRule{
	private static final Logger logger = LoggerFactory.getLogger(CheckPowerUserGroupIsMFAEnabled.class);

	/**
	 * The method will get triggered from Rule Engine with following parameters
	 *
	 * @param ruleParam
	 *
	 ************** Following are the Rule Parameters********* <br>
	 * 			<br>
	 *
	 *            ruleKey : check-PowerUserGroup-is-mfa-enabled <br>
	 * 			<br>
	 *
	 *            powerUserGroupName : specify the name of the group to be
	 *            checked <br>
	 * 			<br>
	 * 
	 *			 powerUserPolicyInput : specify the name of the user to be
	 *            checked <br>
	 * 			<br>
	 *
	 *            severity : Enter the value of severity <br>
	 * 			<br>
	 *
	 *            ruleCategory : Enter the value of category <br>
	 * 			<br>
	 *
	 * @param resourceAttributes
	 *            this is a resource in context which needs to be scanned this
	 *            is provided by execution engine
	 *
	 */
	public RuleResult execute(final Map<String, String> ruleParam, Map<String, String> resourceAttributes) {
		logger.debug("========CheckPowerUserGroupIsMFAEnabled started=========");
		Annotation annotation = null;
		String severity = ruleParam.get(PacmanRuleConstants.SEVERITY);
		String category = ruleParam.get(PacmanRuleConstants.CATEGORY);
		List<String> sourcesverified = new ArrayList<>();
        LinkedHashMap<String, Object> accessLevels = new LinkedHashMap<>();
        String powerUserGroupName = ruleParam.get("powerUserGroupName");
		String powerUserPolicyInput = ruleParam.get("powerUserPolicyInput");
		List<LinkedHashMap<String,Object>>issueList = new ArrayList<>();
		LinkedHashMap<String,Object>issue = new LinkedHashMap<>();
		
		if (!PacmanUtils.doesAllHaveValue(powerUserGroupName, powerUserPolicyInput,severity, category)) {
			logger.info(PacmanRuleConstants.MISSING_CONFIGURATION);
			throw new InvalidInputException(PacmanRuleConstants.MISSING_CONFIGURATION);
		}
        sourcesverified.add("HTTP Get-From Public IP");
        accessLevels.put("HTTP Get-From Public IP", PacmanRuleConstants.PUBLIC);
		if(resourceAttributes.get("groups")!= null || resourceAttributes.get("policies")!= null){

			List<String> policyNameList = Arrays.asList(resourceAttributes.get("policies").split(":;"));
			if(resourceAttributes.get("groupname").equalsIgnoreCase(powerUserGroupName) && !policyNameList.contains(powerUserPolicyInput)){
				annotation = Annotation.buildAnnotation(ruleParam,Annotation.Type.ISSUE);
				annotation.put(PacmanSdkConstants.DESCRIPTION,"Power User Group Is MFA Not Enabled!!");
				annotation.put(PacmanRuleConstants.SEVERITY, severity);
				annotation.put(PacmanRuleConstants.CATEGORY, category);
				issue.put(PacmanRuleConstants.VIOLATION_REASON,	"Power User Group Is MFA Not Enabled!!");
				issue.put(PacmanRuleConstants.SOURCE_VERIFIED, String.join(",", sourcesverified));
				issueList.add(issue);
				annotation.put("issueDetails", issueList.toString());
				return new RuleResult(PacmanSdkConstants.STATUS_FAILURE, PacmanRuleConstants.FAILURE_MESSAGE,annotation);
			}
		}

		logger.debug("========CheckPowerUserGroupIsMFAEnabled ended=========");
		return new RuleResult(PacmanSdkConstants.STATUS_SUCCESS, PacmanRuleConstants.SUCCESS_MESSAGE);

	}


	public String getHelpText() {
		return "This rule should look for IAM users of PowerUser Group with MFA enabled";
	}

}
